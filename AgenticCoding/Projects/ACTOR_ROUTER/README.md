# Actor Router Tutorial

Actor Router 프로젝트는 Apache Pekko(구 Akka)를 사용하여 다양한 작업 분배 전략을 구현하는 튜토리얼입니다. 이 프로젝트는 로컬 액터 시스템에서 작동하며, 서비스 로직 변경 없이 리모트/클러스터로 확장 가능한 구조로 설계되었습니다.

## 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [핵심 구성 요소](#핵심-구성-요소)
3. [라우팅 전략](#라우팅-전략)
4. [사용 방법](#사용-방법)
5. [확장성](#확장성)
6. [모니터링 및 메트릭](#모니터링-및-메트릭)
7. [테스트](#테스트)
8. [로컬에서 클러스터로 전환](#로컬에서-클러스터로-전환)

## 프로젝트 개요

이 프로젝트는 Actor 모델을 사용하여 작업을 여러 워커에게 분배하는 다양한 패턴을 보여줍니다. 주요 목표는:

- 다양한 라우팅 전략 구현
- 동적 스케일링 지원
- 작업 모니터링 및 메트릭 수집
- 장애 처리 및 복원력
- 로컬에서 분산 시스템으로의 쉬운 전환

## 핵심 구성 요소

### 1. WorkerActor

작업을 실제로 수행하는 액터입니다.

```kotlin
class WorkerActor(
    private val workerId: String,
    private val routerRef: ActorRef<RouterCommand>
) : AbstractBehavior<WorkerCommand>(context) {
    
    // 작업 처리
    private fun onDoWork(command: DoWork): Behavior<WorkerCommand> {
        // 작업 수행 시뮬레이션
        processTask(command)
        // 완료 보고
        routerRef.tell(WorkCompleted(workerId, command.taskId, result))
        return this
    }
}
```

주요 특징:
- 한 번에 하나의 작업만 처리
- 우선순위별 처리 시간 차별화
- 작업 완료 후 라우터에 보고
- 메트릭 추적 (처리 시간, 작업 수 등)

### 2. RouterActor

작업을 워커들에게 분배하는 관리 액터입니다.

```kotlin
class RouterActor(
    private val routingStrategy: RoutingStrategy,
    private val initialWorkerCount: Int
) : AbstractBehavior<RouterCommand>(context) {
    
    // 작업 분배
    private fun onProcessTask(command: ProcessTask): Behavior<RouterCommand> {
        val worker = selectWorker(command)
        worker.tell(DoWork(...))
        return this
    }
}
```

주요 기능:
- 다양한 라우팅 전략 지원
- 동적 워커 스케일링
- 작업 추적 및 메트릭 수집

### 3. 명령 및 메시지 모델

```kotlin
// 라우터 명령
sealed interface RouterCommand
data class ProcessTask(val taskId: String, val payload: String, val priority: TaskPriority)
data class ScaleWorkers(val delta: Int)
data class GetRouterMetrics(val replyTo: ActorRef<RouterMetrics>)

// 워커 명령
sealed interface WorkerCommand
data class DoWork(val taskId: String, val payload: String, val priority: TaskPriority)
data class GetWorkerMetrics(val replyTo: ActorRef<WorkerMetrics>)
```

## 라우팅 전략

### 1. Round Robin (순차 분배)

```kotlin
private fun selectRoundRobin(): ActorRef<WorkerCommand> {
    val worker = workers[roundRobinIndex % workers.size]
    roundRobinIndex = (roundRobinIndex + 1) % workers.size
    return worker
}
```

- 작업을 순서대로 각 워커에게 분배
- 가장 공평한 분배 방식
- 워커의 현재 상태를 고려하지 않음

### 2. Random (랜덤 분배)

```kotlin
private fun selectRandom(): ActorRef<WorkerCommand> {
    return workers[Random.nextInt(workers.size)]
}
```

- 무작위로 워커 선택
- 간단하고 빠른 선택
- 통계적으로 균등한 분배

### 3. Least Loaded (최소 부하)

```kotlin
private fun selectLeastLoaded(): ActorRef<WorkerCommand> {
    updateWorkerMetrics()
    return workers.minByOrNull { getWorkerLoad(it) }!!
}
```

- 현재 가장 부하가 적은 워커 선택
- 동적 부하 분산
- 메트릭 조회로 인한 오버헤드 존재

### 4. Consistent Hash (일관된 해시)

```kotlin
private fun selectConsistentHash(taskId: String): ActorRef<WorkerCommand> {
    val hash = taskId.hashCode()
    val index = Math.abs(hash) % workers.size
    return workers[index]
}
```

- 동일한 ID는 항상 같은 워커로
- 캐시 친화적
- 상태가 있는 작업에 유용

### 5. Priority Based (우선순위 기반)

```kotlin
private fun selectPriorityBased(priority: TaskPriority): ActorRef<WorkerCommand> {
    return when (priority) {
        TaskPriority.CRITICAL, TaskPriority.HIGH -> 
            workers[Random.nextInt(0, workers.size / 2)]
        TaskPriority.NORMAL, TaskPriority.LOW -> 
            workers[Random.nextInt(workers.size / 2, workers.size)]
    }
}
```

- 우선순위별로 워커 그룹 분리
- 중요한 작업의 빠른 처리 보장

### 6. Broadcast (브로드캐스트)

```kotlin
// BroadcastRouterActor에서 구현
workers.forEach { worker ->
    worker.tell(DoWork(...))
}
```

- 모든 워커에게 동일한 작업 전송
- 결과 집계 후 응답
- 중복 처리를 통한 신뢰성 향상

## 사용 방법

### 라우터 생성 및 작업 전송

```kotlin
// 라우터 생성
val system = ActorSystem.create(Behaviors.empty<Void>(), "RouterSystem")
val router = system.systemActorOf(
    RouterActor.create(RoutingStrategy.ROUND_ROBIN, 3),
    "router"
)

// 작업 전송
val resultProbe = TestProbe<TaskResult>()
router.tell(ProcessTask(
    taskId = "task-1",
    payload = "Process this data",
    priority = TaskPriority.HIGH,
    replyTo = resultProbe.ref
))

// 결과 수신
val result = resultProbe.receiveMessage()
```

### 동적 스케일링

```kotlin
// 워커 추가
router.tell(ScaleWorkers(delta = 2, replyTo = scaleProbe.ref))

// 워커 감소
router.tell(ScaleWorkers(delta = -1, replyTo = scaleProbe.ref))
```

### 메트릭 조회

```kotlin
// 라우터 메트릭
router.tell(GetRouterMetrics(metricsProbe.ref))
val metrics = metricsProbe.receiveMessage()
println("총 처리 작업: ${metrics.totalTasksProcessed}")
println("평균 처리 시간: ${metrics.averageProcessingTimeMs}ms")

// 워커 상태
router.tell(GetWorkerStatuses(statusProbe.ref))
val statuses = statusProbe.receiveMessage()
statuses.workers.forEach { worker ->
    println("워커 ${worker.workerId}: ${worker.tasksProcessed}개 처리")
}
```

## 확장성

### 스케일 인/아웃

시스템은 실행 중에 워커 수를 동적으로 조정할 수 있습니다:

```kotlin
// 부하가 증가하면 워커 추가
if (metrics.averageProcessingTimeMs > threshold) {
    router.tell(ScaleWorkers(delta = 2, replyTo = probe.ref))
}

// 부하가 감소하면 워커 감소
if (metrics.averageProcessingTimeMs < lowerThreshold) {
    router.tell(ScaleWorkers(delta = -1, replyTo = probe.ref))
}
```

## 모니터링 및 메트릭

시스템은 다음과 같은 메트릭을 제공합니다:

1. **라우터 메트릭**
   - 총 처리된 작업 수
   - 진행 중인 작업 수
   - 평균 처리 시간
   - 현재 워커 수

2. **워커 메트릭**
   - 처리한 작업 수
   - 현재 부하 상태
   - 평균 처리 시간
   - 가용성 상태

## 테스트

프로젝트는 포괄적인 단위 테스트를 포함합니다:

```bash
# 모든 테스트 실행
./gradlew test

# 특정 테스트 실행
./gradlew test --tests RouterActorTest
```

주요 테스트 시나리오:
- 각 라우팅 전략의 동작 검증
- 스케일링 기능 테스트
- 장애 처리 테스트
- 성능 및 부하 테스트

## 로컬에서 클러스터로 전환

현재 구현은 로컬 액터 시스템에서 작동하지만, 서비스 로직 변경 없이 클러스터로 확장할 수 있습니다.

### 1. 클러스터 의존성 추가

```kotlin
dependencies {
    implementation("org.apache.pekko:pekko-cluster-typed_2.13:1.1.2")
    implementation("org.apache.pekko:pekko-cluster-sharding-typed_2.13:1.1.2")
}
```

### 2. 클러스터 설정

```hocon
# application.conf
pekko {
  actor {
    provider = "cluster"
  }
  
  remote.artery {
    canonical {
      hostname = "127.0.0.1"
      port = 2551
    }
  }
  
  cluster {
    seed-nodes = ["pekko://RouterSystem@127.0.0.1:2551"]
    roles = ["router", "worker"]
  }
}
```

### 3. 클러스터 라우터 생성

```kotlin
// 로컬 라우터를 클러스터 라우터로 변경
val router = ClusterRouterPool(
    local = RoundRobinPool(5),
    settings = ClusterRouterPoolSettings(
        totalInstances = 100,
        maxInstancesPerNode = 5,
        allowLocalRoutees = true,
        useRoles = Set("worker")
    )
).props(WorkerActor.props())
```

### 4. 리모트 배포

```hocon
# 워커를 리모트 노드에 배포
pekko.actor.deployment {
  /router/worker {
    remote = "pekko://RouterSystem@worker-node:2552"
  }
}
```

### 주요 변경사항

1. **Serialization**: 메시지는 네트워크를 통해 전송되므로 직렬화 가능해야 함
2. **Location Transparency**: ActorRef는 로컬/리모트 구분 없이 동일하게 작동
3. **Fault Tolerance**: 네트워크 파티션 및 노드 실패 처리 추가
4. **Discovery**: 클러스터 노드 자동 발견 메커니즘

이러한 변경사항들은 비즈니스 로직에는 영향을 주지 않으며, 주로 설정과 인프라 레벨에서 이루어집니다.

## 성능 고려사항

1. **라우팅 전략 선택**
   - Round Robin: 균등한 부하, 빠른 선택
   - Least Loaded: 최적 부하 분산, 메트릭 오버헤드
   - Consistent Hash: 캐시 효율성, 스케일링 시 재분배

2. **워커 수 결정**
   - CPU 코어 수 고려
   - 작업의 I/O vs CPU 집약도
   - 메모리 사용량

3. **메트릭 수집 주기**
   - 너무 자주: 오버헤드 증가
   - 너무 드물게: 부정확한 라우팅

## 결론

이 프로젝트는 Actor 모델을 사용한 작업 분배의 다양한 패턴을 보여줍니다. 로컬에서 시작하여 필요에 따라 분산 시스템으로 확장할 수 있는 유연한 구조를 제공합니다. 각 라우팅 전략은 특정 사용 사례에 최적화되어 있으며, 실제 요구사항에 맞게 선택하여 사용할 수 있습니다.