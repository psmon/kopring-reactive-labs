# Project Creation Guidelines

Starting fresh in the AgenticCoding/Projects/ACTOR_CONCURRENCY folder.

I want to create a Kotlin-based functional module only, not as an application.
I want to write the following actors and perform execution and verification only in unit tests.

## Actor Implementation and Unit Test Implementation
- Please create a basic hello actor. When you send "Hello", it responds with "Kotlin".
- The hello actor should be able to designate a test receiver actor that can receive responses.
- Use the Tell technique to confirm reception.
- Also confirm responses using the ASK technique and utilize all three of the following concurrency processing techniques: CompletableFuture, WebFlux, and Kotlin Coroutines.

## Unit Test Execution and Additional Guidelines
- After completing the code, attempt to run the completed code.
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