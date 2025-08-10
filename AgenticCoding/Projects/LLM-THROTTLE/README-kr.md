# LLM Throttle System

LLM API 호출을 위한 토큰 기반 속도 제한(Rate Limiting) 시스템입니다. Apache Pekko Actor를 사용하여 구현되었으며, 백프레셔(Backpressure) 메커니즘을 통해 안정적인 API 사용량 관리를 제공합니다.

## 두 가지 구현 버전

### 1. LLMThrottleActor (기본 버전)
- 전통적인 액터 기반 접근 방식
- 수동 백프레셔 및 지연 처리
- 간단하고 직관적인 구조

### 2. LLMStreamThrottleActor (향상된 버전)
- **Pekko Streams** 기반 자동 속도 조절
- 내장 throttle 메커니즘 활용
- 비동기 스트림 처리 최적화

## 주요 기능

- **토큰 기반 속도 제한**: 분당 토큰 사용량 제한 (기본값: 10,000 토큰/분)
- **점진적 백프레셔**: 용량에 따른 단계적 지연 처리
- **실패 요청 관리**: 제한 초과 요청의 재시도 큐 관리
- **슬라이딩 윈도우**: 60초 슬라이딩 윈도우를 통한 정확한 사용량 추적
- **자동 속도 조절**: (StreamThrottleActor) 토큰 사용량에 따른 동적 처리율 조절

## 아키텍처

### 핵심 컴포넌트

#### 1. LLMThrottleActor (기본 버전)
메인 액터로, 다음과 같은 책임을 가집니다:
- 토큰 사용량 추적 및 관리
- 용량 기반 요청 처리 결정
- 백프레셔 적용
- 실패한 요청 관리

#### 2. LLMStreamThrottleActor (향상된 버전)
Pekko Streams를 활용한 고성능 액터로, 다음과 같은 특징을 제공합니다:
- **자동 속도 조절**: 토큰 사용량에 따른 동적 처리율 조절
- **스트림 기반 처리**: 비동기 스트림으로 높은 처리량 제공
- **내장 백프레셔**: Pekko Streams의 내장 백프레셔 메커니즘 활용
- **큐 관리**: 자동 오버플로우 처리 및 버퍼링

#### 3. TokenCalculator
텍스트를 토큰으로 변환하는 인터페이스입니다:
```kotlin
interface TokenCalculator {
    fun calculateTokens(text: String): Int
}
```

MockTokenCalculator는 `문자 수 + 랜덤(0~500)` 공식을 사용합니다.

#### 4. 명령/응답 모델
- `ProcessLLMRequest`: LLM 처리 요청
- `LLMResponse`: 성공적인 처리 응답
- `LLMThrottled`: 지연 처리 응답
- `LLMFailed`: 실패한 요청 응답

### 백프레셔 전략

#### 기본 버전 (LLMThrottleActor)
시스템은 현재 용량에 따라 점진적으로 지연을 적용합니다:

| 용량 사용률 | 지연 시간 | 동작 |
|------------|----------|------|
| < 70% | 없음 | 즉시 처리 |
| 70-80% | 100ms | 약간의 지연 |
| 80-90% | 300ms | 중간 지연 |
| 90-95% | 1,000ms | 높은 지연 |
| > 95% | 2,000ms | 최대 지연 |
| > 100% | - | 실패 큐로 이동 |

#### 향상된 버전 (LLMStreamThrottleActor)
Pekko Streams의 동적 스로틀링을 활용합니다:

| 용량 사용률 | 처리율 (req/sec) | 동작 |
|------------|----------------|------|
| < 70% | 10 | 최대 처리율 |
| 70-80% | 8 | 약간 감소 |
| 80-90% | 5 | 중간 감소 |
| 90-95% | 3 | 큰 감소 |
| > 95% | 1 | 최소 처리율 |
| > 100% | - | 실패 큐로 이동 |

## 사용 방법

### 기본 사용

#### 1. 기본 버전 (LLMThrottleActor)

```kotlin
// Actor 시스템 생성
val system = ActorSystem.create<MainStageActorCommand>(
    MainStageActor.create(), "llm-system"
)

// LLMThrottleActor 생성
val throttleActor = system.systemActorOf(
    LLMThrottleActor.create(
        tokenCalculator = MockTokenCalculator(),
        tokenLimitPerMinute = 10_000
    ),
    "llm-throttle"
)
```

#### 2. 향상된 버전 (LLMStreamThrottleActor)

```kotlin
// Actor 시스템 생성
val system = ActorSystem.create<MainStageActorCommand>(
    MainStageActor.create(), "llm-system"
)

// LLMStreamThrottleActor 생성 (Pekko Streams 기반)
val streamThrottleActor = system.systemActorOf(
    LLMStreamThrottleActor.create(
        tokenCalculator = MockTokenCalculator(),
        tokenLimitPerMinute = 10_000
    ),
    "llm-stream-throttle"
)

```

#### 요청 처리 (두 버전 모두 동일)

```kotlin
// 요청 처리
val response = AskPattern.ask(
    throttleActor, // 또는 streamThrottleActor
    { replyTo -> ProcessLLMRequest(
        requestId = UUID.randomUUID().toString(),
        content = "Process this text",
        replyTo = replyTo
    ) },
    Duration.ofSeconds(10),
    system.scheduler()
).toCompletableFuture().get()

// 응답 처리
when (response) {
    is LLMResponse -> {
        println("처리 완료: ${response.processedContent}")
        println("사용 토큰: ${response.tokensUsed}")
    }
    is LLMThrottled -> {
        println("지연 처리: ${response.delayMs}ms 대기")
        println("현재 용량: ${response.currentCapacityPercent}%")
    }
    is LLMFailed -> {
        println("처리 실패: ${response.reason}")
        if (response.canRetry) {
            println("재시도 가능")
        }
    }
}
```

### 상태 확인

```kotlin
// 현재 스로틀 상태 조회
val state = AskPattern.ask(
    throttleActor,
    { replyTo -> GetThrottleState(replyTo) },
    Duration.ofSeconds(5),
    system.scheduler()
).toCompletableFuture().get() as ThrottleState

println("현재 토큰 사용량: ${state.currentTokensUsed}/${state.tokenLimit}")
println("용량 사용률: ${state.capacityPercent}%")
println("실패한 요청 수: ${state.failedRequestsCount}")
```

### 실패한 요청 관리

```kotlin
// 실패한 요청 목록 조회
val failedList = AskPattern.ask(
    throttleActor,
    { replyTo -> GetFailedRequests(replyTo) },
    Duration.ofSeconds(5),
    system.scheduler()
).toCompletableFuture().get() as FailedRequestsList

failedList.requests.forEach { request ->
    println("실패 요청 ID: ${request.requestId}")
    println("실패 시간: ${Date(request.failedAt)}")
    println("실패 이유: ${request.reason}")
}

// 실패한 요청 재시도
throttleActor.tell(RetryFailedRequests)
```

## 설정

### 커스텀 토큰 계산기

실제 LLM API의 토큰 계산 방식에 맞춰 TokenCalculator를 구현할 수 있습니다:

```kotlin
class OpenAITokenCalculator : TokenCalculator {
    override fun calculateTokens(text: String): Int {
        // OpenAI의 tiktoken 라이브러리 로직
        return text.length / 4 // 간단한 근사치
    }
}
```

### 토큰 제한 조정

```kotlin
val throttleActor = LLMThrottleActor.create(
    tokenCalculator = CustomTokenCalculator(),
    tokenLimitPerMinute = 50_000 // 분당 50,000 토큰
)
```

## 내부 동작

### 슬라이딩 윈도우

두 버전 모두 60초 슬라이딩 윈도우를 사용하여 토큰 사용량을 추적합니다:

1. 각 요청은 타임스탬프와 함께 토큰 윈도우에 저장됩니다
2. 현재 시간 기준 60초 이내의 모든 토큰이 계산됩니다
3. 5초마다 만료된 윈도우가 자동으로 정리됩니다

### 상태 관리

#### 기본 버전 (LLMThrottleActor)
액터는 다음 상태를 관리합니다:
- `tokenWindows`: 시간별 토큰 사용량 추적
- `failedRequests`: 처리 실패한 요청 큐
- `stashedRequests`: 지연 처리 대기 중인 요청

#### 향상된 버전 (LLMStreamThrottleActor)
스트림 기반 상태 관리:
- `tokenWindows`: ConcurrentHashMap으로 thread-safe 관리
- `failedRequests`: ConcurrentLinkedQueue로 비동기 처리
- `requestQueue`: Pekko Streams SourceQueue로 버퍼링

### 동시성 처리

#### 기본 버전
- Pekko Actor 모델의 단일 스레드 처리
- 메시지 기반 통신으로 race condition 방지
- 수동 백프레셔 메커니즘

#### 향상된 버전
- Pekko Streams의 비동기 처리
- 내장 백프레셔 및 overflow 전략
- 동적 스로틀링으로 자동 부하 조절
- ConcurrentHashMap을 통한 thread-safe 토큰 관리

## 테스트

프로젝트는 각 구현에 대해 다음과 같은 테스트 시나리오를 포함합니다:

### 기본 버전 (LLMThrottleActorTest)
1. **기본 처리 테스트**: 70% 미만 용량에서 즉시 처리
2. **백프레셔 테스트**: 용량별 지연 시간 검증
3. **용량 초과 테스트**: 실패 큐 동작 검증
4. **슬라이딩 윈도우 테스트**: 시간 경과에 따른 토큰 만료
5. **동시성 테스트**: 다중 요청 처리

### 향상된 버전 (LLMStreamThrottleActorTest)
1. **스트림 처리 테스트**: 저용량에서 스트림 기반 처리
2. **동적 스로틀링 테스트**: 용량에 따른 자동 속도 조절
3. **큐 오버플로우 테스트**: 큐 용량 초과 시 graceful handling
4. **용량 초과 테스트**: 실패 큐 동작 검증
5. **재시도 테스트**: 실패한 요청 재처리
6. **슬라이딩 윈도우 테스트**: 시간 경과에 따른 토큰 만료

테스트 실행:
```bash
./gradlew test
```

## 성능 고려사항

### 기본 버전 (LLMThrottleActor)
1. **메모리 사용**: 토큰 윈도우는 메모리에 저장되므로, 장시간 운영 시 정기적인 정리가 중요합니다
2. **타이머 정확도**: 5초 간격의 정리 타이머는 시스템 부하에 따라 지연될 수 있습니다
3. **처리량**: 액터는 단일 스레드에서 실행되므로, 매우 높은 처리량이 필요한 경우 액터 라우팅 고려

### 향상된 버전 (LLMStreamThrottleActor)
1. **처리량**: Pekko Streams의 비동기 처리로 높은 처리량 제공
2. **메모리 효율성**: ConcurrentHashMap 및 스트림 버퍼링으로 메모리 사용 최적화
3. **백프레셔 성능**: 내장 백프레셔 메커니즘으로 시스템 안정성 향상
4. **동적 조절**: 실시간 부하에 따른 자동 속도 조절로 최적 성능 유지

### 비교 및 선택 가이드

| 특성 | 기본 버전 | 향상된 버전 |
|------|----------|------------|
| 구현 복잡도 | 낮음 | 높음 |
| 처리량 | 중간 | 높음 |
| 메모리 사용 | 보통 | 효율적 |
| 백프레셔 | 수동 | 자동 |
| 디버깅 용이성 | 높음 | 중간 |
| 운영 안정성 | 좋음 | 매우 좋음 |

**권장 사용 시나리오:**
- **기본 버전**: 간단한 요구사항, 낮은 처리량, 디버깅 중요
- **향상된 버전**: 높은 처리량, 복잡한 부하 패턴, 운영 안정성 중요

## 향후 개선 사항

1. **지속성**: 재시작 시에도 토큰 사용량을 유지하기 위한 영속성 추가
2. **분산 처리**: 클러스터 환경에서의 토큰 사용량 공유
3. **동적 임계값**: 부하에 따른 동적 임계값 조정
4. **메트릭 수집**: Prometheus/Grafana 연동을 위한 메트릭 노출
5. **웹훅 지원**: 실패한 요청에 대한 웹훅 알림

## 라이선스

이 프로젝트는 교육 및 참고 목적으로 작성되었습니다.