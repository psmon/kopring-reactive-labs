# Project Creation Guidelines

- Starting fresh in the AgenticCoding/Projects/ACTOR_ROUTER folder.
- I want to create a Kotlin-based functional module only, not as an application.
- I want to write the following actors and perform execution and verification only in unit tests.

## Actor Implementation and Unit Test Implementation
- I want to write a tutorial-style actor model to understand ACTOR Router.
- There is a WorkerActor as a worker, and there is a management actor above that distributes work.
- I want to implement various distributor samples supported by the router, including sequential distribution and random distribution.
- RouterActor distributes work to workers, and workers perform the work and then report the work to the manager.
- Workers should be able to monitor and report the workload of sub-workers.
- If the number of workers is needed, scale in/out should be possible for adjustment.
- Please attempt implementation with local actors only, and explain the method of extension to remote/cluster in documentation without implementation and execution. The key content is that locally implemented actors should be extensible without changing service logic.

## Unit Test Execution and Additional Guidelines
- After completing the code, attempt unit testing of the completed code.
- Once the unit test code is completed, explain the code concept and tutorial in readme.md in an easy-to-understand way for beginners.
- If asynchronous execution testing with Coroutines is needed, use runTest.

## Multi-language Writing Guidelines Support
- README.md should be written in English
- README-kr.md should be written in Korean (translate the English version to Korean)

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