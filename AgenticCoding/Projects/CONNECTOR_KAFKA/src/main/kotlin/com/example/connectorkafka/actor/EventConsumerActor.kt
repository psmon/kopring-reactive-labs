package com.example.connectorkafka.actor

import com.example.connectorkafka.model.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.slf4j.LoggerFactory

class EventConsumerActor private constructor(
    context: ActorContext<EventCommand>,
    private var state: EventConsumerState
) : AbstractBehavior<EventCommand>(context) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(EventConsumerActor::class.java)
        
        fun create(): Behavior<EventCommand> {
            return Behaviors.setup { context ->
                EventConsumerActor(context, EventConsumerState())
            }
        }
    }
    
    override fun createReceive(): Receive<EventCommand> {
        return newReceiveBuilder()
            .onMessage(ProcessEvent::class.java, this::onProcessEvent)
            .onMessage(GetLastEvent::class.java, this::onGetLastEvent)
            .onMessage(GetEventCount::class.java, this::onGetEventCount)
            .onMessage(ClearEvents::class.java, this::onClearEvents)
            .build()
    }
    
    private fun onProcessEvent(command: ProcessEvent): Behavior<EventCommand> {
        val event = command.event
        state = state.copy(
            lastEvent = event,
            eventCount = state.eventCount + 1
        )
        
        logger.info("Processed event: ${event.eventId} - Type: ${event.eventType}, String: ${event.eventString}")
        logger.debug("Current state - Count: ${state.eventCount}, Last event: ${state.lastEvent?.eventId}")
        
        return this
    }
    
    private fun onGetLastEvent(command: GetLastEvent): Behavior<EventCommand> {
        command.replyTo.tell(LastEventResponse(state.lastEvent))
        logger.debug("Returning last event: ${state.lastEvent?.eventId}")
        return this
    }
    
    private fun onGetEventCount(command: GetEventCount): Behavior<EventCommand> {
        command.replyTo.tell(EventCountResponse(state.eventCount))
        logger.debug("Returning event count: ${state.eventCount}")
        return this
    }
    
    private fun onClearEvents(command: ClearEvents): Behavior<EventCommand> {
        logger.info("Clearing event state. Previous count: ${state.eventCount}")
        state = EventConsumerState()
        return this
    }
}