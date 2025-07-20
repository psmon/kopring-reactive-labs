# ACTOR_STREAM - 스트림 처리 비교 튜토리얼

이 프로젝트는 Pekko Streams, Java Streams API, WebFlux, Kotlin Coroutines를 사용하여 동일한 스트림 처리 작업을 구현하고 비교하는 튜토리얼입니다.

## 프로젝트 목표

문자열에서 단어와 숫자를 분리하여 처리하는 스트림 파이프라인을 구현합니다:
- 입력: "abc 345 def sdf"
- 출력: 단어 리스트 ["abc", "def", "sdf"], 단어 개수 3, 숫자 합계 345

## 주요 개념

### 1. Pekko Streams (Actor 기반)

Pekko Streams는 액터 모델 위에 구축된 리액티브 스트림 구현입니다.

#### 핵심 특징:
- **Graph DSL**: 복잡한 스트림 처리를 시각적으로 표현
- **Backpressure**: 자동 흐름 제어
- **액터 통합**: 액터 시스템과 자연스러운 통합

#### 구현 예시:
```kotlin
val graph = RunnableGraph.fromGraph(
    GraphDSL.create { builder ->
        // 소스 정의
        val source = builder.add(Source.single(text))
        
        // 단어/숫자 분리
        val splitter = builder.add(Flow.of(String::class.java)
            .map { /* 분리 로직 */ })
        
        // 병렬 처리를 위한 브로드캐스트
        val broadcast = builder.add(Broadcast.create<Pair<List<String>, List<Int>>>(2))
        
        // 결과 병합
        val zip = builder.add(Zip.create<List<String>, Int>())
        
        // 그래프 연결
        builder.from(source).via(splitter).viaFanOut(broadcast)
        // ...
    }
)
```

### 2. Java Streams API

Java 8에서 도입된 함수형 프로그래밍 스타일의 스트림 처리.

#### 핵심 특징:
- **간단한 API**: 직관적인 메서드 체이닝
- **병렬 처리**: `parallel()` 메서드로 쉬운 병렬화
- **동기식**: 기본적으로 블로킹 연산

#### 구현 예시:
```kotlin
val words = parts.stream()
    .filter { it.matches("[^\\d]+".toRegex()) }
    .collect(Collectors.toList())

val numberSum = parts.stream()
    .filter { it.matches("\\d+".toRegex()) }
    .mapToInt { it.toInt() }
    .sum()
```

### 3. WebFlux (Reactor)

Spring의 리액티브 프로그래밍 라이브러리.

#### 핵심 특징:
- **논블로킹**: 완전한 비동기 처리
- **Backpressure**: 다양한 백프레셔 전략
- **연산자 풍부**: 다양한 변환 및 조합 연산자

#### 구현 예시:
```kotlin
Mono.just(text)
    .flatMap { parts ->
        val wordsFlux = Flux.fromIterable(parts)
            .filter { /* 단어 필터 */ }
            .collectList()
        
        val numberSumMono = Flux.fromIterable(parts)
            .filter { /* 숫자 필터 */ }
            .map { it.toInt() }
            .reduce(0) { acc, num -> acc + num }
        
        Mono.zip(wordsFlux, numberSumMono)
    }
```

### 4. Kotlin Coroutines

Kotlin의 경량 스레드 구현.

#### 핵심 특징:
- **구조화된 동시성**: 안전한 동시성 관리
- **Flow API**: 콜드 스트림 처리
- **채널**: 핫 스트림 처리
- **가독성**: 동기 코드처럼 보이는 비동기 코드

#### 구현 예시:
```kotlin
suspend fun processText(text: String): StreamResult = coroutineScope {
    val parts = text.split("\\s+".toRegex())
    
    // 병렬 처리
    val wordsDeferred = async {
        parts.filter { it.matches("[^\\d]+".toRegex()) }
    }
    
    val numberSumDeferred = async {
        parts.filter { it.matches("\\d+".toRegex()) }
            .map { it.toInt() }
            .sum()
    }
    
    StreamResult(
        originalText = text,
        words = wordsDeferred.await(),
        wordCount = wordsDeferred.await().size,
        numberSum = numberSumDeferred.await()
    )
}
```

## 성능 비교

각 구현의 특성:

| 구현 | 장점 | 단점 | 적합한 사용 사례 |
|------|------|------|-----------------|
| **Pekko Streams** | - 복잡한 그래프 처리<br>- 강력한 백프레셔<br>- 액터 통합 | - 학습 곡선이 높음<br>- 설정이 복잡 | 복잡한 스트림 토폴로지, 액터 시스템 통합 |
| **Java Streams** | - 간단한 API<br>- Java 표준<br>- 빠른 동기 처리 | - 백프레셔 없음<br>- 비동기 지원 제한 | 간단한 데이터 변환, 동기식 처리 |
| **WebFlux** | - 완전한 리액티브<br>- Spring 생태계<br>- 풍부한 연산자 | - 디버깅 어려움<br>- 스택 트레이스 복잡 | 웹 애플리케이션, 리액티브 시스템 |
| **Coroutines** | - 읽기 쉬운 코드<br>- 구조화된 동시성<br>- Kotlin 네이티브 | - Kotlin 전용<br>- 생태계가 작음 | Kotlin 프로젝트, 안드로이드 |

## 실행 방법

### 테스트 실행:
```bash
./gradlew test
```

### 개별 테스트 실행:
```bash
./gradlew test --tests "*ProcessorComparisonTest*"
```

## 주요 학습 포인트

1. **백프레셔 처리**: 각 구현이 데이터 과부하를 어떻게 처리하는지
2. **병렬 처리**: 각 라이브러리의 병렬화 접근 방식
3. **에러 처리**: 스트림 처리 중 에러 전파 방식
4. **리소스 관리**: 각 구현의 리소스 사용 패턴

## 결론

- **간단한 처리**: Java Streams API
- **리액티브 웹**: WebFlux
- **복잡한 그래프**: Pekko Streams
- **Kotlin 프로젝트**: Coroutines

각 도구는 특정 상황에서 장점이 있으며, 프로젝트 요구사항에 따라 적절한 선택이 필요합니다.