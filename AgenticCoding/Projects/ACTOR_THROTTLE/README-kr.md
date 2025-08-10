# ACTOR_THROTTLE - Actor Model Based TPS Throttling Tutorial

## 개요

ACTOR_THROTTLE 프로젝트는 Apache Pekko (Akka fork) 액터 모델을 사용하여 TPS(Transactions Per Second) 제한을 구현하는 튜토리얼입니다. 이 프로젝트는 `mallID` 단위로 작업을 관리하며, 각 mall별로 독립적인 TPS 제한을 적용합니다.

## 핵심 개념

### 1. Throttling 메커니즘

이 프로젝트의 핵심은 Pekko Streams의 `throttle` 연산자를 사용하여 스레드 블로킹 없이 TPS를 제어하는 것입니다:

```kotlin
Source.queue<ThrottledWork>(100, OverflowStrategy.backpressure())
    .throttle(tpsLimit, Duration.ofSeconds(1))
    .to(Sink.foreach { throttledWork ->
        processWork(throttledWork.work)
    })
    .run(materializer)
```

이 접근 방식의 장점:
- **논블로킹**: Thread.sleep() 없이 비동기적으로 처리
- **백프레셔**: 큐가 가득 차면 자동으로 처리 속도 조절
- **정확한 TPS 제어**: 설정된 TPS를 정확하게 준수

### 2. 액터 구조

#### ThrottleActor
- 개별 mall의 작업을 처리하는 액터
- mall별로 독립적인 TPS 제한 적용
- 작업 큐와 처리 통계 관리

#### ThrottleManagerActor
- 여러 mall의 ThrottleActor를 관리
- mall별 액터 생성 및 라우팅
- 통합 통계 수집

#### StatsCollectorActor
- 여러 mall의 통계를 비동기적으로 수집
- 일시적으로 생성되어 작업 완료 후 종료

## 주요 기능

### 1. Mall별 독립적인 TPS 제어

각 mall은 독립적인 ThrottleActor를 가지며, 서로 영향을 주지 않습니다:

```kotlin
// mall1은 TPS 1로 제한
// mall2도 독립적으로 TPS 1로 제한
managerActor.tell(ProcessMallWork("mall1", "work1", replyTo))
managerActor.tell(ProcessMallWork("mall2", "work2", replyTo))
```

### 2. 실시간 통계 조회

작업 처리 중에도 실시간으로 통계를 조회할 수 있습니다:

```kotlin
// 특정 mall 통계
managerActor.tell(GetMallStats("mall1", statsProbe.ref))

// 모든 mall 통계
managerActor.tell(GetAllStats(allStatsProbe.ref))
```

### 3. FIFO 작업 처리

작업은 요청된 순서대로(FIFO) 처리되며, TPS 제한에 따라 순차적으로 실행됩니다.

## 사용 예제

### 기본 사용법

```kotlin
// ThrottleManagerActor 생성
val managerActor = actorSystem.spawn(ThrottleManagerActor.create(), "throttle-manager")

// 작업 요청
val resultProbe = testKit.createTestProbe<WorkResult>()
managerActor.tell(ProcessMallWork("mall1", "work-123", resultProbe.ref))

// 결과 수신
val result = resultProbe.receiveMessage()
println("Work ${result.workId} processed at ${result.timestamp}")
```

### 통계 조회

```kotlin
// 특정 mall 통계 조회
val statsProbe = testKit.createTestProbe<ThrottleStats>()
managerActor.tell(GetMallStats("mall1", statsProbe.ref))
val stats = statsProbe.receiveMessage()

println("Mall: ${stats.mallId}")
println("Processed: ${stats.processedCount}")
println("Queued: ${stats.queuedCount}")
println("TPS: ${stats.tps}")
```

## 테스트 실행

```bash
./gradlew test
```

## 주요 테스트 케이스

### 1. TPS 제한 검증
- 5개의 작업을 TPS=1로 처리하면 최소 4초 소요

### 2. Mall별 독립성 검증
- 여러 mall이 동시에 작업을 처리해도 각각 독립적인 TPS 유지

### 3. 통계 정확성 검증
- 처리된 작업 수, 대기 중인 작업 수, 실제 TPS 확인

## 학습 포인트

1. **액터 모델의 동시성 처리**: 각 mall별로 독립적인 액터를 생성하여 동시성 처리
2. **Reactive Streams 활용**: Pekko Streams를 사용한 비동기 처리
3. **백프레셔 처리**: OverflowStrategy.backpressure()를 통한 자동 흐름 제어
4. **테스트 가능한 설계**: TestProbe를 활용한 액터 동작 검증

## 확장 가능성

- **동적 TPS 조정**: 런타임에 TPS 제한 변경
- **우선순위 큐**: 작업별 우선순위 부여
- **Circuit Breaker**: 장애 시 자동 차단
- **메트릭 수집**: Prometheus 등과 연동하여 모니터링

이 프로젝트는 액터 모델을 사용한 TPS 제어의 기본 개념을 보여주며, 실제 프로덕션 환경에서 확장하여 사용할 수 있는 패턴을 제공합니다.