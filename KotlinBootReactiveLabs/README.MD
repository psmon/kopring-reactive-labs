# KotlinBootReactiveLabs

This repository is for learning Reactive Stream using Spring Boot WebFlux.

## Concurrent Programming

- [ConcurrentTest](src/test/kotlin/org/example/kotlinbootreactivelabs/actor/guide/ConcurrentTest.kt)

## Reactive

![alt text](../Docs/springboot-reactive.png)

- https://spring.io/reactive

## Reactive WebSocket

Testing WebSocket using Reactive

## BaseTest

- http://localhost:8080/api-doc
- http://localhost:8080/base-chat/sse-test.html
- http://localhost:8080/base-chat/ws-rx-test.html
- http://localhost:8080/base-chat/ws-actor-test.html

## CxTest
- http://localhost:8080/cx-chat/wstest-user.html
- http://localhost:8080/cx-chat/wstest-counselor.html


## Kafka with AkkaStreams

- https://pekko.apache.org/docs/pekko-connectors-kafka/current/home.html#project-info


## ActorModel by Pekko

To handle concurrency and distributed processing, we use ReactiveStream based on WebFlux and partially adopt the actor model. Pekko (open-source Akka) actors can be used to solve complex problems.

- https://pekko.apache.org/docs/pekko/current/index.html
- https://www.baeldung.com/akka-with-spring
- https://wiki.webnori.com/display/AKKA/AKKA.Kotlin
- https://wiki.webnori.com/display/AKKA/Reactive+WebSocket+With+Kotlin