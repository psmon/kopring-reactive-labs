# CONNECTOR_KAFKA - Kafka Connector with Actor Model

## Overview

This project is an event processing system that combines Apache Kafka with the Pekko Actor model. It implements stable and scalable message processing through Kafka producer/consumer and Actor-based state management.

## Key Features

- ✅ Event transmission through Kafka producer
- ✅ Integration of Kafka consumer with Actor model
- ✅ Event state management (storing last event)
- ✅ Event count tracking
- ✅ Batch processing support
- ✅ Event transmission with metadata
- ✅ High throughput (TPS) support

## Project Structure

```
CONNECTOR_KAFKA/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/example/connectorkafka/
│   │   │       ├── model/          # Event models and serialization
│   │   │       ├── producer/       # Kafka producer
│   │   │       ├── actor/          # Event processing Actor
│   │   │       └── connector/      # Kafka-Actor connection
│   │   └── resources/
│   │       ├── application.conf    # Pekko/Kafka configuration
│   │       └── logback.xml        # Logging configuration
│   └── test/
│       └── kotlin/                 # Unit and integration tests
├── docker-compose.yml              # Kafka cluster setup
└── build.gradle.kts               # Gradle build configuration
```

## Core Components

### 1. KafkaEvent Model
```kotlin
data class KafkaEvent(
    val eventType: String,
    val eventId: String,
    val eventString: String,
    val timestamp: Long = System.currentTimeMillis()
)
```
Defines the basic structure of events. Uses Jackson serialization to convert to Kafka messages.

### 2. EventProducer
Producer that sends events to Kafka:
- Single event transmission
- Batch event transmission
- Transmission with metadata
- Tracking transmitted event count

### 3. EventConsumerActor
Actor that processes events and manages state:
- Event reception and processing
- Maintaining last event state
- Managing processed event count
- State initialization functionality

### 4. KafkaConsumerConnector
Connector that links Kafka consumer with Actor:
- Receiving messages from Kafka topics
- Forwarding events to Actor
- Managing offset commits
- Graceful shutdown through Kill switch

## Getting Started

### Prerequisites

- JDK 17+
- Docker and Docker Compose
- Gradle 7.x+

### 1. Start Kafka Cluster

```bash
# Start Kafka cluster with Docker Compose
docker-compose up -d

# Check cluster status
docker-compose ps

# Access Kafka UI (browser)
# http://localhost:8080
```

### 2. Build Project

```bash
# Navigate to project directory
cd AgenticCoding/Projects/CONNECTOR_KAFKA

# Gradle build
./gradlew build
```

### 3. Run Tests

```bash
# Run all tests
./gradlew test

# Run specific test classes
./gradlew test --tests EventProducerTest
./gradlew test --tests EventConsumerActorTest
./gradlew test --tests KafkaIntegrationTest
```

## Usage Examples

### Producer Usage Example

```kotlin
// Create Actor system
val system = ActorTestKit.create("MySystem", config)

// Initialize producer
val producer = EventProducer(system.system(), "test-topic1")

// Send single event
val event = KafkaEvent(
    eventType = "USER_ACTION",
    eventId = UUID.randomUUID().toString(),
    eventString = "User clicked button"
)

producer.sendEvent(event).thenAccept { done ->
    println("Event sent successfully")
}

// Send batch events
val events = (1..100).map { i ->
    KafkaEvent(
        eventType = "BATCH_EVENT",
        eventId = "batch-$i",
        eventString = "Batch event $i"
    )
}

producer.sendEvents(events).thenAccept { done ->
    println("Batch sent: ${producer.getProducedCount()} events")
}
```

### Consumer Usage Example

```kotlin
// Initialize consumer connector
val consumer = KafkaConsumerConnector(
    system.system(),
    topicName = "test-topic1",
    groupId = "my-consumer-group"
)

// Start consuming
val control = consumer.startConsuming()

// Query state through Actor
val actor = consumer.getConsumerActor()
val probe = system.createTestProbe<EventResponse>()

// Query last event
actor.tell(GetLastEvent(probe.ref))
val lastEvent = probe.expectMessageClass(LastEventResponse::class.java)
println("Last event: ${lastEvent.event?.eventString}")

// Query event count
val countProbe = system.createTestProbe<EventCountResponse>()
actor.tell(GetEventCount(countProbe.ref))
val count = probe.expectMessageClass(EventCountResponse::class.java)
println("Total events processed: ${count.count}")
```

### Graceful Shutdown with Kill Switch

```kotlin
// Start with kill switch
val (killSwitch, future) = consumer.startConsumingWithKillSwitch()

// Processing logic...

// Graceful shutdown
killSwitch.shutdown()
future.toCompletableFuture().get(10, TimeUnit.SECONDS)
```

## Configuration Guide

### Key application.conf Settings

```hocon
kafka {
  bootstrap.servers = "localhost:9092,localhost:9093,localhost:9094"
}

pekko.kafka {
  producer {
    kafka-clients {
      acks = "all"              # Acknowledge all replicas
      retries = 3               # Number of retries
      compression.type = "gzip" # Compression type
    }
  }
  
  consumer {
    kafka-clients {
      auto.offset.reset = "earliest"  # Offset reset policy
      enable.auto.commit = false       # Manual commit
      max.poll.records = 500          # Maximum polling records
    }
  }
}
```

## Performance and Test Results

### Throughput (TPS) Tests
- **Producer TPS**: Average 1000+ events/sec
- **End-to-End TPS**: Average 500+ events/sec
- **Test Environment**: Local Docker Kafka cluster (3 brokers)

### Reliability Tests
- ✅ Event order guarantee
- ✅ Accurate event count matching
- ✅ State consistency maintenance
- ✅ Concurrency processing verification

## Architecture Concepts

### Advantages of Actor Model
1. **Isolated State Management**: Each Actor manages state independently
2. **Message-based Communication**: Loose coupling through asynchronous message passing
3. **Error Isolation**: Actor failures don't affect the entire system
4. **Scalability**: Easy scaling by increasing Actor instances

### Benefits of Kafka and Actor Integration
1. **Backpressure Control**: Automatic backpressure management with Pekko Streams
2. **Exactly-Once Processing**: Synchronization of offset commits and Actor state
3. **Resilience**: Automatic reconnection and recovery during failures
4. **Monitoring**: Fine-grained monitoring through Actor system

## Troubleshooting

### Kafka Connection Failure
```bash
# Check Kafka cluster status
docker-compose ps

# Check Kafka logs
docker-compose logs kafka-1

# Check topic list
docker exec -it kafka-broker-1 kafka-topics --list --bootstrap-server localhost:9092
```

### Test Failures
```bash
# Run tests with detailed logs
./gradlew test --info --stacktrace

# Run specific tests in debug mode
./gradlew test --tests KafkaIntegrationTest --debug
```

### Performance Issues
- Optimize batch size by adjusting `max.poll.records`
- Adjust concurrency level with `parallelism` settings
- Increase JVM heap memory: `-Xmx2g -Xms1g`

## References

- [Pekko Kafka Connector Documentation](https://pekko.apache.org/docs/pekko-connectors-kafka/current/)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Pekko Actor Documentation](https://pekko.apache.org/docs/pekko/current/typed/index.html)
- [Testcontainers Kafka Module](https://www.testcontainers.org/modules/kafka/)

## License

This project is written for educational purposes.