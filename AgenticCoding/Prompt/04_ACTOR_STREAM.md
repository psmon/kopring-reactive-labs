# Project Creation Guidelines

- Starting fresh in the AgenticCoding/Projects/ACTOR_STREAM folder.
- I want to create a Kotlin-based functional module only, not as an application.
- I want to write the following actors and perform execution and verification only in unit tests.

## Actor Implementation and Unit Test Implementation
- I want to write a tutorial-style actor model to understand pekko-stream.
- The sample code generates sentences with words and numbers mixed together like "abc 345 def sdf" from a stream source.
- Separate numbers and words and send them as two streams: "abc def sdf" and "345".
- In the separated streams, the word stream counts the number of sentences, and the number stream sums only the numbers.
- The final merge should have information about the original word information, sentence count, and the total sum of mentioned numbers, and output to console.
- Make good use of PekkoStream's Working With Graph characteristics to make the code concise, and also apply Buffer and Working With rate to make it a controllable stream...
- Implement versions using Java Streams API, WebFlux, and Kotlin Coroutines without PekkoStream with the same concept. The purpose is to compare which one can be implemented more concisely and powerfully.

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