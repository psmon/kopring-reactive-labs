# Project Creation Guidelines

Starting fresh in the AgenticCoding/Projects/LLM-THROTTLE folder.

I want to create a Kotlin-based functional module only, not as an application.
I want to write the following actors and perform execution and verification only in unit tests.

## Actor Implementation and Unit Test Implementation
- I want to create a device that constrains and controls LLM calls using a throttle mechanism
- Create the LLM token meter as a virtual class, applying character count + random(0~500)
- The LLM per-minute constraint is 10,000 tokens
- The purpose of this device is to perform well as a backpressure mechanism to prevent usage when tokens are exceeded. When approaching the threshold, it's acceptable to respond slowly
- If the threshold is not reached, respond as stably as possible
- If the limit is reached and processing cannot be done, load failed items into a separate failed item list... batch services can process them later if needed
- Create one additional extended version of LLMThrottleActor that automatically controls speed using PekkoStream's throttle mechanism. The core point is being able to automatically adjust speed using this, and both versions (with and without this feature) are needed

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