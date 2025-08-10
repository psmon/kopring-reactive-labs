# Project Creation Guidelines
- Create a project in the AgenticCoding/Projects/CONNECTOR_KAFKA folder.
- I want to create a Kotlin-based functional module only, not as an application.
- I want to write, execute, and verify unit tests that meet the following functional requirements.
- Once all writing is completed, explain the code concept and tutorial in readme.md in an easy-to-understand way for beginners.

## Implementation Module and Unit Tests
- The producer sends messages to topic name `test-topic1`.
- Messages are composed in the form "eventType, eventId, eventString" as Java serialized objects.
- The consumer receives messages from test-topic1.
- The consumer is connected to an actor model and stores the last eventString value as state.
- Verify the number of events produced and the number of events received.

## Local Environment Additional Guidelines
- Configure Kafka using DockerCompose.
- After running the infrastructure~ perform unit tests to verify build errors and unit tests.

## Kafka Usage Sample Code
Dependencies:
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/discovery.html#dependency

Usage examples:
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/atleastonce.html#at-least-once-delivery
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/atleastonce.html#multiple-effects-per-commit
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/atleastonce.html#conditional-message-processing

Advanced techniques:
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/transactions.html#transactional-source
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/transactions.html#transactional-sink-and-flow
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/transactions.html#consume-transform-produce-workflow
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/transactions.html#caveats
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/transactions.html#further-reading

Error handling:
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/errorhandling.html#failing-consumer
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/errorhandling.html#failing-producer
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/errorhandling.html#restarting-the-stream-with-a-backoff-stage
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/errorhandling.html#unexpected-consumer-offset-reset

Unit testing:
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/testing.html#testing
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/testing.html#running-kafka-with-your-tests
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/testing.html#alternative-testing-libraries
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/testing.html#mocking-the-consumer-or-producer
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/testing-testcontainers.html

## Unit Test Execution and Additional Guidelines
- After completing the code, attempt unit testing of the completed code.
- Use pekko testkit to verify and check TPS through completion target probe.
- Once the unit test code is completed, explain the code concept and tutorial in readme.md in an easy-to-understand way for beginners.

## Multi-language Writing Guidelines Support
- README.md should be written in English
- README-kr.md should be written in Korean (translate the English version to Korean)

## Pekko Reference Knowledge

There are Pekko sample codes that work with Kotlin in the following directories:

```
current working directory:
├── CommonModel/
│   └── src/
├── Docs/
│   ├── eng/
│   └── kr/
├── KotlinBootReactiveLabs/
│   └── src/
│       ├── main/
│       └── test/
└── README.MD
```

### Reference Targets
- Reference target directories refer to files in the subdirectories of the reference code locations
- Reactive stream-based concurrency processing and various actor models are implemented in Kotlin based on Spring Boot.
- For code learning targets, refer to *.kt and .md files
- When unit tests are needed, refer to and improve upon the methods used in test files
- When dependencies are the same as this sample code, match the same versions - using Gradle