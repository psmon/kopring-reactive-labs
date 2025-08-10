# Project Creation Guidelines

I want to create a Kotlin-based Spring Boot project in the AgenticCoding/Projects/SSE-PUSH-SYSTEM folder.
Please follow these guidelines when writing the code.

## Core Functionality Description
- I want to create a push service using SSE (Server-Sent Events) functionality.
- The server should be able to publish real-time events to topics.
- For users who couldn't connect, I want to store up to the latest 100 topics that occurred in the past for each topic when they connect next time.
- Push will be handled via POST method, and SSE reception will be handled via GET method.
- Topic-related state processing should be handled by the actor model per user ID.

## Unit Testing and Additional Guidelines
- Please write Swagger documentation with comments for the completed code.
- After completing the code, write and attempt the following unit tests:
- User 1 subscribes to topic A, and user 2 subscribes to topic B... when a message is generated for topic A, only user 1 receives it.
- If user 3 connects late after server messages are generated... user 3 can receive up to 100 past generated topics.
- Once the unit test code is completed, explain the code concept and tutorial in readme.md in an easy-to-understand way for beginners.

## Test Client
- After the unit tests succeed, please also write an additional test web client based on that.
- I want to add client functionality that can test SSE in resources/static/index.html.
- The client has two views, left/right: the left screen is a client that receives SSE, and the right view is a client that generates SSE.

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
- When unit tests are needed, refer to the methods used in test files
- Dependencies required for Spring Boot should match the same versions as this sample code - using Gradle