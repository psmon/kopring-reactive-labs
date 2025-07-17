package com.example.ssepushsystem.model

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
interface CborSerializable

data class TopicEvent(
    val id: String,
    val topic: String,
    val data: String,
    val timestamp: Instant = Instant.now()
) : CborSerializable

data class TopicSubscription(
    val userId: String,
    val topics: Set<String>
) : CborSerializable

data class EventHistory(
    val topic: String,
    val events: List<TopicEvent>
) : CborSerializable