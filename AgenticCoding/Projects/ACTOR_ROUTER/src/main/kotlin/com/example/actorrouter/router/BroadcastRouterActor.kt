package com.example.actorrouter.router

import com.example.actorrouter.actor.WorkerActor
import com.example.actorrouter.model.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.*
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class BroadcastRouterActor private constructor(
    context: ActorContext<RouterCommand>,
    private val initialWorkerCount: Int
) : AbstractBehavior<RouterCommand>(context) {

    private val logger = LoggerFactory.getLogger(BroadcastRouterActor::class.java)
    private val workers = mutableListOf<ActorRef<WorkerCommand>>()
    private val taskResponses = ConcurrentHashMap<String, MutableList<TaskResult>>()
    private val taskRequesters = ConcurrentHashMap<String, ProcessTask>()
    
    init {
        logger.info("Broadcast Router starting with $initialWorkerCount workers")
        repeat(initialWorkerCount) {
            createWorker()
        }
    }

    override fun createReceive(): Receive<RouterCommand> {
        return newReceiveBuilder()
            .onMessage(ProcessTask::class.java, this::onProcessTask)
            .onMessage(WorkCompleted::class.java, this::onWorkCompleted)
            .onMessage(GetRouterMetrics::class.java, this::onGetRouterMetrics)
            .onMessage(ShutdownRouter::class.java) { onShutdown() }
            .build()
    }

    private fun onProcessTask(command: ProcessTask): Behavior<RouterCommand> {
        logger.debug("Broadcasting task ${command.taskId} to all ${workers.size} workers")
        
        taskResponses[command.taskId] = mutableListOf()
        taskRequesters[command.taskId] = command
        
        // Broadcast to all workers
        workers.forEach { worker ->
            val doWork = DoWork(
                taskId = command.taskId,
                payload = command.payload,
                priority = command.priority
            )
            worker.tell(doWork)
        }
        
        return this
    }

    private fun onWorkCompleted(command: WorkCompleted): Behavior<RouterCommand> {
        val responses = taskResponses[command.taskId]
        if (responses != null) {
            responses.add(command.result)
            
            // Check if all workers have responded
            if (responses.size == workers.size) {
                val task = taskRequesters.remove(command.taskId)
                taskResponses.remove(command.taskId)
                
                if (task != null) {
                    // Aggregate results
                    val aggregatedResult = aggregateResults(command.taskId, responses)
                    task.replyTo.tell(aggregatedResult)
                    logger.debug("Task ${command.taskId} completed by all workers")
                }
            }
        }
        
        return this
    }

    private fun aggregateResults(taskId: String, results: List<TaskResult>): TaskResult {
        val successCount = results.count { it.success }
        val totalTime = results.sumOf { it.processingTimeMs }
        val averageTime = totalTime / results.size
        
        val aggregatedResult = if (successCount == results.size) {
            "All ${results.size} workers succeeded"
        } else {
            "$successCount out of ${results.size} workers succeeded"
        }
        
        return TaskResult(
            taskId = taskId,
            success = successCount > results.size / 2, // Majority success
            result = aggregatedResult,
            processingTimeMs = averageTime
        )
    }

    private fun createWorker() {
        val workerId = "broadcast-worker-${UUID.randomUUID()}"
        val worker = context.spawn(
            WorkerActor.create(workerId, context.self),
            workerId
        )
        workers.add(worker)
    }

    private fun onGetRouterMetrics(command: GetRouterMetrics): Behavior<RouterCommand> {
        val metrics = RouterMetrics(
            totalTasksProcessed = taskRequesters.size.toLong(),
            tasksInProgress = taskResponses.size,
            averageProcessingTimeMs = 0, // Would need to track this
            workerCount = workers.size,
            routingStrategy = "BROADCAST"
        )
        command.replyTo.tell(metrics)
        return this
    }

    private fun onShutdown(): Behavior<RouterCommand> {
        logger.info("Broadcast Router shutting down")
        workers.forEach { it.tell(ShutdownWorker) }
        return Behaviors.stopped()
    }

    companion object {
        fun create(initialWorkerCount: Int = 3): Behavior<RouterCommand> {
            return Behaviors.setup { context ->
                BroadcastRouterActor(context, initialWorkerCount)
            }
        }
    }
}