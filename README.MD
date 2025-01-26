# Kopring Reactive Labs

![](./Docs/springboot-reactive.png)

코틀린+SpringBoot 를 활용해 Reactive Stack을 연구하는 공간으로, 다양하고 유용한 액터모델 발굴도 연구중에 있습니다.

## 중요 학습키워드

### Reactive Streams

Java Reactive Streams는 비동기 스트림 처리와 데이터 흐름 관리를 표준화하기 위한 API로, 대규모 데이터 처리와 고성능 애플리케이션 개발에 적합합니다. 핵심 개념인 백프레셔(backpressure)는 생산자와 소비자 간 데이터 처리 속도를 동기화하여 리소스 과부하를 방지하며 안정성을 높입니다. 이를 통해 비차단(non-blocking) 방식으로 효율적인 데이터 스트림 처리가 가능하며, I/O 집약적인 작업에서 높은 성능을 발휘합니다. 특히 대규모 분산 시스템, 리액티브 애플리케이션, 실시간 데이터 처리와 같은 다양한 환경에서 활용됩니다. Java 9에서는 Flow API로 통합되어 더 많은 개발자들이 Reactive Streams의 장점을 누릴 수 있게 되었으며, 프로젝트 리액터(Reactor), RxJava, Akka Streams 등 주요 구현체들과 함께 리액티브 프로그래밍의 기반을 제공합니다.


### R2DBC

R2DBC(Reactive Relational Database Connectivity)는 리액티브 프로그래밍 환경에서 관계형 데이터베이스와 비차단 방식으로 상호작용할 수 있도록 설계된 API입니다. 주요 장점으로는 비동기 처리를 통해 블로킹 없는 고성능 데이터베이스 접근이 가능하며, 대규모 동시성을 처리하는 애플리케이션에 적합합니다. 백프레셔 지원으로 데이터 흐름을 제어하여 안정성을 높이고, Netty와 같은 리액티브 네트워크 프로그래밍 프레임워크와 자연스럽게 통합됩니다. 또한, Spring WebFlux와 같은 리액티브 스택과의 호환성이 우수해, 최신 리액티브 애플리케이션 개발에 적합합니다. R2DBC는 기존 JDBC의 한계를 극복하며, 리소스 효율성과 성능 최적화를 동시에 제공합니다.


### WebFlux

Spring WebFlux는 비동기, 논블로킹 리액티브 프로그래밍을 지원하는 웹 프레임워크로, 높은 동시성 처리와 효율적인 리소스 사용이 주요 장점입니다. Netty, Undertow와 같은 리액티브 런타임 위에서 작동하며, 블로킹 없는 요청/응답 모델을 제공하여 성능을 극대화합니다. Reactor 기반으로 리액티브 스트림 표준을 지원하며, 데이터베이스(R2DBC), 메시징, 외부 API와의 리액티브 통합이 용이합니다. 경량 설계로 적은 리소스에서도 대규모 트래픽을 처리할 수 있으며, RESTful API, SSE(Server-Sent Events), WebSocket 등 다양한 프로토콜을 지원합니다. 이를 통해 마이크로서비스, 실시간 애플리케이션에 적합한 선택이 됩니다.

### ActorModel


액터 모델의 장점은 동시성과 분산 시스템 구현에서의 강력한 설계 방식에 있습니다. 메시지 기반 통신으로 액터 간 상태 공유를 배제하여 데이터 경합과 데드락 문제를 방지합니다. 비동기 처리를 통해 높은 성능과 효율성을 제공하며, 비차단 방식으로 동작하여 리소스 활용도를 극대화합니다. 액터는 독립적으로 동작하고 장애 시 슈퍼바이저 전략으로 자동 복구가 가능해 높은 안정성을 보장합니다. 수평적 확장성으로 대규모 분산 시스템에서 탁월한 성능을 발휘하며, 복잡한 병렬 작업을 단순화합니다. 또한, 이벤트 소싱, 스트림 처리 등과 쉽게 통합되며, 실시간 애플리케이션, IoT, 마이크로서비스 등 다양한 도메인에 활용됩니다.

### CQRS
AKKA가 제공하는 다음 CQRS(Command Query Responsibility Segregation) 장치를 통해
CQRS를 학습할수 있습니다.

1. Event Sourcing
   Akka Persistence는 이벤트 소싱(Event Sourcing)을 지원하여 액터 상태 변경을 이벤트의 형태로 저장합니다.
   이벤트는 저널(Journal)에 순차적으로 저장되며, 이를 통해 시스템의 모든 상태 변경 이력을 유지합니다.
   이벤트 재생(replay)을 통해 액터를 특정 시점의 상태로 복원할 수 있습니다.
2. Snapshot
   이벤트의 재생 속도를 높이기 위해 Akka Persistence는 스냅샷(Snapshot) 기능을 제공합니다.
   스냅샷은 액터의 전체 상태를 특정 시점에 저장하며, 복구 시 스냅샷 이후의 이벤트만 재생하면 됩니다.
   읽기 모델을 최적화하기 위해 스냅샷 데이터를 활용할 수 있습니다.
3. Durable State
   Durable State Store는 이벤트가 아닌 현재 상태를 저장합니다.
   CQRS의 Query 측면에서 데이터의 최신 상태를 빠르게 제공하며, 이벤트 히스토리가 필요하지 않은 경우 적합합니다.
   Akka Projections와 함께 활용하여 읽기 모델을 생성하거나 유지할 수 있습니다.
4. Akka Projections
   Akka Projections는 이벤트 로그나 지속 상태를 기반으로 CQRS의 Query를 구현하는 데 도움을 줍니다.
   이벤트를 읽어 별도의 읽기 데이터베이스 또는 캐시에 반영하여 최적화된 조회 성능을 제공합니다.
   R2DBC, JDBC, Kafka, Slick 등 다양한 데이터 소스를 지원합니다.
5. Tagging 및 Query 분리
   이벤트에 태그를 추가하여 특정 이벤트만 필터링하거나 그룹화할 수 있습니다.
   이를 통해 쓰기 모델과 별도의 읽기 모델을 설계하여 CQRS 구조를 구현합니다.

## Docs
- [Actor with Kotlin](https://wiki.webnori.com/display/AKKA/AKKA.Kotlin)
- [Test SampleCode](KotlinBootReactiveLabs/src/test/kotlin/org/example/kotlinbootreactivelabs/actor/readme.md)
- [AkkaDoc](https://doc.akka.io/libraries/akka-core/2.6/index.html)