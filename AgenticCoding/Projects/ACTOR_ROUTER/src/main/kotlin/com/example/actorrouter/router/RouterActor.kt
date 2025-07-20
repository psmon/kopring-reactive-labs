package com.example.actorrouter.router

import com.example.actorrouter.actor.WorkerActor
import com.example.actorrouter.model.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.*
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.random.Random

class RouterActor private constructor(
    context: ActorContext<RouterCommand>,
    private val routingStrategy: RoutingStrategy,
    private val initialWorkerCount: Int
) : AbstractBehavior<RouterCommand>(context) {

    private val logger = LoggerFactory.getLogger(RouterActor::class.java)
    private val workers = mutableListOf<ActorRef<WorkerCommand>>()
    private val workerMetrics = ConcurrentHashMap<String, WorkerMetrics>()
    private val pendingTasks = ConcurrentHashMap<String, ProcessTask>()
    
    // Metrics
    private var totalTasksProcessed: Long = 0
    private var totalProcessingTime: Long = 0
    private var roundRobinIndex = 0
    
    init {
        logger.info("Router starting with strategy: $routingStrategy and $initialWorkerCount workers")
        // Create initial workers
        repeat(initialWorkerCount) {
            createWorker()
        }
    }

    override fun createReceive(): Receive<RouterCommand> {
        return newReceiveBuilder()
            .onMessage(ProcessTask::class.java, this::onProcessTask)
            .onMessage(ScaleWorkers::class.java, this::onScaleWorkers)
            .onMessage(GetRouterMetrics::class.java, this::onGetRouterMetrics)
            .onMessage(GetWorkerStatuses::class.java, this::onGetWorkerStatuses)
            .onMessage(WorkCompleted::class.java, this::onWorkCompleted)
            .onMessage(WorkerMetricsUpdate::class.java, this::onWorkerMetricsUpdate)
            .onMessage(ShutdownRouter::class.java) { onShutdown() }
            .build()
    }

    private fun onProcessTask(command: ProcessTask): Behavior<RouterCommand> {
        logger.debug("Received task ${command.taskId} with priority ${command.priority}")
        pendingTasks[command.taskId] = command
        
        val worker = selectWorker(command)
        if (worker != null) {
            val doWork = DoWork(
                taskId = command.taskId,
                payload = command.payload,
                priority = command.priority
            )
            worker.tell(doWork)
            logger.debug("Task ${command.taskId} assigned to worker")
        } else {
            logger.warn("No available workers for task ${command.taskId}")
            command.replyTo.tell(
                TaskResult(
                    taskId = command.taskId,
                    success = false,
                    error = "No available workers"
                )
            )
            pendingTasks.remove(command.taskId)
        }
        
        return this
    }

    private fun selectWorker(task: ProcessTask): ActorRef<WorkerCommand>? {
        if (workers.isEmpty()) return null
        
        return when (routingStrategy) {
            RoutingStrategy.ROUND_ROBIN -> selectRoundRobin()
            RoutingStrategy.RANDOM -> selectRandom()
            RoutingStrategy.LEAST_LOADED -> selectLeastLoaded()
            RoutingStrategy.CONSISTENT_HASH -> selectConsistentHash(task.taskId)
            RoutingStrategy.PRIORITY_BASED -> selectPriorityBased(task.priority)
            RoutingStrategy.BROADCAST -> null // Broadcast handled separately
        }
    }

    private fun selectRoundRobin(): ActorRef<WorkerCommand> {
        val worker = workers[roundRobinIndex % workers.size]
        roundRobinIndex = (roundRobinIndex + 1) % workers.size
        return worker
    }

    private fun selectRandom(): ActorRef<WorkerCommand> {
        return workers[Random.nextInt(workers.size)]
    }

    private fun selectLeastLoaded(): ActorRef<WorkerCommand> {
        // First, request metrics from all workers
        updateWorkerMetrics()
        
        // Find the least loaded worker
        var leastLoadedWorker = workers[0]
        var minLoad = Int.MAX_VALUE
        
        workers.forEachIndexed { index, worker ->
            val workerId = "worker-$index"
            val metrics = workerMetrics[workerId]
            val load = metrics?.currentLoad ?: 0
            
            if (load < minLoad) {
                minLoad = load
                leastLoadedWorker = worker
            }
        }
        
        return leastLoadedWorker
    }

    private fun selectConsistentHash(taskId: String): ActorRef<WorkerCommand> {
        val hash = taskId.hashCode()
        val index = Math.abs(hash) % workers.size
        return workers[index]
    }

    private fun selectPriorityBased(priority: TaskPriority): ActorRef<WorkerCommand> {
        // Assign high-priority tasks to the first half of workers
        // and low-priority tasks to the second half
        val index = when (priority) {
            TaskPriority.CRITICAL, TaskPriority.HIGH -> Random.nextInt(0, (workers.size + 1) / 2)
            TaskPriority.NORMAL, TaskPriority.LOW -> Random.nextInt((workers.size + 1) / 2, workers.size)
        }
        return workers[index.coerceIn(0, workers.size - 1)]
    }

    private fun updateWorkerMetrics() {
        workers.forEachIndexed { index, worker ->
            context.ask(
                WorkerMetrics::class.java,
                worker,
                java.time.Duration.ofMillis(100),
                { GetWorkerMetrics(it) },
                { metrics, _ ->
                    WorkerMetricsUpdate("worker-$index", metrics)
                }
            )
        }
    }

    private fun onScaleWorkers(command: ScaleWorkers): Behavior<RouterCommand> {
        val currentCount = workers.size
        val targetCount = (currentCount + command.delta).coerceIn(1, 100)
        val actualDelta = targetCount - currentCount
        
        if (actualDelta > 0) {
            repeat(actualDelta) {
                createWorker()
            }
            logger.info("Scaled up to $targetCount workers")
        } else if (actualDelta < 0) {
            repeat(-actualDelta) {
                if (workers.isNotEmpty()) {
                    val worker = workers.removeLast()
                    worker.tell(ShutdownWorker)
                }
            }
            logger.info("Scaled down to $targetCount workers")
        }
        
        command.replyTo.tell(
            ScaleResult(
                success = true,
                currentWorkerCount = workers.size,
                message = "Scaled from $currentCount to ${workers.size} workers"
            )
        )
        
        return this
    }

    private fun createWorker() {
        val workerId = "worker-${UUID.randomUUID()}"
        val worker = context.spawn(
            WorkerActor.create(workerId, context.self),
            workerId
        )
        workers.add(worker)
        workerMetrics[workerId] = WorkerMetrics(
            workerId = workerId,
            tasksProcessed = 0,
            currentLoad = 0,
            averageProcessingTimeMs = 0,
            isAvailable = true
        )
    }

    private fun onGetRouterMetrics(command: GetRouterMetrics): Behavior<RouterCommand> {
        val metrics = RouterMetrics(
            totalTasksProcessed = totalTasksProcessed,
            tasksInProgress = pendingTasks.size,
            averageProcessingTimeMs = if (totalTasksProcessed > 0) totalProcessingTime / totalTasksProcessed else 0,
            workerCount = workers.size,
            routingStrategy = routingStrategy.name
        )
        command.replyTo.tell(metrics)
        return this
    }

    private fun onGetWorkerStatuses(command: GetWorkerStatuses): Behavior<RouterCommand> {
        updateWorkerMetrics()
        Thread.sleep(200) // Give time for metrics to update
        
        val statuses = WorkerStatuses(
            workers = workerMetrics.values.toList()
        )
        command.replyTo.tell(statuses)
        return this
    }

    private fun onWorkCompleted(command: WorkCompleted): Behavior<RouterCommand> {
        val task = pendingTasks.remove(command.taskId)
        if (task != null) {
            task.replyTo.tell(command.result)
            totalTasksProcessed++
            totalProcessingTime += command.result.processingTimeMs
            logger.debug("Task ${command.taskId} completed by ${command.workerId}")
        }
        return this
    }

    private fun onWorkerMetricsUpdate(command: WorkerMetricsUpdate): Behavior<RouterCommand> {
        workerMetrics[command.workerId] = command.metrics
        return this
    }

    private fun onShutdown(): Behavior<RouterCommand> {
        logger.info("Router shutting down. Total tasks processed: $totalTasksProcessed")
        workers.forEach { it.tell(ShutdownWorker) }
        return Behaviors.stopped()
    }

    companion object {
        fun create(
            routingStrategy: RoutingStrategy = RoutingStrategy.ROUND_ROBIN,
            initialWorkerCount: Int = 3
        ): Behavior<RouterCommand> {
            return Behaviors.setup { context ->
                RouterActor(context, routingStrategy, initialWorkerCount)
            }
        }
    }
}