I want to create a response API using Pekko HTTP and Pekko's actor model.
Please refer to the guidelines below to create it.

# Project Creation Guidelines
- Create a project in the AgenticCoding/Projects/PEKKO_HTTP folder.
- I want to create a Kotlin-based functional module only, not as an application.
- I want to write, execute, and verify unit tests that meet the following functional requirements.

## Core Implementation Features
- Create an actor model that responds with "Pekko" when "Hello" is requested, and connect it to an API.
- When user behavior events are called through the API~ send events to the actor model via Stream and output them as logging.
- If there are good sample types that connect PekkoHTTP with Pekko actor models and Streams, please write them additionally.

## Unit Test Execution and Additional Guidelines
- After completing the code, attempt unit testing of the completed code.
- In addition to unit tests, also perform integration tests using curl. Please also write a test documentation file that can test curl.
- Please also write swagger documentation for pekko-http.
- Explain the advantages and disadvantages of fully adopting Pekko's actor model through pekko-http. Please explain the reasons and advantages of implementing using pekko-http without using spring boot.
- Once unit test code is written and verified, explain this project's code concept and tutorial in readme.md in an easy-to-understand way for beginners. If necessary, also include diagram explanations using mermaid.

## Multi-language Writing Guidelines Support
- README.md should be written in English
- README-kr.md should be written in Korean (translate the English version to Korean)

# Pekko HTTP Reference
Implement web HTTP services using the following Pekko HTTP without using Spring Boot.
Do not use Spring dependencies and refer to the following to configure it lightweight.

- https://pekko.apache.org/docs/pekko-http/current/release-notes/releases-1.2.html
- https://pekko.apache.org/docs/pekko-http/current/introduction.html
- https://pekko.apache.org/docs/pekko-http/current/introduction.html#using-apache-pekko-http
- https://pekko.apache.org/docs/pekko-http/current/introduction.html#streaming
- https://pekko.apache.org/docs/pekko-http/current/introduction.html#marshalling
- https://pekko.apache.org/docs/pekko-http/current/configuration.html
- https://github.com/theiterators/pekko-http-microservice
- https://github.com/pjfanning/pekko-http-json
- https://github.com/swagger-akka-http/swagger-pekko-http

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
- Reactive stream-based concurrency processing and various actor models are implemented in Kotlin.
- For code learning targets, refer to *.kt and .md files
- When unit tests are needed, refer to and improve upon the methods used in test files
- When dependencies are the same as this sample code, match the same versions - using Gradle