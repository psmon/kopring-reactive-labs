# ACTOR_STREAM - Stream Processing Comparison Tutorial

This project is a tutorial that implements and compares the same stream processing task using Pekko Streams, Java Streams API, WebFlux, and Kotlin Coroutines.

## Project Goals

Implement a stream pipeline that separates and processes words and numbers from strings:
- Input: "abc 345 def sdf"
- Output: word list ["abc", "def", "sdf"], word count 3, number sum 345

## Key Concepts

### 1. Pekko Streams (Actor-based)

Pekko Streams is a reactive stream implementation built on top of the actor model.

#### Core Features:
- **Graph DSL**: Visual representation of complex stream processing
- **Backpressure**: Automatic flow control
- **Actor Integration**: Natural integration with actor systems

#### Implementation Example:
```kotlin
val graph = RunnableGraph.fromGraph(
    GraphDSL.create { builder ->
        // Define source
        val source = builder.add(Source.single(text))
        
        // Word/number separation
        val splitter = builder.add(Flow.of(String::class.java)
            .map { /* separation logic */ })
        
        // Broadcast for parallel processing
        val broadcast = builder.add(Broadcast.create<Pair<List<String>, List<Int>>>(2))
        
        // Result merging
        val zip = builder.add(Zip.create<List<String>, Int>())
        
        // Connect graph
        builder.from(source).via(splitter).viaFanOut(broadcast)
        // ...
    }
)
```

### 2. Java Streams API

Functional programming style stream processing introduced in Java 8.

#### Core Features:
- **Simple API**: Intuitive method chaining
- **Parallel Processing**: Easy parallelization with `parallel()` method
- **Synchronous**: Blocking operations by default

#### Implementation Example:
```kotlin
val words = parts.stream()
    .filter { it.matches("[^\\d]+".toRegex()) }
    .collect(Collectors.toList())

val numberSum = parts.stream()
    .filter { it.matches("\\d+".toRegex()) }
    .mapToInt { it.toInt() }
    .sum()
```

### 3. WebFlux (Reactor)

Spring's reactive programming library.

#### Core Features:
- **Non-blocking**: Fully asynchronous processing
- **Backpressure**: Various backpressure strategies
- **Rich Operators**: Diverse transformation and combination operators

#### Implementation Example:
```kotlin
Mono.just(text)
    .flatMap { parts ->
        val wordsFlux = Flux.fromIterable(parts)
            .filter { /* word filter */ }
            .collectList()
        
        val numberSumMono = Flux.fromIterable(parts)
            .filter { /* number filter */ }
            .map { it.toInt() }
            .reduce(0) { acc, num -> acc + num }
        
        Mono.zip(wordsFlux, numberSumMono)
    }
```

### 4. Kotlin Coroutines

Kotlin's lightweight thread implementation.

#### Core Features:
- **Structured Concurrency**: Safe concurrency management
- **Flow API**: Cold stream processing
- **Channels**: Hot stream processing
- **Readability**: Asynchronous code that looks like synchronous code

#### Implementation Example:
```kotlin
suspend fun processText(text: String): StreamResult = coroutineScope {
    val parts = text.split("\\s+".toRegex())
    
    // Parallel processing
    val wordsDeferred = async {
        parts.filter { it.matches("[^\\d]+".toRegex()) }
    }
    
    val numberSumDeferred = async {
        parts.filter { it.matches("\\d+".toRegex()) }
            .map { it.toInt() }
            .sum()
    }
    
    StreamResult(
        originalText = text,
        words = wordsDeferred.await(),
        wordCount = wordsDeferred.await().size,
        numberSum = numberSumDeferred.await()
    )
}
```

## Performance Comparison

Characteristics of each implementation:

| Implementation | Advantages | Disadvantages | Suitable Use Cases |
|---------------|-----------|--------------|------------------|
| **Pekko Streams** | - Complex graph processing<br>- Strong backpressure<br>- Actor integration | - High learning curve<br>- Complex setup | Complex stream topologies, actor system integration |
| **Java Streams** | - Simple API<br>- Java standard<br>- Fast synchronous processing | - No backpressure<br>- Limited async support | Simple data transformation, synchronous processing |
| **WebFlux** | - Fully reactive<br>- Spring ecosystem<br>- Rich operators | - Difficult debugging<br>- Complex stack traces | Web applications, reactive systems |
| **Coroutines** | - Readable code<br>- Structured concurrency<br>- Kotlin native | - Kotlin only<br>- Smaller ecosystem | Kotlin projects, Android |

## How to Run

### Run Tests:
```bash
./gradlew test
```

### Run Individual Tests:
```bash
./gradlew test --tests "*ProcessorComparisonTest*"
```

## Key Learning Points

1. **Backpressure Handling**: How each implementation handles data overload
2. **Parallel Processing**: Each library's approach to parallelization
3. **Error Handling**: Error propagation methods during stream processing
4. **Resource Management**: Resource usage patterns of each implementation

## Conclusion

- **Simple Processing**: Java Streams API
- **Reactive Web**: WebFlux
- **Complex Graphs**: Pekko Streams
- **Kotlin Projects**: Coroutines

Each tool has advantages in specific situations, and appropriate selection is needed according to project requirements.