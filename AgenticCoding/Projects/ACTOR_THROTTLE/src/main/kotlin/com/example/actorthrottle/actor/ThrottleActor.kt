package com.example.actorthrottle.actor

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.javadsl.Source
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.javadsl.SourceQueueWithComplete
import com.example.actorthrottle.model.*
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentLinkedQueue

class ThrottleActor private constructor(
    context: ActorContext<ThrottleCommand>,
    private val mallId: String,
    private val tpsLimit: Int = 1
) : AbstractBehavior<ThrottleCommand>(context) {

    companion object {
        private val logger = LoggerFactory.getLogger(ThrottleActor::class.java)
        
        fun create(mallId: String, tpsLimit: Int = 1): Behavior<ThrottleCommand> {
            return Behaviors.setup { context ->
                ThrottleActor(context, mallId, tpsLimit)
            }
        }
    }

    private val materializer = Materializer.createMaterializer(context.system)
    private val processedCount = AtomicLong(0)
    private val startTime = System.currentTimeMillis()
    private val pendingQueue = ConcurrentLinkedQueue<ProcessWork>()
    
    private data class ThrottledWork(
        val work: ProcessWork
    )
    
    private val throttleSource: SourceQueueWithComplete<ThrottledWork> = Source
        .queue<ThrottledWork>(100, OverflowStrategy.backpressure())
        .throttle(tpsLimit, Duration.ofSeconds(1))
        .to(Sink.foreach { throttledWork ->
            processWork(throttledWork.work)
        })
        .run(materializer)

    override fun createReceive(): Receive<ThrottleCommand> {
        return newReceiveBuilder()
            .onMessage(ProcessWork::class.java, this::onProcessWork)
            .onMessage(GetStats::class.java, this::onGetStats)
            .build()
    }

    private fun onProcessWork(command: ProcessWork): Behavior<ThrottleCommand> {
        logger.debug("Mall {} received work request: {}", mallId, command.workId)
        
        pendingQueue.offer(command)
        throttleSource.offer(ThrottledWork(command))
        
        return this
    }

    private fun processWork(work: ProcessWork) {
        pendingQueue.remove(work)
        val timestamp = System.currentTimeMillis()
        processedCount.incrementAndGet()
        
        logger.info("Mall {} processing work: {} at {}", mallId, work.workId, timestamp)
        
        val result = WorkResult(
            mallId = work.mallId,
            workId = work.workId,
            timestamp = timestamp,
            success = true
        )
        
        work.replyTo?.tell(result)
    }

    private fun onGetStats(command: GetStats): Behavior<ThrottleCommand> {
        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
        val actualTps = if (elapsedSeconds > 0) processedCount.get() / elapsedSeconds else 0.0
        
        val stats = ThrottleStats(
            mallId = mallId,
            processedCount = processedCount.get(),
            queuedCount = pendingQueue.size,
            tps = actualTps
        )
        
        command.replyTo.tell(stats)
        return this
    }
}