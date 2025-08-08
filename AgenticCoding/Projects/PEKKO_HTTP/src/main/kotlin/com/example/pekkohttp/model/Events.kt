package com.example.pekkohttp.model

import java.time.Instant
import java.io.Serializable

data class UserEvent(
    val userId: String,
    val eventType: String,
    val action: String,
    val metadata: Map<String, Any> = emptyMap(),
    val timestamp: Instant = Instant.now()
) : Serializable

data class EventStats(
    val totalEvents: Long,
    val eventsPerType: Map<String, Long>,
    val eventsPerUser: Map<String, Long>,
    val lastEventTime: Instant?
) : Serializable

data class StreamedEvent(
    val sequenceNumber: Long,
    val event: UserEvent,
    val processedAt: Instant = Instant.now()
) : Serializable