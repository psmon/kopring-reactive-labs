package com.example.actorrouter.model

import org.apache.pekko.actor.typed.ActorRef
import java.time.Instant

// Router Commands
sealed interface RouterCommand

data class ProcessTask(
    val taskId: String,
    val payload: String,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val replyTo: ActorRef<TaskResult>
) : RouterCommand

data class ScaleWorkers(
    val delta: Int,
    val replyTo: ActorRef<ScaleResult>
) : RouterCommand

data class GetRouterMetrics(
    val replyTo: ActorRef<RouterMetrics>
) : RouterCommand

data class GetWorkerStatuses(
    val replyTo: ActorRef<WorkerStatuses>
) : RouterCommand

object ShutdownRouter : RouterCommand

// Worker Commands
sealed interface WorkerCommand

data class DoWork(
    val taskId: String,
    val payload: String,
    val priority: TaskPriority,
    val startTime: Instant = Instant.now()
) : WorkerCommand

data class GetWorkerMetrics(
    val replyTo: ActorRef<WorkerMetrics>
) : WorkerCommand

object ShutdownWorker : WorkerCommand

// Internal Router Commands for worker responses
data class WorkCompleted(
    val workerId: String,
    val taskId: String,
    val result: TaskResult
) : RouterCommand

data class WorkerMetricsUpdate(
    val workerId: String,
    val metrics: WorkerMetrics
) : RouterCommand

// Response Types
data class TaskResult(
    val taskId: String,
    val success: Boolean,
    val result: String? = null,
    val error: String? = null,
    val processingTimeMs: Long = 0
)

data class ScaleResult(
    val success: Boolean,
    val currentWorkerCount: Int,
    val message: String
)

data class RouterMetrics(
    val totalTasksProcessed: Long,
    val tasksInProgress: Int,
    val averageProcessingTimeMs: Long,
    val workerCount: Int,
    val routingStrategy: String
)

data class WorkerMetrics(
    val workerId: String,
    val tasksProcessed: Long,
    val currentLoad: Int,
    val averageProcessingTimeMs: Long,
    val isAvailable: Boolean
)

data class WorkerStatuses(
    val workers: List<WorkerMetrics>
)

// Task Priority
enum class TaskPriority {
    LOW, NORMAL, HIGH, CRITICAL
}

// Routing Strategies
enum class RoutingStrategy {
    ROUND_ROBIN,
    RANDOM,
    LEAST_LOADED,
    CONSISTENT_HASH,
    BROADCAST,
    PRIORITY_BASED
}