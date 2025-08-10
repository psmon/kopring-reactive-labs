# ACTOR_THROTTLE - Actor Model Based TPS Throttling Tutorial

## Overview

The ACTOR_THROTTLE project is a tutorial that implements TPS (Transactions Per Second) throttling using the Apache Pekko (Akka fork) actor model. This project manages work on a per-`mallID` basis and applies independent TPS limits for each mall.

## Core Concepts

### 1. Throttling Mechanism

The core of this project is using Pekko Streams' `throttle` operator to control TPS without thread blocking:

```kotlin
Source.queue<ThrottledWork>(100, OverflowStrategy.backpressure())
    .throttle(tpsLimit, Duration.ofSeconds(1))
    .to(Sink.foreach { throttledWork ->
        processWork(throttledWork.work)
    })
    .run(materializer)
```

Advantages of this approach:
- **Non-blocking**: Asynchronous processing without Thread.sleep()
- **Backpressure**: Automatic processing rate control when queue is full
- **Precise TPS Control**: Accurately adheres to the configured TPS

### 2. Actor Structure

#### ThrottleActor
- Actor that processes work for individual malls
- Applies independent TPS limits per mall
- Manages work queue and processing statistics

#### ThrottleManagerActor
- Manages multiple mall ThrottleActors
- Creates actors per mall and handles routing
- Collects integrated statistics

#### StatsCollectorActor
- Asynchronously collects statistics from multiple malls
- Created temporarily and terminates after completing work

## Key Features

### 1. Independent TPS Control per Mall

Each mall has its own independent ThrottleActor and doesn't affect others:

```kotlin
// mall1 is limited to TPS 1
// mall2 is also independently limited to TPS 1
managerActor.tell(ProcessMallWork("mall1", "work1", replyTo))
managerActor.tell(ProcessMallWork("mall2", "work2", replyTo))
```

### 2. Real-time Statistics Query

Statistics can be queried in real-time even during work processing:

```kotlin
// Statistics for specific mall
managerActor.tell(GetMallStats("mall1", statsProbe.ref))

// Statistics for all malls
managerActor.tell(GetAllStats(allStatsProbe.ref))
```

### 3. FIFO Work Processing

Work is processed in the order requested (FIFO) and executed sequentially according to TPS limits.

## Usage Examples

### Basic Usage

```kotlin
// Create ThrottleManagerActor
val managerActor = actorSystem.spawn(ThrottleManagerActor.create(), "throttle-manager")

// Request work
val resultProbe = testKit.createTestProbe<WorkResult>()
managerActor.tell(ProcessMallWork("mall1", "work-123", resultProbe.ref))

// Receive result
val result = resultProbe.receiveMessage()
println("Work ${result.workId} processed at ${result.timestamp}")
```

### Statistics Query

```kotlin
// Query statistics for specific mall
val statsProbe = testKit.createTestProbe<ThrottleStats>()
managerActor.tell(GetMallStats("mall1", statsProbe.ref))
val stats = statsProbe.receiveMessage()

println("Mall: ${stats.mallId}")
println("Processed: ${stats.processedCount}")
println("Queued: ${stats.queuedCount}")
println("TPS: ${stats.tps}")
```

## Running Tests

```bash
./gradlew test
```

## Key Test Cases

### 1. TPS Limit Verification
- Processing 5 tasks with TPS=1 should take at least 4 seconds

### 2. Mall Independence Verification
- Multiple malls can process work simultaneously while maintaining independent TPS

### 3. Statistics Accuracy Verification
- Verify processed task count, queued task count, and actual TPS

## Learning Points

1. **Concurrency Handling with Actor Model**: Creating independent actors for each mall to handle concurrency
2. **Reactive Streams Utilization**: Asynchronous processing using Pekko Streams
3. **Backpressure Handling**: Automatic flow control through OverflowStrategy.backpressure()
4. **Testable Design**: Actor behavior verification using TestProbe

## Extensibility

- **Dynamic TPS Adjustment**: Change TPS limits at runtime
- **Priority Queue**: Assign priorities to different tasks
- **Circuit Breaker**: Automatic failure isolation
- **Metrics Collection**: Integration with monitoring systems like Prometheus

This project demonstrates the basic concepts of TPS control using the actor model and provides patterns that can be extended for use in production environments.