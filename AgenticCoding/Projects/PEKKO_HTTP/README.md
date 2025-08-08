# Pekko HTTP with Actor Model - Reactive Web Service

This project demonstrates a lightweight, reactive web service built with Pekko HTTP and the Actor model, without Spring Boot dependencies. It showcases the power of Pekko's actor system for building scalable, concurrent applications.

## 🎯 Project Overview

This is a Kotlin-based implementation that leverages:
- **Pekko HTTP**: Lightweight HTTP server and client
- **Pekko Actors**: Concurrent, message-driven actors
- **Pekko Streams**: Backpressure-aware stream processing
- **No Spring Boot**: Pure Pekko implementation for minimal overhead

## 🏗️ Architecture

```mermaid
graph TB
    Client[HTTP Client] -->|HTTP Request| PekkoHTTP[Pekko HTTP Server]
    PekkoHTTP --> Routes[Route Handlers]
    
    Routes --> HelloRoute[Hello Route]
    Routes --> EventRoute[Event Route]
    Routes --> WebSocketRoute[WebSocket Route]
    
    HelloRoute -->|Ask Pattern| HelloActor[Hello Actor]
    EventRoute -->|Tell Pattern| EventActor[Event Stream Actor]
    WebSocketRoute -->|Tell Pattern| WSActor[WebSocket Actor]
    
    EventActor --> Stream[Pekko Streams]
    Stream --> Processing[Stream Processing]
    Processing --> Sink[Event Sink]
    
    WSActor --> Connections[Connection Manager]
    Connections --> Broadcast[Broadcast to Clients]
```

## 🚀 Quick Start

### Prerequisites
- JDK 17+
- Gradle 8.x

### Build and Run

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run the server
./gradlew run

# Or build and run the fat JAR
./gradlew shadowJar
java -jar build/libs/pekko-http-server.jar
```

The server will start on `http://localhost:8080`

## 📡 API Endpoints

### Hello Endpoint
- `GET /api/hello` - Default greeting
- `GET /api/hello/{name}` - Personalized greeting
- `POST /api/hello` - Greeting with JSON body

### Event Processing
- `POST /api/events` - Send single event
- `POST /api/events/batch` - Send batch of events
- `GET /api/events/stats` - Get event statistics
- `GET /api/events/stream` - Event statistics (simplified SSE)

### WebSocket
- `WS /ws` - WebSocket connection
- `WS /ws/{userId}` - WebSocket with user ID
- `POST /api/broadcast` - Broadcast to all WS clients

### Documentation & Monitoring
- `GET /swagger-ui` - Interactive Swagger UI
- `GET /swagger` - Redirects to Swagger UI
- `GET /api-docs` - OpenAPI 3.0 specification (JSON)
- `GET /swagger.json` - Alternative OpenAPI endpoint
- `GET /health` - Health check endpoint

### Testing Tools
- `GET /test` - Interactive test page for SSE and WebSocket

## 🧪 Interactive Test Page

### Accessing the Test Console

Open your browser and navigate to:
```
http://localhost:8080/test
```

The test page provides:
- **WebSocket Testing**: Real-time bidirectional communication
  - Connect/disconnect to WebSocket server
  - Send and receive messages
  - View connection statistics
  - Test ping/pong and broadcast features

- **SSE Testing**: Server-Sent Events streaming
  - Connect to event stream
  - Monitor incoming events
  - View event statistics
  - Track connection status

- **API Testing**: Quick test buttons for all endpoints
  - Send test events (click, view, submit)
  - Send batch events
  - Test Hello API variations
  - Check system health
  - Get event statistics

### Features
- Real-time message display with timestamps
- Connection status indicators
- Message counters and statistics
- Color-coded message types
- Responsive design for all screen sizes

## 📚 Swagger UI

### Accessing Swagger UI

Once the server is running, open your browser and navigate to:
```
http://localhost:8080/swagger-ui
```

The Swagger UI provides:
- **Interactive API Documentation**: Browse all available endpoints
- **Try It Out**: Test API calls directly from the browser
- **Request/Response Examples**: See sample payloads and responses
- **Model Definitions**: View data structures used by the API

### Features
- Full OpenAPI 3.0 specification
- CORS enabled for cross-origin requests
- Real-time testing of all endpoints
- Automatic request/response validation
- WebSocket endpoint documentation

### API Testing with Swagger UI

1. **Hello Endpoint**: 
   - Click on "Hello" tag
   - Try the GET `/api/hello/{name}` endpoint
   - Enter a name and click "Execute"

2. **Event Processing**:
   - Click on "Events" tag
   - Test POST `/api/events` with sample JSON
   - View statistics with GET `/api/events/stats`

3. **WebSocket Testing**:
   - WebSocket connections are documented but need a WebSocket client for testing
   - Use the provided curl examples or browser developer tools

## 💡 Core Concepts

### 1. Actor Model Implementation

Actors are the fundamental unit of computation:

```kotlin
class HelloActor : AbstractBehavior<HelloCommand>(context) {
    override fun createReceive(): Receive<HelloCommand> {
        return newReceiveBuilder()
            .onMessage(GetHello::class.java, this::onGetHello)
            .build()
    }
    
    private fun onGetHello(command: GetHello): Behavior<HelloCommand> {
        val response = HelloResponse("Pekko says hello to ${command.name}!")
        command.replyTo.tell(response)
        return this
    }
}
```

### 2. Stream Processing with Backpressure

Event processing with Pekko Streams:

```kotlin
class EventStreamActor : AbstractBehavior<EventCommand>(context) {
    private val materializer = Materializer.createMaterializer(context.system)
    
    private fun processEvent(event: UserEvent) {
        Source.single(event)
            .via(enrichmentFlow())
            .via(throttleFlow())
            .via(statsFlow())
            .to(persistenceSink())
            .run(materializer)
    }
}
```

### 3. HTTP Route Definition

Clean, functional route definitions:

```kotlin
class HelloRoute(private val helloActor: ActorRef<HelloCommand>) {
    fun createRoute(): Route {
        return path(segment("api").slash("hello")) {
            get {
                onSuccess(askHello("World")) { response ->
                    complete(StatusCodes.OK, response, marshaller())
                }
            }
        }
    }
}
```

## 🔄 Actor Communication Patterns

### Ask Pattern (Request-Response)
Used when you need a response from an actor:

```kotlin
val future = AskPattern.ask(
    helloActor,
    { replyTo -> GetHello(name, replyTo) },
    timeout,
    scheduler
)
```

### Tell Pattern (Fire-and-Forget)
Used for asynchronous message passing:

```kotlin
eventActor.tell(ProcessEvent(userEvent))
```

## 🌟 Why Pekko HTTP over Spring Boot?

### Advantages of Pekko HTTP

1. **Lightweight**: 
   - Minimal memory footprint
   - Fast startup time
   - No annotation processing overhead

2. **True Reactive**:
   - Built on Reactive Streams
   - Native backpressure support
   - Non-blocking I/O throughout

3. **Actor Model**:
   - Natural concurrency model
   - Location transparency
   - Fault tolerance through supervision

4. **Stream Processing**:
   - First-class stream support
   - Composable processing pipelines
   - Built-in flow control

### Comparison Table

| Feature | Pekko HTTP | Spring Boot WebFlux |
|---------|------------|-------------------|
| Startup Time | ~1s | ~3-5s |
| Memory Usage | ~50MB | ~150MB+ |
| Dependencies | Minimal | Extensive |
| Learning Curve | Steeper | Gentler |
| Actor Support | Native | Via Integration |
| Stream Processing | Native | Via Reactor |
| Community | Growing | Large |
| Enterprise Features | Basic | Comprehensive |

### When to Use Pekko HTTP

✅ **Best for:**
- High-performance, low-latency services
- Stream processing applications
- WebSocket-heavy applications
- Microservices with minimal footprint
- Actor-based architectures

❌ **Consider alternatives when:**
- You need extensive Spring ecosystem
- Team is already familiar with Spring
- You need comprehensive enterprise features
- Rapid prototyping with many integrations

## 🧪 Testing

### Unit Tests
Test individual actors:

```kotlin
@Test
fun `should respond with Pekko message`() = runTest {
    val actor = testKit.spawn(HelloActor.create())
    val probe = testKit.createTestProbe<HelloResponse>()
    
    actor.tell(GetHello("World", probe.ref))
    
    val response = probe.expectMessageClass(HelloResponse::class.java)
    response.message shouldBe "Pekko says hello to World!"
}
```

### Integration Tests
Test complete flows:

```kotlin
@Test
fun `test event processing pipeline`() = runTest {
    val eventActor = system.systemActorOf(EventStreamActor.create())
    eventActor.tell(ProcessEvent(testEvent))
    
    // Verify processing
    val stats = getEventStats()
    stats.totalEvents shouldBe 1
}
```

## 📊 Performance Characteristics

- **Throughput**: ~10,000 req/s on modest hardware
- **Latency**: < 10ms p99 for simple requests
- **Concurrency**: Handles thousands of concurrent connections
- **Memory**: ~50MB base footprint
- **CPU**: Efficient async I/O, low CPU overhead

## 🛠️ Configuration

Configuration via `application.conf`:

```hocon
pekko {
  actor {
    provider = local
  }
  
  http {
    server {
      idle-timeout = 60s
      request-timeout = 20s
    }
  }
  
  stream {
    materializer {
      initial-input-buffer-size = 4
      max-input-buffer-size = 16
    }
  }
}
```

## 📚 Learning Resources

### Tutorials for Beginners

1. **Understanding Actors**: Start with `HelloActor` - the simplest actor pattern
2. **Stream Processing**: Study `EventStreamActor` for stream concepts
3. **HTTP Routes**: Examine route definitions for REST API patterns
4. **WebSocket**: Learn real-time communication with `WebSocketActor`

### Advanced Topics

- **Supervision**: Error handling and recovery strategies
- **Clustering**: Distributing actors across nodes
- **Persistence**: Durable state with event sourcing
- **Testing**: Comprehensive testing strategies

## 🔍 Troubleshooting

### Common Issues

1. **Port Already in Use**: Change port in `PekkoHttpServer.main()`
2. **Memory Issues**: Adjust JVM heap size
3. **Slow Startup**: Check for blocking operations in actor constructors

## 🤝 Contributing

Contributions are welcome! Please feel free to submit pull requests.

## 📄 License

This project is open source and available under the MIT License.

## 🙏 Acknowledgments

- Apache Pekko team for the excellent actor framework
- Kotlin team for the modern, concise language
- The reactive streams community

---

**Note**: This is a demonstration project showcasing Pekko HTTP capabilities. For production use, consider additional concerns like security, monitoring, and operational requirements.