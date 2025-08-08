package com.example.pekkohttp.actor

import com.example.pekkohttp.model.*
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.stream.*
import org.apache.pekko.stream.javadsl.*
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

class EventStreamActor private constructor(
    context: ActorContext<EventCommand>
) : AbstractBehavior<EventCommand>(context) {

    companion object {
        fun create(): Behavior<EventCommand> {
            return Behaviors.setup { context ->
                EventStreamActor(context)
            }
        }
    }

    private val materializer = Materializer.createMaterializer(context.system)
    private val sequenceCounter = AtomicLong(0)
    private val eventStats = mutableMapOf<String, MutableMap<String, Long>>()
    private var lastEventTime: Instant? = null
    private var totalEvents = 0L

    override fun createReceive(): Receive<EventCommand> {
        return newReceiveBuilder()
            .onMessage(ProcessEvent::class.java, this::onProcessEvent)
            .onMessage(GetEventStats::class.java, this::onGetEventStats)
            .onMessage(StreamComplete::class.java, this::onStreamComplete)
            .build()
    }

    private fun onProcessEvent(command: ProcessEvent): Behavior<EventCommand> {
        val event = command.event
        context.log.info("Processing event: userId=${event.userId}, type=${event.eventType}, action=${event.action}")
        
        // Update statistics immediately when event is received
        updateStatistics(event)
        
        val graph = RunnableGraph.fromGraph(
            GraphDSL.create { builder ->
                // Source - single event
                val source = builder.add(Source.single(event))
                
                // Flow 1: Event enrichment
                val enrichFlow = builder.add(Flow.of(UserEvent::class.java)
                    .map { evt ->
                        StreamedEvent(
                            sequenceNumber = sequenceCounter.incrementAndGet(),
                            event = evt,
                            processedAt = Instant.now()
                        )
                    })
                
                // Flow 2: Throttling to simulate processing time (reduced for testing)
                val throttleFlow = builder.add(Flow.of(StreamedEvent::class.java)
                    .throttle(1000, Duration.ofMillis(10), 100, ThrottleMode.shaping()))
                
                // Flow 3: Logging
                val loggingFlow = builder.add(Flow.of(StreamedEvent::class.java)
                    .map { streamedEvent ->
                        context.log.info("Stream processed event #${streamedEvent.sequenceNumber}: " +
                            "userId=${streamedEvent.event.userId}, " +
                            "type=${streamedEvent.event.eventType}, " +
                            "action=${streamedEvent.event.action}, " +
                            "processedAt=${streamedEvent.processedAt}")
                        streamedEvent
                    })
                
                // Buffer for backpressure handling
                val bufferFlow = builder.add(Flow.of(StreamedEvent::class.java)
                    .buffer(1000, OverflowStrategy.backpressure()))
                
                // Broadcast for parallel processing
                val broadcast = builder.add(Broadcast.create<StreamedEvent>(2))
                
                // Branch 1: Fast logging
                val fastLogSink = builder.add(Sink.foreach<StreamedEvent> { evt ->
                    context.log.debug("Fast log: Event #${evt.sequenceNumber} processed")
                })
                
                // Branch 2: Persistence simulation (faster for testing)
                val persistSink = builder.add(
                    Sink.foreach<StreamedEvent> { evt ->
                        context.log.info("Persisted event #${evt.sequenceNumber} to storage")
                        context.self.tell(StreamComplete(1))
                    }
                )
                
                // Connect the graph
                builder.from(source.out())
                    .via(enrichFlow)
                    .via(throttleFlow)
                    .via(loggingFlow)
                    .via(bufferFlow)
                    .viaFanOut(broadcast)
                    
                builder.from(broadcast.out(0)).to(fastLogSink)
                builder.from(broadcast.out(1)).to(persistSink)
                
                ClosedShape.getInstance()
            }
        )
        
        graph.run(materializer)
        return this
    }
    
    private fun updateStatistics(event: UserEvent) {
        totalEvents++
        lastEventTime = event.timestamp
        
        // Update events per type
        val typeStats = eventStats.getOrPut("type") { mutableMapOf() }
        typeStats[event.eventType] = typeStats.getOrDefault(event.eventType, 0L) + 1
        
        // Update events per user
        val userStats = eventStats.getOrPut("user") { mutableMapOf() }
        userStats[event.userId] = userStats.getOrDefault(event.userId, 0L) + 1
    }

    private fun onGetEventStats(command: GetEventStats): Behavior<EventCommand> {
        val stats = EventStats(
            totalEvents = totalEvents,
            eventsPerType = eventStats["type"]?.toMap() ?: emptyMap(),
            eventsPerUser = eventStats["user"]?.toMap() ?: emptyMap(),
            lastEventTime = lastEventTime
        )
        command.replyTo.tell(stats)
        context.log.info("Sent event statistics: totalEvents=$totalEvents")
        return this
    }

    private fun onStreamComplete(command: StreamComplete): Behavior<EventCommand> {
        context.log.info("Stream processing completed for ${command.processed} events")
        return this
    }
}