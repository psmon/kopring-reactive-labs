# Actor Concurrency with Kotlin

이 프로젝트는 Apache Pekko(Akka의 오픈소스 포크)를 사용하여 액터 모델의 동시성 처리를 보여주는 Kotlin 기반 모듈입니다.

## 목차
- [액터 모델이란?](#액터-모델이란)
- [Tell vs Ask 패턴](#tell-vs-ask-패턴)
- [동시성 모델 비교](#동시성-모델-비교)
- [프로젝트 구조](#프로젝트-구조)
- [코드 예제](#코드-예제)
- [테스트 실행](#테스트-실행)

## 액터 모델이란?

액터 모델은 동시성 프로그래밍을 위한 수학적 모델로, 다음과 같은 특징을 가집니다:

1. **액터**: 독립적인 계산 단위로, 자체 상태와 동작을 캡슐화합니다
2. **메시지 기반 통신**: 액터들은 오직 메시지를 통해서만 통신합니다
3. **비동기성**: 메시지는 비동기적으로 전송되고 처리됩니다
4. **격리성**: 각 액터는 독립적으로 실행되며 상태를 공유하지 않습니다

### 액터의 3가지 기본 동작
- **메시지 수신**: 다른 액터로부터 메시지를 받습니다
- **상태 변경**: 내부 상태를 업데이트할 수 있습니다
- **새 액터 생성**: 필요시 자식 액터를 생성할 수 있습니다

## Tell vs Ask 패턴

### Tell 패턴 (Fire-and-Forget)
```kotlin
// 메시지를 보내고 응답을 기다리지 않음
actor.tell(Hello("Hello"))
```
- **장점**: 비동기적이고 논블로킹
- **단점**: 응답을 받을 수 없음
- **사용 시기**: 단방향 통신이나 이벤트 알림

### Ask 패턴 (Request-Response)
```kotlin
// 메시지를 보내고 응답을 기다림
val response = AskPattern.ask(
    actor,
    { replyTo -> Hello("Hello", replyTo) },
    Duration.ofSeconds(3),
    scheduler
)
```
- **장점**: 응답을 받을 수 있음
- **단점**: 타임아웃 설정 필요, 약간의 오버헤드
- **사용 시기**: 응답이 필요한 요청-응답 패턴

## 동시성 모델 비교

### 1. CompletableFuture (Java 표준)
```kotlin
val future = AskPattern.ask(...).toCompletableFuture()
val result = future.get() // 블로킹
```
- **특징**: Java 8+ 표준 비동기 API
- **장점**: 널리 사용됨, 다양한 조합 연산 지원
- **단점**: 체이닝시 가독성 떨어짐

### 2. Reactor (WebFlux)
```kotlin
val mono = AskPattern.ask(...).toCompletableFuture().toMono()
val result = mono.block() // 블로킹
```
- **특징**: 리액티브 스트림 구현체
- **장점**: 백프레셔, 풍부한 연산자
- **단점**: 학습 곡선이 있음

### 3. Kotlin Coroutines
```kotlin
suspend fun getResponse() {
    val result = AskPattern.ask(...).toCompletableFuture().await()
}
```
- **특징**: Kotlin 네이티브 비동기 프로그래밍
- **장점**: 직관적인 코드, 경량 스레드
- **단점**: Kotlin 전용

## 프로젝트 구조

```
ACTOR_CONCURRENCY/
├── build.gradle.kts           # 빌드 설정
├── src/
│   ├── main/kotlin/
│   │   └── com/example/actorconcurrency/
│   │       ├── actor/
│   │       │   ├── HelloActor.kt         # 메인 액터
│   │       │   └── TestReceiverActor.kt  # 테스트용 수신 액터
│   │       └── model/
│   │           └── Commands.kt           # 메시지 정의
│   └── test/kotlin/
│       └── com/example/actorconcurrency/
│           └── actor/
│               └── HelloActorConcurrencyTest.kt  # 테스트
└── README.md
```

## 코드 예제

### 1. 액터 정의
```kotlin
class HelloActor(
    context: ActorContext<HelloCommand>,
    private val testReceiver: ActorRef<HelloCommand>? = null
) : AbstractBehavior<HelloCommand>(context) {
    
    override fun createReceive(): Receive<HelloCommand> {
        return newReceiveBuilder()
            .onMessage(Hello::class.java, this::onHello)
            .build()
    }
    
    private fun onHello(hello: Hello): Behavior<HelloCommand> {
        val response = HelloResponse("Kotlin")
        hello.replyTo?.tell(response)
        testReceiver?.tell(response)
        return this
    }
}
```

### 2. Tell 패턴 사용
```kotlin
@Test
fun `test HelloActor with Tell pattern`() {
    val probe = testKit.createTestProbe<HelloCommand>()
    val actor = testKit.spawn(HelloActor.create(probe.ref))
    
    // 메시지 전송 (응답 기다리지 않음)
    actor.tell(Hello("Hello"))
    
    // 프로브로 응답 확인
    probe.expectMessage(HelloResponse("Kotlin"))
}
```

### 3. Ask 패턴 - 세 가지 방식

#### CompletableFuture
```kotlin
val future = AskPattern.ask(
    actor,
    { replyTo -> Hello("Hello", replyTo) },
    Duration.ofSeconds(3),
    scheduler
).toCompletableFuture()

val response = future.get()
```

#### Reactor Mono
```kotlin
val mono = AskPattern.ask(...).toCompletableFuture().toMono()
val response = mono.block()
```

#### Kotlin Coroutines
```kotlin
suspend fun askActor() {
    val response = AskPattern.ask(...).toCompletableFuture().await()
}
```

## 테스트 실행

### 테스트 실행 방법
```bash
# 모든 테스트 실행
./gradlew test

# 특정 테스트만 실행
./gradlew test --tests HelloActorConcurrencyTest
```

### 테스트 내용
1. **Tell 패턴 테스트**: 메시지 전송 후 TestProbe로 확인
2. **Ask 패턴 - CompletableFuture**: Java 표준 비동기 API 사용
3. **Ask 패턴 - WebFlux**: Reactor Mono 사용
4. **Ask 패턴 - Coroutines**: Kotlin 코루틴 사용
5. **동시 요청 테스트**: 여러 동시성 모델을 함께 사용

## 핵심 개념 정리

### 액터 모델의 장점
- **스레드 안전성**: 공유 상태가 없어 동기화 문제 없음
- **확장성**: 액터는 독립적이므로 쉽게 확장 가능
- **장애 격리**: 한 액터의 실패가 다른 액터에 영향 없음
- **위치 투명성**: 액터가 로컬이든 원격이든 동일하게 통신

### 실제 사용 사례
- **게임 서버**: 각 플레이어나 NPC를 액터로 모델링
- **IoT 시스템**: 각 디바이스를 액터로 표현
- **마이크로서비스**: 서비스 간 통신 패턴
- **실시간 처리**: 이벤트 기반 시스템

## 추가 학습 자료
- [Apache Pekko 공식 문서](https://pekko.apache.org/)
- [Kotlin Coroutines 가이드](https://kotlinlang.org/docs/coroutines-guide.html)
- [Project Reactor 문서](https://projectreactor.io/docs)

## 라이선스
이 프로젝트는 교육 목적으로 작성되었습니다.