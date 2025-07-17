# SSE Push System - 프로젝트 문서

## 목차
1. [프로젝트 개요](#프로젝트-개요)
2. [기술 스택](#기술-스택)
3. [아키텍처 설계](#아키텍처-설계)
4. [주요 컴포넌트](#주요-컴포넌트)
5. [코드 컨셉 설명](#코드-컨셉-설명)
6. [API 엔드포인트](#api-엔드포인트)
7. [사용 방법](#사용-방법)
8. [테스트](#테스트)

## 프로젝트 개요

SSE Push System은 Server-Sent Events(SSE)를 활용한 실시간 이벤트 푸시 시스템입니다. 이 시스템은 토픽 기반의 pub/sub 패턴을 구현하여 클라이언트가 특정 토픽을 구독하고 실시간으로 이벤트를 수신할 수 있도록 합니다.

### 주요 기능
- **실시간 이벤트 스트리밍**: SSE를 통한 서버→클라이언트 단방향 실시간 통신
- **토픽 기반 구독**: 클라이언트는 관심있는 토픽만 선택적으로 구독
- **이벤트 히스토리**: 토픽별로 최근 100개의 이벤트를 저장하여 늦게 접속한 사용자도 이전 이벤트 수신 가능
- **Actor 모델**: Apache Pekko(구 Akka)를 사용한 동시성 처리 및 상태 관리

## 기술 스택

- **언어**: Kotlin 2.0.0
- **프레임워크**: Spring Boot 3.3.4, Spring WebFlux
- **Actor 시스템**: Apache Pekko 1.1.2 (Akka의 오픈소스 포크)
- **빌드 도구**: Gradle 8.10.2
- **테스트**: Kotest 5.9.1, WebTestClient
- **문서화**: SpringDoc OpenAPI (Swagger)

## 아키텍처 설계

### Actor 계층 구조

```
ActorSystem (SSEPushSystem)
    └── MainStageActor
        ├── TopicManagerActor
        └── UserEventActor
            └── EventReceiver (per user)
```

### 데이터 흐름

```
1. 이벤트 발행
   Client → PushController → TopicManagerActor → Topic Actor → Subscribers

2. SSE 구독
   Client → SseController → UserEventActor → EventReceiver → SSE Stream
```

## 주요 컴포넌트

### 1. Actor 시스템

#### MainStageActor
- 시스템의 루트 액터로서 다른 액터들의 생명주기 관리
- TopicManagerActor와 UserEventActor 생성 및 감독

#### TopicManagerActor
- 토픽별 이벤트 관리 및 라우팅
- 이벤트 히스토리 저장 (토픽당 최대 100개)
- 24시간 이상 된 이벤트 자동 정리
- Pekko의 Topic API를 사용한 pub/sub 구현

#### UserEventActor
- 사용자별 SSE 연결 관리
- 토픽 구독/구독해제 처리
- 연결 상태 모니터링 및 자동 정리
- 사용자별 EventReceiver 액터 생성

### 2. Controller 계층

#### SseController
- SSE 스트리밍 엔드포인트 제공
- 30초마다 keepalive 메시지 전송으로 연결 유지
- 연결 시 과거 이벤트 자동 전송

#### PushController
- 이벤트 발행 API 제공
- 단일 이벤트, 배치 이벤트, 브로드캐스트 지원

### 3. 모델

#### TopicEvent
```kotlin
data class TopicEvent(
    val id: String = UUID.randomUUID().toString(),
    val topic: String,
    val data: String,
    val timestamp: Instant = Instant.now()
) : CborSerializable
```

## 코드 컨셉 설명

### 1. Reactive Programming
Spring WebFlux와 Reactor를 사용하여 비동기 논블로킹 처리:
```kotlin
return sink.asFlux()
    .map { event -> ServerSentEvent.builder<TopicEvent>()...build() }
    .mergeWith(keepAliveFlux)
```

### 2. Actor Model
동시성 문제를 Actor 모델로 해결:
```kotlin
Behaviors.receiveMessage { message ->
    when (message) {
        is Connect -> handleConnect(message)
        is Disconnect -> handleDisconnect(message)
        // ...
    }
}
```

### 3. Ask Pattern
Actor와의 동기적 통신이 필요한 경우 Ask 패턴 사용:
```kotlin
val connected = AskPattern.ask(
    userEventActor,
    { replyTo -> UserEventActor.Connect(userId, sink, replyTo) },
    Duration.ofSeconds(5),
    actorSystem.scheduler()
).await()
```

### 4. 이벤트 히스토리 관리
ConcurrentHashMap과 순환 버퍼 개념으로 효율적인 메모리 관리:
```kotlin
val history = eventHistory.computeIfAbsent(topicName) { mutableListOf() }
history.add(event)
if (history.size > MAX_EVENTS_PER_TOPIC) {
    history.removeAt(0)
}
```

### 5. 연결 재사용 문제 해결
동일 사용자의 재연결 시 기존 연결 정리:
```kotlin
connections[message.userId]?.let { existingConnection ->
    // 기존 연결 정리
    existingConnection.sink.tryEmitComplete()
    context.stop(existingConnection.eventReceiver)
}
// 새 연결 생성 (timestamp로 유니크한 actor 이름 보장)
val eventReceiver = context.spawn(
    createEventReceiver(userId),
    "event-receiver-${userId}-${System.currentTimeMillis()}"
)
```

## API 엔드포인트

### SSE 스트리밍
```
GET /api/sse/stream?userId={userId}&topics={topic1,topic2}
```

### 이벤트 발행
```
POST /api/push/event
Body: { "topic": "news", "data": "Breaking news!" }

POST /api/push/events/batch
Body: [{"topic": "news", "data": "Event 1"}, ...]

POST /api/push/broadcast?topics=news,updates&data=Broadcast message
```

### 토픽 관리
```
POST /api/sse/subscribe?userId={userId}&topic={topic}
POST /api/sse/unsubscribe?userId={userId}&topic={topic}
GET /api/sse/history/{topic}
```

## 사용 방법

### 1. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 2. Swagger UI 접속
```
http://localhost:8080/swagger-ui/index.html
```

### 3. 테스트 클라이언트 사용
- 전체 기능 테스트: `http://localhost:8080/index.html`
- 간단한 테스트: `http://localhost:8080/test.html`

### 4. 프로그래밍 방식 사용
```javascript
// SSE 연결
const eventSource = new EventSource('/api/sse/stream?userId=user1&topics=news,updates');

eventSource.addEventListener('news', (event) => {
    const data = JSON.parse(event.data);
    console.log('News event:', data);
});

// 이벤트 발행
fetch('/api/push/event', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topic: 'news', data: 'Breaking news!' })
});
```

## 테스트

### 단위 테스트 실행
```bash
./gradlew test
```

### 테스트 커버리지
- `TopicManagerActorTest`: 이벤트 발행, 구독, 히스토리 관리
- `UserEventActorTest`: 사용자 연결, 구독 관리
- `SseIntegrationTest`: End-to-End 통합 테스트

### 주요 테스트 시나리오
1. 사용자별 선택적 토픽 구독
2. 늦게 접속한 사용자의 히스토리 수신
3. 동시 다중 사용자 처리
4. 재연결 시 기존 연결 정리

## 성능 고려사항

1. **메모리 관리**: 토픽당 최대 100개 이벤트만 유지
2. **연결 관리**: 유휴 연결 자동 감지 및 정리
3. **동시성**: Actor 모델로 thread-safe 보장
4. **확장성**: 토픽별 독립적인 Actor로 수평 확장 가능

## 향후 개선 사항

1. **클러스터링**: Pekko Cluster를 사용한 분산 시스템 구성
2. **영속성**: 이벤트 히스토리의 데이터베이스 저장
3. **보안**: JWT 기반 인증/인가
4. **모니터링**: 메트릭 수집 및 대시보드
5. **메시지 필터링**: 이벤트 내용 기반 필터링