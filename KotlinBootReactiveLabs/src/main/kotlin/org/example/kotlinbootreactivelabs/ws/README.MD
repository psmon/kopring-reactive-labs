# Reactive WebSocket with Actor Model

코프링에서 Reactive Weboscket을 먼저구현하고, 웹소켓핸들러를 액터모델로 연결해 풍성한 기능을 확장하는 연구프로젝트입니다.  


StepByStep Sample Code

- base : 액터종속 없는 기본버전으로~ 기본적인 pub/sub을 Class로 구현
- actor : base 버전의 pub/sub Class를 액터버전으로 확장
- messenger : 액터모델 이용한 간단한 채팅 기능구현 ( 예정 )
- messenger-cluster : messenger 버전을 클러스터로 확장 ( 예정 )

# Spring WebFlux (`org.springframework.web.reactive.socket`) vs Spring MVC 웹소켓 비교

## ✅ Spring WebFlux (Reactive WebSocket)
### 장점
- 🚀 **비동기 & 논블로킹**: 높은 동시성 처리 가능
- 🔥 **적은 리소스 소비**: 이벤트 기반 처리로 최소한의 스레드 사용
- ⚡ **성능 최적화**: 대규모 실시간 데이터 스트리밍, 채팅 등에 적합
- 🌍 **Netty 지원**: 고성능 리액티브 서버 활용 가능

### 단점
- 🧩 **개발 복잡도**: `Flux`, `Mono` 기반 코드 구조가 다소 어렵고 익숙하지 않음
- 🔄 **기존 MVC와의 호환성 낮음**: 기존 Spring MVC와 혼용이 어려움
- 🛠 **Tomcat 기본 지원 없음**: Netty 기반 서버 필요

---

## ✅ Spring MVC 웹소켓 (Traditional WebSocket)
### 장점
- 🎯 **쉽고 익숙한 개발 방식**: `@Controller`, `@MessageMapping` 사용
- 🔄 **기존 MVC 프로젝트와 호환**: 기존 Spring 애플리케이션과 쉽게 통합 가능
- ✅ **Tomcat, Jetty 등 지원**: 별도의 리액티브 서버 필요 없음

### 단점
- ⚠️ **블로킹 방식**: 스레드 기반 처리로 많은 동접자 처리 시 성능 저하
- ⏳ **리소스 사용량 증가**: 연결이 많아질수록 스레드 부담 증가
- 🏗 **확장성 낮음**: 대규모 웹소켓 서비스에는 부적합

---

## 🔥 선택 기준
| 기준 | WebFlux (Reactive) | MVC WebSocket (Traditional) |
|------|---------------------|----------------------------|
| **성능 & 동시성** | ✅ 우수 | ❌ 낮음 |
| **개발 난이도** | ❌ 어려움 | ✅ 쉬움 |
| **기존 MVC 프로젝트 연동** | ❌ 어려움 | ✅ 쉬움 |
| **서버 요구사항** | Netty, Undertow | Tomcat, Jetty |
| **적용 사례** | 실시간 데이터, 대규모 채팅 | 간단한 알림, 소규모 채팅 |

🔹 **대규모 실시간 시스템** → WebFlux  
🔹 **기존 MVC 기반 시스템** → Spring MVC 웹소켓  

# WebSocket + 액터 모델 (Actor Model) 연동 시 장점

## ✅ 1. 고성능 & 확장성 (Scalability)
- **WebSocket은 지속적인 연결**이 필요하므로 많은 클라이언트가 연결되면 **스레드 기반 처리 방식은 부담**이 커짐.
- **액터 모델은 비동기 메시징 방식**을 사용하여 **대량의 WebSocket 세션을 효율적으로 처리** 가능.
- **수평 확장(Scale-Out) 용이**: 액터는 독립적으로 동작하며, 여러 노드로 쉽게 확장 가능.

## ✅ 2. 비동기 이벤트 기반 구조 (Async & Event-driven)
- **WebSocket은 기본적으로 이벤트 기반**이며, 액터 모델과 자연스럽게 연동됨.
- **각 액터는 WebSocket 세션을 개별적으로 관리**하여, 메시지가 오면 즉시 비동기 처리 가능.
- **백프레셔(Backpressure) 제어 가능**: 액터 간 메시지 큐를 활용하여 과부하를 방지.

## ✅ 3. WebSocket 세션과 액터 매핑 (Session Management)
- **각 클라이언트 WebSocket 세션을 개별 액터로 관리 가능** (예: `UserActor`, `ChatSessionActor`).
- 클라이언트 연결이 해제되면 **자동으로 액터를 정리(Stop)하여 리소스 절약**.
- 액터 상태를 유지하여, **세션 복구 및 지속적인 연결 유지** 가능.

## ✅ 4. 장애 복구 및 상태 유지 (Fault Tolerance)
- 액터 모델의 **슈퍼바이저 전략(Supervision Strategy)** 을 활용하여 WebSocket 세션이 끊어져도 자동 복구 가능.
- **WebSocket 세션 상태를 Event Sourcing 또는 Durable State로 저장**하여 장애 시에도 클라이언트 복구 지원.

## ✅ 5. 메시지 브로커 없이 액터 간 직접 메시징 가능
- 일반적으로 **WebSocket 메시지를 Kafka, Redis Pub/Sub 등의 브로커를 통해 전달**하지만,  
  **액터 모델을 활용하면 별도의 메시지 브로커 없이 직접 메시지 전송 가능**.
- **낮은 레이턴시(Low Latency) & 높은 처리량(Throughput)** 제공.

## ✅ 6. 분산 환경에서 유리한 구조
- **Akka Cluster 또는 Pekko Cluster**를 사용하면 여러 노드에 분산된 WebSocket 액터를 쉽게 관리 가능.
- 액터가 다른 서버로 이동하더라도 WebSocket 클라이언트에게 **끊김 없는 경험 제공** 가능.

---

# 🔥 WebSocket + 액터 모델 추천 아키텍처
1. **각 클라이언트는 `UserActor`를 통해 WebSocket 연결을 관리.**
2. **메시지 라우팅을 `RoomActor`, `SessionActor`에서 처리.**
3. **분산된 클러스터 환경에서 `ShardRegion`을 사용하여 WebSocket 액터를 자동으로 로드 밸런싱.**
4. **Event Sourcing을 사용하여 WebSocket 연결이 끊어지더라도 상태 복원 가능.**

🔹 **대규모 실시간 채팅**  
🔹 **라이브 스트리밍 & 방송 시스템**  
🔹 **금융 거래 및 실시간 알림 시스템**

👉 **WebSocket + 액터 모델 조합은 높은 성능과 확장성을 보장하는 최적의 솔루션!** 🚀
