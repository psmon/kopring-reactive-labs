# Project Creation Guidelines
- Create a project in the AgenticCoding/Projects/PERSIST_DURABLE_CLUSTER folder.
- I want to create a Kotlin-based functional module only, not as an application.
- I want to write, execute, and verify unit tests that meet the following functional requirements.

## Implementation Module and Unit Tests
- Analyze AgenticCoding/Projects/PERSIST_DURABLE that works as StandAlone and upgrade to Cluster mode
- Use mallId-userId as the distribution key for hashing

## Local Environment Additional Guidelines
- Configure PostgreSQL using DockerCompose.
- After running the infrastructure~ perform unit tests to verify build errors and unit tests.

## Unit Test Execution and Additional Guidelines
- After completing the code, attempt unit testing of the completed code in cluster mode.
- For tests that require waiting for a long time, use manualTime.timePasses.
- Use pekko testkit to verify and confirm through completion target probe. For cluster tests, probes should be verified and separated for the same node.
- Once the unit test code is completed, explain the code concept and tutorial in readme.md in an easy-to-understand way for beginners. If necessary, also include diagram explanations using mermaid.
- Additionally in readme.md, explain the advantages and disadvantages of each device compared to kafka-ktable and apache-flink when doing clustered state programming with pekko-persist.

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
- For message serialization used in clusters, refer to KotlinBootReactiveLabs/src/main/resources/application.conf and examine surrounding code
- For cluster unit tests, refer to KotlinBootReactiveLabs/src/test/kotlin/org/example/kotlinbootreactivelabs/actor/cluster/ClusterTest.kt