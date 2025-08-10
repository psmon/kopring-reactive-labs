# SSE Push System

A Kotlin-based Spring Boot reactive application implementing Server-Sent Events (SSE) with topic-based push notifications using the Actor Model (Apache Pekko).

## Features

- **Real-time SSE Streaming**: Clients can subscribe to multiple topics and receive real-time events
- **Topic-based Publishing**: Events can be published to specific topics
- **Event History**: Stores last 100 events per topic for late-joining clients
- **Actor Model**: Uses Apache Pekko actors for concurrent event management
- **Reactive Programming**: Built with Spring WebFlux for non-blocking I/O
- **REST API**: Comprehensive API for publishing events and managing subscriptions
- **Swagger Documentation**: Available at `/swagger-ui.html`
- **Web Test Client**: Interactive HTML client for testing SSE functionality

## Architecture

- **TopicManagerActor**: Manages topic subscriptions and event history
- **UserEventActor**: Handles user SSE connections and event distribution
- **SseController**: REST endpoints for SSE streaming and subscription management
- **PushController**: REST endpoints for publishing events

## Getting Started

### Prerequisites

- JDK 17 or higher
- Gradle

### Running the Application

```bash
./gradlew bootRun
```

The application will start on port 8080.

### Testing

Run the unit tests:

```bash
./gradlew test
```

### Web Test Client

Open your browser and navigate to:
```
http://localhost:8080/index.html
```

The test client has two panels:
- **Left Panel (SSE Receiver)**: Connect to SSE stream and subscribe to topics
- **Right Panel (Event Publisher)**: Publish events to topics

### API Documentation

Swagger UI is available at:
```
http://localhost:8080/swagger-ui.html
```

## API Endpoints

### SSE Streaming

- `GET /api/sse/stream?userId={userId}&topics={topics}` - Connect to SSE stream
- `POST /api/sse/subscribe?userId={userId}&topic={topic}` - Subscribe to a topic
- `POST /api/sse/unsubscribe?userId={userId}&topic={topic}` - Unsubscribe from a topic
- `GET /api/sse/history/{topic}` - Get event history for a topic

### Event Publishing

- `POST /api/push/event` - Publish a single event
- `POST /api/push/events/batch` - Publish multiple events
- `POST /api/push/broadcast?topics={topics}&data={data}` - Broadcast to multiple topics

## Usage Example

1. **Connect to SSE Stream**:
   ```bash
   curl -N "http://localhost:8080/api/sse/stream?userId=user1&topics=news,updates"
   ```

2. **Publish an Event**:
   ```bash
   curl -X POST http://localhost:8080/api/push/event \
     -H "Content-Type: application/json" \
     -d '{"topic":"news","data":"Breaking news!"}'
   ```

## Configuration

- Application configuration: `src/main/resources/application.properties`
- Pekko configuration: `src/main/resources/application.conf`
- Logging configuration: `src/main/resources/logback.xml`

## Technology Stack

- Kotlin 2.0
- Spring Boot 3.3.4
- Spring WebFlux (Reactive)
- Apache Pekko 1.1.2 (Actor System)
- H2 Database (In-memory)
- SpringDoc OpenAPI (Swagger)