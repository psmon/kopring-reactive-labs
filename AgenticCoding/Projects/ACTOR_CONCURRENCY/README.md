# Actor Concurrency with Kotlin

This project is a Kotlin-based module that demonstrates concurrent processing with the Actor Model using Apache Pekko (an open-source fork of Akka).

## Table of Contents
- [What is the Actor Model?](#what-is-the-actor-model)
- [Tell vs Ask Patterns](#tell-vs-ask-patterns)
- [Concurrency Model Comparison](#concurrency-model-comparison)
- [Project Structure](#project-structure)
- [Code Examples](#code-examples)
- [Running Tests](#running-tests)

## What is the Actor Model?

The Actor Model is a mathematical model for concurrent programming with the following characteristics:

1. **Actors**: Independent units of computation that encapsulate their own state and behavior
2. **Message-based Communication**: Actors communicate only through messages
3. **Asynchrony**: Messages are sent and processed asynchronously
4. **Isolation**: Each actor runs independently and doesn't share state

### Three Basic Operations of an Actor
- **Receive Messages**: Receive messages from other actors
- **Change State**: Can update internal state
- **Create New Actors**: Can create child actors when needed

## Tell vs Ask Patterns

### Tell Pattern (Fire-and-Forget)
```kotlin
// Send a message without waiting for a response
actor.tell(Hello("Hello"))
```
- **Pros**: Asynchronous and non-blocking
- **Cons**: Cannot receive responses
- **When to Use**: One-way communication or event notifications

### Ask Pattern (Request-Response)
```kotlin
// Send a message and wait for a response
val response = AskPattern.ask(
    actor,
    { replyTo -> Hello("Hello", replyTo) },
    Duration.ofSeconds(3),
    scheduler
)
```
- **Pros**: Can receive responses
- **Cons**: Requires timeout settings, slight overhead
- **When to Use**: Request-response patterns requiring answers

## Concurrency Model Comparison

### 1. CompletableFuture (Java Standard)
```kotlin
val future = AskPattern.ask(...).toCompletableFuture()
val result = future.get() // blocking
```
- **Features**: Java 8+ standard async API
- **Pros**: Widely used, supports various composition operations
- **Cons**: Poor readability when chaining

### 2. Reactor (WebFlux)
```kotlin
val mono = AskPattern.ask(...).toCompletableFuture().toMono()
val result = mono.block() // blocking
```
- **Features**: Reactive Streams implementation
- **Pros**: Backpressure, rich operators
- **Cons**: Learning curve

### 3. Kotlin Coroutines
```kotlin
suspend fun getResponse() {
    val result = AskPattern.ask(...).toCompletableFuture().await()
}
```
- **Features**: Kotlin native asynchronous programming
- **Pros**: Intuitive code, lightweight threads
- **Cons**: Kotlin-specific

## Project Structure

```
ACTOR_CONCURRENCY/
├── build.gradle.kts           # Build configuration
├── src/
│   ├── main/kotlin/
│   │   └── com/example/actorconcurrency/
│   │       ├── actor/
│   │       │   ├── HelloActor.kt         # Main actor
│   │       │   └── TestReceiverActor.kt  # Test receiver actor
│   │       └── model/
│   │           └── Commands.kt           # Message definitions
│   └── test/kotlin/
│       └── com/example/actorconcurrency/
│           └── actor/
│               └── HelloActorConcurrencyTest.kt  # Tests
└── README.md
```

## Code Examples

### 1. Actor Definition
```kotlin
class HelloActor(
    context: ActorContext<HelloCommand>,
    private val testReceiver: ActorRef<HelloCommand>? = null
) : AbstractBehavior<HelloCommand>(context) {
    
    override fun createReceive(): Receive<HelloCommand> {
        return newReceiveBuilder()
            .onMessage(Hello::class.java, this::onHello)
            .build()
    }
    
    private fun onHello(hello: Hello): Behavior<HelloCommand> {
        val response = HelloResponse("Kotlin")
        hello.replyTo?.tell(response)
        testReceiver?.tell(response)
        return this
    }
}
```

### 2. Using Tell Pattern
```kotlin
@Test
fun `test HelloActor with Tell pattern`() {
    val probe = testKit.createTestProbe<HelloCommand>()
    val actor = testKit.spawn(HelloActor.create(probe.ref))
    
    // Send message (don't wait for response)
    actor.tell(Hello("Hello"))
    
    // Verify response with probe
    probe.expectMessage(HelloResponse("Kotlin"))
}
```

### 3. Ask Pattern - Three Approaches

#### CompletableFuture
```kotlin
val future = AskPattern.ask(
    actor,
    { replyTo -> Hello("Hello", replyTo) },
    Duration.ofSeconds(3),
    scheduler
).toCompletableFuture()

val response = future.get()
```

#### Reactor Mono
```kotlin
val mono = AskPattern.ask(...).toCompletableFuture().toMono()
val response = mono.block()
```

#### Kotlin Coroutines
```kotlin
suspend fun askActor() {
    val response = AskPattern.ask(...).toCompletableFuture().await()
}
```

## Running Tests

### How to Run Tests
```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests HelloActorConcurrencyTest
```

### Test Coverage
1. **Tell Pattern Test**: Send message and verify with TestProbe
2. **Ask Pattern - CompletableFuture**: Using Java standard async API
3. **Ask Pattern - WebFlux**: Using Reactor Mono
4. **Ask Pattern - Coroutines**: Using Kotlin coroutines
5. **Concurrent Request Test**: Using multiple concurrency models together

## Key Concepts Summary

### Advantages of the Actor Model
- **Thread Safety**: No shared state means no synchronization issues
- **Scalability**: Actors are independent and easily scalable
- **Fault Isolation**: One actor's failure doesn't affect others
- **Location Transparency**: Actors communicate the same way whether local or remote

### Real-World Use Cases
- **Game Servers**: Model each player or NPC as an actor
- **IoT Systems**: Represent each device as an actor
- **Microservices**: Inter-service communication patterns
- **Real-time Processing**: Event-driven systems

## Additional Learning Resources
- [Apache Pekko Official Documentation](https://pekko.apache.org/)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Project Reactor Documentation](https://projectreactor.io/docs)

## License
This project was created for educational purposes.