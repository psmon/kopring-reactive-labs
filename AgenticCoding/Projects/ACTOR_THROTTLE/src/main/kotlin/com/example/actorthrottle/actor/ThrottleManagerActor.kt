package com.example.actorthrottle.actor

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import com.example.actorthrottle.model.*
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CompletableFuture

class ThrottleManagerActor private constructor(
    context: ActorContext<ThrottleManagerCommand>
) : AbstractBehavior<ThrottleManagerCommand>(context) {

    companion object {
        private val logger = LoggerFactory.getLogger(ThrottleManagerActor::class.java)
        
        fun create(): Behavior<ThrottleManagerCommand> {
            return Behaviors.setup { context ->
                ThrottleManagerActor(context)
            }
        }
    }

    private val mallActors = mutableMapOf<String, ActorRef<ThrottleCommand>>()

    override fun createReceive(): Receive<ThrottleManagerCommand> {
        return newReceiveBuilder()
            .onMessage(ProcessMallWork::class.java, this::onProcessMallWork)
            .onMessage(GetMallStats::class.java, this::onGetMallStats)
            .onMessage(GetAllStats::class.java, this::onGetAllStats)
            .build()
    }

    private fun onProcessMallWork(command: ProcessMallWork): Behavior<ThrottleManagerCommand> {
        logger.debug("Processing work for mall: {}", command.mallId)
        
        val mallActor = getOrCreateMallActor(command.mallId)
        
        val processWork = ProcessWork(
            mallId = command.mallId,
            workId = command.workId,
            replyTo = command.replyTo
        )
        
        mallActor.tell(processWork)
        
        return this
    }

    private fun onGetMallStats(command: GetMallStats): Behavior<ThrottleManagerCommand> {
        val mallActor = mallActors[command.mallId]
        
        if (mallActor != null) {
            mallActor.tell(GetStats(command.replyTo))
        } else {
            command.replyTo.tell(
                ThrottleStats(
                    mallId = command.mallId,
                    processedCount = 0,
                    queuedCount = 0,
                    tps = 0.0
                )
            )
        }
        
        return this
    }

    private fun onGetAllStats(command: GetAllStats): Behavior<ThrottleManagerCommand> {
        if (mallActors.isEmpty()) {
            command.replyTo.tell(emptyMap())
            return this
        }

        // Create a temporary actor to collect all stats
        val collector = context.spawn(
            StatsCollectorActor.create(command.replyTo, mallActors.size),
            "stats-collector-${System.currentTimeMillis()}"
        )

        // Request stats from each mall actor
        mallActors.forEach { (mallId, actorRef) ->
            actorRef.tell(GetStats(collector))
        }

        return this
    }


    private fun getOrCreateMallActor(mallId: String): ActorRef<ThrottleCommand> {
        return mallActors.getOrPut(mallId) {
            logger.info("Creating new ThrottleActor for mall: {}", mallId)
            val actorName = "throttle-actor-$mallId"
            context.spawn(ThrottleActor.create(mallId, tpsLimit = 1), actorName)
        }
    }
}

// Helper actor to collect stats from multiple mall actors
private class StatsCollectorActor(
    context: ActorContext<ThrottleStats>,
    private val replyTo: ActorRef<Map<String, ThrottleStats>>,
    private val expectedStats: Int
) : AbstractBehavior<ThrottleStats>(context) {
    
    companion object {
        fun create(
            replyTo: ActorRef<Map<String, ThrottleStats>>,
            expectedStats: Int
        ): Behavior<ThrottleStats> {
            return Behaviors.setup { context ->
                StatsCollectorActor(context, replyTo, expectedStats)
            }
        }
    }
    
    private val collectedStats = mutableMapOf<String, ThrottleStats>()
    
    override fun createReceive(): Receive<ThrottleStats> {
        return newReceiveBuilder()
            .onMessage(ThrottleStats::class.java, this::onStats)
            .build()
    }
    
    private fun onStats(stats: ThrottleStats): Behavior<ThrottleStats> {
        collectedStats[stats.mallId] = stats
        
        if (collectedStats.size >= expectedStats) {
            replyTo.tell(collectedStats.toMap())
            return Behaviors.stopped()
        }
        
        return this
    }
}