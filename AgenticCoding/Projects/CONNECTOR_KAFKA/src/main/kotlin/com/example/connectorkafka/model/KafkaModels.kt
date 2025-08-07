package com.example.connectorkafka.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.Serializable

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class KafkaEvent(
    @JsonProperty("eventType")
    val eventType: String,
    
    @JsonProperty("eventId")
    val eventId: String,
    
    @JsonProperty("eventString")
    val eventString: String,
    
    @JsonProperty("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

sealed interface EventCommand : Serializable

data class ProcessEvent(val event: KafkaEvent) : EventCommand
data class GetLastEvent(val replyTo: org.apache.pekko.actor.typed.ActorRef<EventResponse>) : EventCommand
data class GetEventCount(val replyTo: org.apache.pekko.actor.typed.ActorRef<EventResponse>) : EventCommand
object ClearEvents : EventCommand

sealed interface EventResponse : Serializable
data class LastEventResponse(val event: KafkaEvent?) : EventResponse
data class EventCountResponse(val count: Int) : EventResponse

data class EventConsumerState(
    val lastEvent: KafkaEvent? = null,
    val eventCount: Int = 0
) : Serializable