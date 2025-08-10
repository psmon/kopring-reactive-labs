# LLM Throttle System

A token-based rate limiting system for LLM API calls. Built with Apache Pekko Actors, it provides stable API usage management through backpressure mechanisms.

## Two Implementation Versions

### 1. LLMThrottleActor (Basic Version)
- Traditional actor-based approach
- Manual backpressure and delay handling
- Simple and intuitive structure

### 2. LLMStreamThrottleActor (Enhanced Version)
- **Pekko Streams** based automatic rate control
- Built-in throttle mechanism utilization
- Asynchronous stream processing optimization

## Key Features

- **Token-based Rate Limiting**: Token usage limit per minute (default: 10,000 tokens/min)
- **Progressive Backpressure**: Gradual delay processing based on capacity
- **Failed Request Management**: Retry queue management for requests exceeding limits
- **Sliding Window**: Accurate usage tracking through 60-second sliding window
- **Automatic Rate Control**: (StreamThrottleActor) Dynamic processing rate adjustment based on token usage

## Architecture

### Core Components

#### 1. LLMThrottleActor (Basic Version)
Main actor with the following responsibilities:
- Token usage tracking and management
- Capacity-based request processing decisions
- Backpressure application
- Failed request management

#### 2. LLMStreamThrottleActor (Enhanced Version)
High-performance actor utilizing Pekko Streams with the following features:
- **Automatic Rate Control**: Dynamic processing rate adjustment based on token usage
- **Stream-based Processing**: High throughput through asynchronous streams
- **Built-in Backpressure**: Utilization of Pekko Streams' built-in backpressure mechanism
- **Queue Management**: Automatic overflow handling and buffering

#### 3. TokenCalculator
Interface for converting text to tokens:
```kotlin
interface TokenCalculator {
    fun calculateTokens(text: String): Int
}
```

MockTokenCalculator uses the formula `character count + random(0~500)`.

#### 4. Command/Response Models
- `ProcessLLMRequest`: LLM processing request
- `LLMResponse`: Successful processing response
- `LLMThrottled`: Delayed processing response
- `LLMFailed`: Failed request response

### Backpressure Strategies

#### Basic Version (LLMThrottleActor)
The system applies gradual delays based on current capacity:

| Capacity Usage | Delay Time | Action |
|---------------|-----------|--------|
| < 70% | None | Immediate processing |
| 70-80% | 100ms | Slight delay |
| 80-90% | 300ms | Medium delay |
| 90-95% | 1,000ms | High delay |
| > 95% | 2,000ms | Maximum delay |
| > 100% | - | Move to failure queue |

#### Enhanced Version (LLMStreamThrottleActor)
Utilizes Pekko Streams' dynamic throttling:

| Capacity Usage | Processing Rate (req/sec) | Action |
|---------------|-------------------------|--------|
| < 70% | 10 | Maximum processing rate |
| 70-80% | 8 | Slight reduction |
| 80-90% | 5 | Medium reduction |
| 90-95% | 3 | Large reduction |
| > 95% | 1 | Minimum processing rate |
| > 100% | - | Move to failure queue |

## Usage

### Basic Usage

#### 1. Basic Version (LLMThrottleActor)

```kotlin
// Create Actor system
val system = ActorSystem.create<MainStageActorCommand>(
    MainStageActor.create(), "llm-system"
)

// Create LLMThrottleActor
val throttleActor = system.systemActorOf(
    LLMThrottleActor.create(
        tokenCalculator = MockTokenCalculator(),
        tokenLimitPerMinute = 10_000
    ),
    "llm-throttle"
)
```

#### 2. Enhanced Version (LLMStreamThrottleActor)

```kotlin
// Create Actor system
val system = ActorSystem.create<MainStageActorCommand>(
    MainStageActor.create(), "llm-system"
)

// Create LLMStreamThrottleActor (Pekko Streams based)
val streamThrottleActor = system.systemActorOf(
    LLMStreamThrottleActor.create(
        tokenCalculator = MockTokenCalculator(),
        tokenLimitPerMinute = 10_000
    ),
    "llm-stream-throttle"
)

```

#### Request Processing (Same for both versions)

```kotlin
// Request processing
val response = AskPattern.ask(
    throttleActor, // or streamThrottleActor
    { replyTo -> ProcessLLMRequest(
        requestId = UUID.randomUUID().toString(),
        content = "Process this text",
        replyTo = replyTo
    ) },
    Duration.ofSeconds(10),
    system.scheduler()
).toCompletableFuture().get()

// Response handling
when (response) {
    is LLMResponse -> {
        println("Processing complete: ${response.processedContent}")
        println("Tokens used: ${response.tokensUsed}")
    }
    is LLMThrottled -> {
        println("Delayed processing: wait ${response.delayMs}ms")
        println("Current capacity: ${response.currentCapacityPercent}%")
    }
    is LLMFailed -> {
        println("Processing failed: ${response.reason}")
        if (response.canRetry) {
            println("Retry available")
        }
    }
}
```

### State Monitoring

```kotlin
// Query current throttle state
val state = AskPattern.ask(
    throttleActor,
    { replyTo -> GetThrottleState(replyTo) },
    Duration.ofSeconds(5),
    system.scheduler()
).toCompletableFuture().get() as ThrottleState

println("Current token usage: ${state.currentTokensUsed}/${state.tokenLimit}")
println("Capacity usage: ${state.capacityPercent}%")
println("Failed requests: ${state.failedRequestsCount}")
```

### Failed Request Management

```kotlin
// Query failed request list
val failedList = AskPattern.ask(
    throttleActor,
    { replyTo -> GetFailedRequests(replyTo) },
    Duration.ofSeconds(5),
    system.scheduler()
).toCompletableFuture().get() as FailedRequestsList

failedList.requests.forEach { request ->
    println("Failed request ID: ${request.requestId}")
    println("Failure time: ${Date(request.failedAt)}")
    println("Failure reason: ${request.reason}")
}

// Retry failed requests
throttleActor.tell(RetryFailedRequests)
```

## Configuration

### Custom Token Calculator

You can implement TokenCalculator to match actual LLM API token calculation methods:

```kotlin
class OpenAITokenCalculator : TokenCalculator {
    override fun calculateTokens(text: String): Int {
        // OpenAI's tiktoken library logic
        return text.length / 4 // Simple approximation
    }
}
```

### Token Limit Adjustment

```kotlin
val throttleActor = LLMThrottleActor.create(
    tokenCalculator = CustomTokenCalculator(),
    tokenLimitPerMinute = 50_000 // 50,000 tokens per minute
)
```

## Internal Operations

### Sliding Window

Both versions use a 60-second sliding window to track token usage:

1. Each request is stored in the token window with a timestamp
2. All tokens within 60 seconds from the current time are calculated
3. Expired windows are automatically cleaned up every 5 seconds

### State Management

#### Basic Version (LLMThrottleActor)
The actor manages the following state:
- `tokenWindows`: Time-based token usage tracking
- `failedRequests`: Queue of failed processing requests
- `stashedRequests`: Requests waiting for delayed processing

#### Enhanced Version (LLMStreamThrottleActor)
Stream-based state management:
- `tokenWindows`: Thread-safe management with ConcurrentHashMap
- `failedRequests`: Asynchronous processing with ConcurrentLinkedQueue
- `requestQueue`: Buffering with Pekko Streams SourceQueue

### Concurrency Handling

#### Basic Version
- Single-threaded processing of Pekko Actor model
- Race condition prevention through message-based communication
- Manual backpressure mechanism

#### Enhanced Version
- Asynchronous processing of Pekko Streams
- Built-in backpressure and overflow strategies
- Automatic load balancing through dynamic throttling
- Thread-safe token management through ConcurrentHashMap

## Testing

The project includes the following test scenarios for each implementation:

### Basic Version (LLMThrottleActorTest)
1. **Basic Processing Test**: Immediate processing at under 70% capacity
2. **Backpressure Test**: Verification of delay times by capacity
3. **Capacity Overflow Test**: Verification of failure queue operation
4. **Sliding Window Test**: Token expiration over time
5. **Concurrency Test**: Multiple request processing

### Enhanced Version (LLMStreamThrottleActorTest)
1. **Stream Processing Test**: Stream-based processing at low capacity
2. **Dynamic Throttling Test**: Automatic rate adjustment by capacity
3. **Queue Overflow Test**: Graceful handling when queue capacity is exceeded
4. **Capacity Overflow Test**: Verification of failure queue operation
5. **Retry Test**: Reprocessing of failed requests
6. **Sliding Window Test**: Token expiration over time

Running tests:
```bash
./gradlew test
```

## Performance Considerations

### Basic Version (LLMThrottleActor)
1. **Memory Usage**: Token windows are stored in memory, so regular cleanup is important for long-term operation
2. **Timer Accuracy**: The 5-second cleanup timer may be delayed depending on system load
3. **Throughput**: Actors run in single threads, so consider actor routing for very high throughput requirements

### Enhanced Version (LLMStreamThrottleActor)
1. **Throughput**: High throughput provided by Pekko Streams' asynchronous processing
2. **Memory Efficiency**: Memory usage optimization through ConcurrentHashMap and stream buffering
3. **Backpressure Performance**: System stability improvement through built-in backpressure mechanism
4. **Dynamic Control**: Optimal performance maintenance through automatic rate adjustment based on real-time load

### Comparison and Selection Guide

| Feature | Basic Version | Enhanced Version |
|---------|-------------|-----------------|
| Implementation Complexity | Low | High |
| Throughput | Medium | High |
| Memory Usage | Normal | Efficient |
| Backpressure | Manual | Automatic |
| Debugging Ease | High | Medium |
| Operational Stability | Good | Very Good |

**Recommended Usage Scenarios:**
- **Basic Version**: Simple requirements, low throughput, debugging importance
- **Enhanced Version**: High throughput, complex load patterns, operational stability importance

## Future Improvements

1. **Persistence**: Adding persistence to maintain token usage even after restart
2. **Distributed Processing**: Token usage sharing in cluster environments
3. **Dynamic Thresholds**: Dynamic threshold adjustment based on load
4. **Metrics Collection**: Metrics exposure for Prometheus/Grafana integration
5. **Webhook Support**: Webhook notifications for failed requests

## License

This project is written for educational and reference purposes.