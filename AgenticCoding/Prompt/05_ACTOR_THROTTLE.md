# Project Creation Guidelines
- Starting fresh in the AgenticCoding/Projects/ACTOR_THROTTLE folder.
- I want to create a Kotlin-based functional module only, not as an application.
- I want to write the following actors and perform execution and verification only in unit tests.

## Actor Implementation and Unit Test Implementation
- I want to write a tutorial-style actor model that equips the actor model with a throttle mechanism to control TPS.
- Work is generated in "mallID" units, and work for each mallID is constrained to TPS1 settings.
- A work manager manages sub-work actors by mallID.

## Multi-language Writing Guidelines Support
- README.md should be written in English
- README-kr.md should be written in Korean (translate the English version to Korean)

## Throttle

The throttle mechanism refers to the following~ Control without Sleep (thread blocking)

```
val helloLimitSource = Source.queue<HelloLimit>(100, OverflowStrategy.backpressure())
    .throttle(3, Duration.ofSeconds(1))
    .to(Sink.foreach { cmd ->
        // Send while adhering to TPS3.
        helloStateActor.tell(Hello(cmd.message, cmd.replyTo))
    })
    .run(materializer)
// You can request 100 events simultaneously, but they will be processed sequentially due to TPS constraints.
for (i in 1..100) {
    helloLimitSource.offer(HelloLimit("Hello", probe.ref()))
}   
```

## Unit Test Execution and Additional Guidelines
- After completing the code, attempt unit testing of the completed code.
- Use pekko testkit to verify and check TPS through completion target probe.
- Once the unit test code is completed, explain the code concept and tutorial in readme.md in an easy-to-understand way for beginners.

## Reference Code Prerequisites

There are sample codes worth referencing in the following directories:

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