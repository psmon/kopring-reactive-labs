# CONNECTOR_KAFKA - Kafka Connector with Actor Model

## 개요

이 프로젝트는 Apache Kafka와 Pekko Actor 모델을 결합한 이벤트 처리 시스템입니다. Kafka 프로듀서/컨슈머와 Actor 기반 상태 관리를 통해 안정적이고 확장 가능한 메시지 처리를 구현합니다.

## 주요 기능

- ✅ Kafka 프로듀서를 통한 이벤트 전송
- ✅ Kafka 컨슈머와 Actor 모델 통합
- ✅ 이벤트 상태 관리 (마지막 이벤트 저장)
- ✅ 이벤트 카운트 추적
- ✅ 배치 처리 지원
- ✅ 메타데이터를 포함한 이벤트 전송
- ✅ 높은 처리량 (TPS) 지원

## 프로젝트 구조

```
CONNECTOR_KAFKA/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/example/connectorkafka/
│   │   │       ├── model/          # 이벤트 모델 및 직렬화
│   │   │       ├── producer/       # Kafka 프로듀서
│   │   │       ├── actor/          # 이벤트 처리 Actor
│   │   │       └── connector/      # Kafka-Actor 연결
│   │   └── resources/
│   │       ├── application.conf    # Pekko/Kafka 설정
│   │       └── logback.xml        # 로깅 설정
│   └── test/
│       └── kotlin/                 # 단위 및 통합 테스트
├── docker-compose.yml              # Kafka 클러스터 설정
└── build.gradle.kts               # Gradle 빌드 설정
```

## 핵심 구성 요소

### 1. KafkaEvent 모델
```kotlin
data class KafkaEvent(
    val eventType: String,
    val eventId: String,
    val eventString: String,
    val timestamp: Long = System.currentTimeMillis()
)
```
이벤트의 기본 구조를 정의합니다. Jackson 직렬화를 사용하여 Kafka 메시지로 변환됩니다.

### 2. EventProducer
Kafka로 이벤트를 전송하는 프로듀서입니다:
- 단일 이벤트 전송
- 배치 이벤트 전송
- 메타데이터 포함 전송
- 전송된 이벤트 카운트 추적

### 3. EventConsumerActor
이벤트를 처리하고 상태를 관리하는 Actor입니다:
- 이벤트 수신 및 처리
- 마지막 이벤트 상태 유지
- 처리된 이벤트 카운트 관리
- 상태 초기화 기능

### 4. KafkaConsumerConnector
Kafka 컨슈머와 Actor를 연결하는 커넥터입니다:
- Kafka 토픽에서 메시지 수신
- Actor로 이벤트 전달
- 오프셋 커밋 관리
- Kill switch를 통한 우아한 종료

## 시작하기

### 필수 요구사항

- JDK 17 이상
- Docker 및 Docker Compose
- Gradle 7.x 이상

### 1. Kafka 클러스터 시작

```bash
# Docker Compose로 Kafka 클러스터 시작
docker-compose up -d

# 클러스터 상태 확인
docker-compose ps

# Kafka UI 접속 (브라우저)
# http://localhost:8080
```

### 2. 프로젝트 빌드

```bash
# 프로젝트 디렉토리로 이동
cd AgenticCoding/Projects/CONNECTOR_KAFKA

# Gradle 빌드
./gradlew build
```

### 3. 테스트 실행

```bash
# 모든 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests EventProducerTest
./gradlew test --tests EventConsumerActorTest
./gradlew test --tests KafkaIntegrationTest
```

## 사용 예제

### 프로듀서 사용 예제

```kotlin
// Actor 시스템 생성
val system = ActorTestKit.create("MySystem", config)

// 프로듀서 초기화
val producer = EventProducer(system.system(), "test-topic1")

// 단일 이벤트 전송
val event = KafkaEvent(
    eventType = "USER_ACTION",
    eventId = UUID.randomUUID().toString(),
    eventString = "User clicked button"
)

producer.sendEvent(event).thenAccept { done ->
    println("Event sent successfully")
}

// 배치 이벤트 전송
val events = (1..100).map { i ->
    KafkaEvent(
        eventType = "BATCH_EVENT",
        eventId = "batch-$i",
        eventString = "Batch event $i"
    )
}

producer.sendEvents(events).thenAccept { done ->
    println("Batch sent: ${producer.getProducedCount()} events")
}
```

### 컨슈머 사용 예제

```kotlin
// 컨슈머 커넥터 초기화
val consumer = KafkaConsumerConnector(
    system.system(),
    topicName = "test-topic1",
    groupId = "my-consumer-group"
)

// 컨슈밍 시작
val control = consumer.startConsuming()

// Actor를 통한 상태 조회
val actor = consumer.getConsumerActor()
val probe = system.createTestProbe<EventResponse>()

// 마지막 이벤트 조회
actor.tell(GetLastEvent(probe.ref))
val lastEvent = probe.expectMessageClass(LastEventResponse::class.java)
println("Last event: ${lastEvent.event?.eventString}")

// 이벤트 카운트 조회
val countProbe = system.createTestProbe<EventCountResponse>()
actor.tell(GetEventCount(countProbe.ref))
val count = probe.expectMessageClass(EventCountResponse::class.java)
println("Total events processed: ${count.count}")
```

### Kill Switch를 사용한 우아한 종료

```kotlin
// Kill switch와 함께 시작
val (killSwitch, future) = consumer.startConsumingWithKillSwitch()

// 처리 로직...

// 우아한 종료
killSwitch.shutdown()
future.toCompletableFuture().get(10, TimeUnit.SECONDS)
```

## 설정 가이드

### application.conf 주요 설정

```hocon
kafka {
  bootstrap.servers = "localhost:9092,localhost:9093,localhost:9094"
}

pekko.kafka {
  producer {
    kafka-clients {
      acks = "all"              # 모든 복제본 확인
      retries = 3               # 재시도 횟수
      compression.type = "gzip" # 압축 타입
    }
  }
  
  consumer {
    kafka-clients {
      auto.offset.reset = "earliest"  # 오프셋 리셋 정책
      enable.auto.commit = false       # 수동 커밋
      max.poll.records = 500          # 최대 폴링 레코드 수
    }
  }
}
```

## 성능 및 테스트 결과

### 처리량 (TPS) 테스트
- **프로듀서 TPS**: 평균 1000+ events/sec
- **End-to-End TPS**: 평균 500+ events/sec
- **테스트 환경**: 로컬 Docker Kafka 클러스터 (3 브로커)

### 신뢰성 테스트
- ✅ 이벤트 순서 보장
- ✅ 정확한 이벤트 카운트 매칭
- ✅ 상태 일관성 유지
- ✅ 동시성 처리 검증

## 아키텍처 개념

### Actor 모델의 장점
1. **격리된 상태 관리**: 각 Actor가 독립적으로 상태 관리
2. **메시지 기반 통신**: 비동기 메시지 전달로 느슨한 결합
3. **오류 격리**: Actor 장애가 시스템 전체에 영향 없음
4. **확장성**: Actor 인스턴스를 늘려 쉽게 확장

### Kafka와 Actor 통합의 이점
1. **백프레셔 제어**: Pekko Streams로 자동 백프레셔 관리
2. **정확한 한 번 처리**: 오프셋 커밋과 Actor 상태 동기화
3. **복원력**: 장애 시 자동 재연결 및 복구
4. **모니터링**: Actor 시스템을 통한 세밀한 모니터링

## 트러블슈팅

### Kafka 연결 실패
```bash
# Kafka 클러스터 상태 확인
docker-compose ps

# Kafka 로그 확인
docker-compose logs kafka-1

# 토픽 목록 확인
docker exec -it kafka-broker-1 kafka-topics --list --bootstrap-server localhost:9092
```

### 테스트 실패
```bash
# 상세 로그와 함께 테스트 실행
./gradlew test --info --stacktrace

# 특정 테스트만 디버그 모드로 실행
./gradlew test --tests KafkaIntegrationTest --debug
```

### 성능 이슈
- `max.poll.records` 조정으로 배치 크기 최적화
- `parallelism` 설정으로 동시 처리 수준 조정
- JVM 힙 메모리 증가: `-Xmx2g -Xms1g`

## 참고 자료

- [Pekko Kafka Connector Documentation](https://pekko.apache.org/docs/pekko-connectors-kafka/current/)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Pekko Actor Documentation](https://pekko.apache.org/docs/pekko/current/typed/index.html)
- [Testcontainers Kafka Module](https://www.testcontainers.org/modules/kafka/)

## 라이선스

이 프로젝트는 교육 목적으로 작성되었습니다.