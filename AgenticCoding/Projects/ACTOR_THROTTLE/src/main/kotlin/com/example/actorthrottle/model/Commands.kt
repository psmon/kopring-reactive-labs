package com.example.actorthrottle.model

import org.apache.pekko.actor.typed.ActorRef

sealed class ThrottleCommand

data class ProcessWork(
    val mallId: String,
    val workId: String,
    val replyTo: ActorRef<WorkResult>?
) : ThrottleCommand()

data class WorkResult(
    val mallId: String,
    val workId: String,
    val timestamp: Long,
    val success: Boolean
) : ThrottleCommand()

data class GetStats(
    val replyTo: ActorRef<ThrottleStats>
) : ThrottleCommand()

data class ThrottleStats(
    val mallId: String,
    val processedCount: Long,
    val queuedCount: Int,
    val tps: Double
) : ThrottleCommand()

sealed class ThrottleManagerCommand

data class ProcessMallWork(
    val mallId: String,
    val workId: String,
    val replyTo: ActorRef<WorkResult>?
) : ThrottleManagerCommand()

data class GetMallStats(
    val mallId: String,
    val replyTo: ActorRef<ThrottleStats>
) : ThrottleManagerCommand()

data class GetAllStats(
    val replyTo: ActorRef<Map<String, ThrottleStats>>
) : ThrottleManagerCommand()

internal data class MallActorRef(
    val mallId: String,
    val actorRef: ActorRef<ThrottleCommand>
) : ThrottleManagerCommand()