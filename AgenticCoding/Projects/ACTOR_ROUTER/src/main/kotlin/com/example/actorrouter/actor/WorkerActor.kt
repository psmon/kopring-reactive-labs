package com.example.actorrouter.actor

import com.example.actorrouter.model.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class WorkerActor private constructor(
    context: ActorContext<WorkerCommand>,
    private val workerId: String,
    private val routerRef: ActorRef<RouterCommand>
) : AbstractBehavior<WorkerCommand>(context) {

    private val logger = LoggerFactory.getLogger(WorkerActor::class.java)
    private var tasksProcessed: Long = 0
    private var currentTask: DoWork? = null
    private var totalProcessingTime: Long = 0

    init {
        logger.info("Worker $workerId started")
    }


    private fun onDoWork(command: DoWork): Behavior<WorkerCommand> {
        if (currentTask != null) {
            logger.warn("Worker $workerId is busy, rejecting task ${command.taskId}")
            val result = TaskResult(
                taskId = command.taskId,
                success = false,
                error = "Worker is busy"
            )
            routerRef.tell(WorkCompleted(workerId, command.taskId, result))
            return this
        }

        currentTask = command
        logger.debug("Worker $workerId processing task ${command.taskId} with priority ${command.priority}")

        // Process the task directly in a separate thread
        Thread {
            processTask(command)
        }.start()
        
        return this
    }

    private fun onGetMetrics(command: GetWorkerMetrics): Behavior<WorkerCommand> {
        val metrics = WorkerMetrics(
            workerId = workerId,
            tasksProcessed = tasksProcessed,
            currentLoad = if (currentTask != null) 1 else 0,
            averageProcessingTimeMs = if (tasksProcessed > 0) totalProcessingTime / tasksProcessed else 0,
            isAvailable = currentTask == null
        )
        command.replyTo.tell(metrics)
        return this
    }

    private fun onShutdown(): Behavior<WorkerCommand> {
        logger.info("Worker $workerId shutting down. Tasks processed: $tasksProcessed")
        return Behaviors.stopped()
    }

    private fun processTask(task: DoWork) {
        // Simulate different processing times based on task characteristics
        val baseProcessingTime = when (task.priority) {
            TaskPriority.CRITICAL -> 100L
            TaskPriority.HIGH -> 200L
            TaskPriority.NORMAL -> 500L
            TaskPriority.LOW -> 1000L
        }

        // Add some randomness
        val processingTime = baseProcessingTime + Random.nextLong(0, baseProcessingTime / 2)
        
        // Simulate work
        Thread.sleep(processingTime)

        val endTime = Instant.now()
        val duration = Duration.between(task.startTime, endTime).toMillis()
        
        // Simulate success/failure (95% success rate)
        val success = Random.nextDouble() < 0.95
        
        val result = if (success) {
            TaskResult(
                taskId = task.taskId,
                success = true,
                result = "Processed: ${task.payload} by worker $workerId",
                processingTimeMs = duration
            )
        } else {
            TaskResult(
                taskId = task.taskId,
                success = false,
                error = "Random processing failure",
                processingTimeMs = duration
            )
        }

        // Update metrics
        tasksProcessed++
        totalProcessingTime += duration
        currentTask = null

        // Send completion to router
        routerRef.tell(WorkCompleted(workerId, task.taskId, result))
        
        logger.debug("Worker $workerId completed task ${task.taskId} in ${duration}ms")
    }

    override fun createReceive(): Receive<WorkerCommand> {
        return newReceiveBuilder()
            .onMessage(DoWork::class.java, this::onDoWork)
            .onMessage(GetWorkerMetrics::class.java, this::onGetMetrics)
            .onMessage(ShutdownWorker::class.java) { onShutdown() }
            .build()
    }

    companion object {
        fun create(workerId: String, routerRef: ActorRef<RouterCommand>): Behavior<WorkerCommand> {
            return Behaviors.setup { context ->
                WorkerActor(context, workerId, routerRef)
            }
        }
    }
}