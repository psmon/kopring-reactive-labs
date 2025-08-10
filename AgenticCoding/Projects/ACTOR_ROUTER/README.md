# Actor Router Tutorial

The Actor Router project is a tutorial that implements various work distribution strategies using Apache Pekko (formerly Akka). This project operates on a local actor system and is designed with a structure that can be expanded to remote/cluster without changing service logic.

## Table of Contents

1. [Project Overview](#project-overview)
2. [Core Components](#core-components)
3. [Routing Strategies](#routing-strategies)
4. [Usage](#usage)
5. [Scalability](#scalability)
6. [Monitoring and Metrics](#monitoring-and-metrics)
7. [Testing](#testing)
8. [Transition from Local to Cluster](#transition-from-local-to-cluster)

## Project Overview

This project demonstrates various patterns for distributing work among multiple workers using the Actor model. The main objectives are:

- Implementation of various routing strategies
- Dynamic scaling support
- Work monitoring and metrics collection
- Fault handling and resilience
- Easy transition from local to distributed systems

## Core Components

### 1. WorkerActor

The actor that actually performs the work.

```kotlin
class WorkerActor(
    private val workerId: String,
    private val routerRef: ActorRef<RouterCommand>
) : AbstractBehavior<WorkerCommand>(context) {
    
    // Work processing
    private fun onDoWork(command: DoWork): Behavior<WorkerCommand> {
        // Simulate task execution
        processTask(command)
        // Report completion
        routerRef.tell(WorkCompleted(workerId, command.taskId, result))
        return this
    }
}
```

Key features:
- Processes only one task at a time
- Differentiated processing time by priority
- Reports to router after task completion
- Metrics tracking (processing time, task count, etc.)

### 2. RouterActor

The management actor that distributes work to workers.

```kotlin
class RouterActor(
    private val routingStrategy: RoutingStrategy,
    private val initialWorkerCount: Int
) : AbstractBehavior<RouterCommand>(context) {
    
    // Work distribution
    private fun onProcessTask(command: ProcessTask): Behavior<RouterCommand> {
        val worker = selectWorker(command)
        worker.tell(DoWork(...))
        return this
    }
}
```

Key functions:
- Support for various routing strategies
- Dynamic worker scaling
- Task tracking and metrics collection

### 3. Command and Message Models

```kotlin
// Router commands
sealed interface RouterCommand
data class ProcessTask(val taskId: String, val payload: String, val priority: TaskPriority)
data class ScaleWorkers(val delta: Int)
data class GetRouterMetrics(val replyTo: ActorRef<RouterMetrics>)

// Worker commands
sealed interface WorkerCommand
data class DoWork(val taskId: String, val payload: String, val priority: TaskPriority)
data class GetWorkerMetrics(val replyTo: ActorRef<WorkerMetrics>)
```

## Routing Strategies

### 1. Round Robin (Sequential Distribution)

```kotlin
private fun selectRoundRobin(): ActorRef<WorkerCommand> {
    val worker = workers[roundRobinIndex % workers.size]
    roundRobinIndex = (roundRobinIndex + 1) % workers.size
    return worker
}
```

- Distributes tasks sequentially to each worker
- The fairest distribution method
- Does not consider current worker state

### 2. Random (Random Distribution)

```kotlin
private fun selectRandom(): ActorRef<WorkerCommand> {
    return workers[Random.nextInt(workers.size)]
}
```

- Randomly selects workers
- Simple and fast selection
- Statistically even distribution

### 3. Least Loaded (Minimum Load)

```kotlin
private fun selectLeastLoaded(): ActorRef<WorkerCommand> {
    updateWorkerMetrics()
    return workers.minByOrNull { getWorkerLoad(it) }!!
}
```

- Selects the worker with the least current load
- Dynamic load balancing
- Overhead exists due to metrics queries

### 4. Consistent Hash

```kotlin
private fun selectConsistentHash(taskId: String): ActorRef<WorkerCommand> {
    val hash = taskId.hashCode()
    val index = Math.abs(hash) % workers.size
    return workers[index]
}
```

- Same ID always routes to the same worker
- Cache-friendly
- Useful for stateful tasks

### 5. Priority Based

```kotlin
private fun selectPriorityBased(priority: TaskPriority): ActorRef<WorkerCommand> {
    return when (priority) {
        TaskPriority.CRITICAL, TaskPriority.HIGH -> 
            workers[Random.nextInt(0, workers.size / 2)]
        TaskPriority.NORMAL, TaskPriority.LOW -> 
            workers[Random.nextInt(workers.size / 2, workers.size)]
    }
}
```

- Separates worker groups by priority
- Ensures fast processing of important tasks

### 6. Broadcast

```kotlin
// Implemented in BroadcastRouterActor
workers.forEach { worker ->
    worker.tell(DoWork(...))
}
```

- Sends same task to all workers
- Aggregates results before responding
- Improves reliability through redundant processing

## Usage

### Router Creation and Task Submission

```kotlin
// Create router
val system = ActorSystem.create(Behaviors.empty<Void>(), "RouterSystem")
val router = system.systemActorOf(
    RouterActor.create(RoutingStrategy.ROUND_ROBIN, 3),
    "router"
)

// Submit task
val resultProbe = TestProbe<TaskResult>()
router.tell(ProcessTask(
    taskId = "task-1",
    payload = "Process this data",
    priority = TaskPriority.HIGH,
    replyTo = resultProbe.ref
))

// Receive result
val result = resultProbe.receiveMessage()
```

### Dynamic Scaling

```kotlin
// Add workers
router.tell(ScaleWorkers(delta = 2, replyTo = scaleProbe.ref))

// Remove workers
router.tell(ScaleWorkers(delta = -1, replyTo = scaleProbe.ref))
```

### Metrics Queries

```kotlin
// Router metrics
router.tell(GetRouterMetrics(metricsProbe.ref))
val metrics = metricsProbe.receiveMessage()
println("Total tasks processed: ${metrics.totalTasksProcessed}")
println("Average processing time: ${metrics.averageProcessingTimeMs}ms")

// Worker status
router.tell(GetWorkerStatuses(statusProbe.ref))
val statuses = statusProbe.receiveMessage()
statuses.workers.forEach { worker ->
    println("Worker ${worker.workerId}: processed ${worker.tasksProcessed} tasks")
}
```

## Scalability

### Scale In/Out

The system can dynamically adjust the number of workers during runtime:

```kotlin
// Add workers when load increases
if (metrics.averageProcessingTimeMs > threshold) {
    router.tell(ScaleWorkers(delta = 2, replyTo = probe.ref))
}

// Remove workers when load decreases
if (metrics.averageProcessingTimeMs < lowerThreshold) {
    router.tell(ScaleWorkers(delta = -1, replyTo = probe.ref))
}
```

## Monitoring and Metrics

The system provides the following metrics:

1. **Router Metrics**
   - Total tasks processed
   - Tasks in progress
   - Average processing time
   - Current number of workers

2. **Worker Metrics**
   - Tasks processed
   - Current load status
   - Average processing time
   - Availability status

## Testing

The project includes comprehensive unit tests:

```bash
# Run all tests
./gradlew test

# Run specific tests
./gradlew test --tests RouterActorTest
```

Key test scenarios:
- Verification of each routing strategy behavior
- Scaling functionality tests
- Fault handling tests
- Performance and load tests

## Transition from Local to Cluster

The current implementation operates on a local actor system but can be expanded to a cluster without changing service logic.

### 1. Add Cluster Dependencies

```kotlin
dependencies {
    implementation("org.apache.pekko:pekko-cluster-typed_2.13:1.1.2")
    implementation("org.apache.pekko:pekko-cluster-sharding-typed_2.13:1.1.2")
}
```

### 2. Cluster Configuration

```hocon
# application.conf
pekko {
  actor {
    provider = "cluster"
  }
  
  remote.artery {
    canonical {
      hostname = "127.0.0.1"
      port = 2551
    }
  }
  
  cluster {
    seed-nodes = ["pekko://RouterSystem@127.0.0.1:2551"]
    roles = ["router", "worker"]
  }
}
```

### 3. Cluster Router Creation

```kotlin
// Change local router to cluster router
val router = ClusterRouterPool(
    local = RoundRobinPool(5),
    settings = ClusterRouterPoolSettings(
        totalInstances = 100,
        maxInstancesPerNode = 5,
        allowLocalRoutees = true,
        useRoles = Set("worker")
    )
).props(WorkerActor.props())
```

### 4. Remote Deployment

```hocon
# Deploy workers to remote nodes
pekko.actor.deployment {
  /router/worker {
    remote = "pekko://RouterSystem@worker-node:2552"
  }
}
```

### Key Changes

1. **Serialization**: Messages must be serializable as they are transmitted over the network
2. **Location Transparency**: ActorRef works identically without local/remote distinction
3. **Fault Tolerance**: Added handling for network partitions and node failures
4. **Discovery**: Automatic cluster node discovery mechanism

These changes do not affect business logic and are primarily made at the configuration and infrastructure level.

## Performance Considerations

1. **Routing Strategy Selection**
   - Round Robin: Even load, fast selection
   - Least Loaded: Optimal load balancing, metrics overhead
   - Consistent Hash: Cache efficiency, redistribution during scaling

2. **Worker Count Determination**
   - Consider CPU core count
   - Task I/O vs CPU intensity
   - Memory usage

3. **Metrics Collection Frequency**
   - Too frequent: Increased overhead
   - Too infrequent: Inaccurate routing

## Conclusion

This project demonstrates various patterns of work distribution using the Actor model. It provides a flexible structure that can start locally and expand to distributed systems as needed. Each routing strategy is optimized for specific use cases and can be selected according to actual requirements.