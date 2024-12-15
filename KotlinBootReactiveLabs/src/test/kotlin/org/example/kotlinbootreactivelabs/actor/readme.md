# 프로젝트 개요

Akka의 오픈소스 쿨다운 버전인 Pekko를 Kotlin 버전으로 액터를 구현하고 테스트하는 예제를 제공합니다.
구현된 코드및 테스트코드와함께 연관 문서가 링크되어 있습니다.

Spring Boot내에서 작동되며, 테스트코드는 Junit5를 이용합니다.

## 사전셋업

### SpringBoot와 통합

- https://wiki.webnori.com/pages/viewpage.action?pageId=93946878

### [`Infra`](../../../../../../test/docker/)

- 액터 영속성 CQRS파트를 위한 데이터베이스 장치및 DDL을 구성합니다.


## 주요 Test 파일 

### [`HelloActorTest.kt`](core/HelloActorTest.kt)

- Hello 이벤트를 발생시키면, Kotlin으로 응답하는 기본 액터를 설명하고 테스트합니다.
- Doc : https://pekko.apache.org/docs/pekko/current/typed/actor-lifecycle.html


### [`DiscoveryTest.kt`](discovery/DiscoveryTest.kt)

- 액터를 로컬에서 이용가능한 참조자(ActorRef) 접근과 , 리모트에 및 클러스터에서 활용가능한 Receptionist를 활용한 액터 찾기를 설명하고 테스트합니다. 
- Doc : https://pekko.apache.org/docs/pekko/current/typed/actor-discovery.html

### [`HelloStateActorTest.kt`](state/HelloStateActorTest.kt)

- 액터에 상태를 정의하여, 이벤트에 반응하여 상태값을 변경하고, 상태값을 조회하는 액터를 설명하고 테스트합니다.

### [`TimerActorTest.kt`](timer/basic/TimerActorTest.kt)

- 액터는 고유의 스케줄러 기능을 가질수 있으며 기본 액터에 타이머를 탑재하고 테스트합니다.
- Doc : https://pekko.apache.org/docs/pekko/1.1/typed/interaction-patterns.html#typed-scheduling

### [`SupervisorActorTest.kt`](supervisor/SupervisorActorTest.kt)

- 액터는 Fault-Tolerance를 지원하며, 부모액터는 자식액터의 생명주기를 관리하고, 자식액터의 예외를 처리하는 방법을 설명하고 테스트합니다.
- Doc : https://pekko.apache.org/docs/pekko/1.1/typed/fault-tolerance.html

### [`HelloRouterTest.kt`](router/HelloRouterTest.kt)
- 액터는 라우터를 통해 병렬및 분산처리를 구성할수 있으며 다양한 라우터 장치를 제공합니다.
- https://pekko.apache.org/docs/pekko/1.1/typed/routers.html

### [`HelloStateStoreActorTest.kt`](state/store/HelloStateStoreActorTest.kt)
- 액터는 상태를 저장하고, 상태를 복구할수 있는 상태저장소를 연결할수 있습니다. 본 샘플은 커스텀한 r2dbc 연결이 이용되었습니다.
- https://pekko.apache.org/docs/pekko/1.1/typed/stash.html

### [`HelloPersistentDurableStateActorTest.kt`](persistent/durable/HelloPersistentDurableStateActorTest.kt)
- 액터의 상태 영속성을 커스텀하게 제작할수도 있지만 제공되는 플러그인을 활용할수도 있습니다. 쿨다운버전인 이유로ㅡ r2dbc와같이 최신버전을 플러그인이 늦을수 있기때문에 영속성 장치는 커스텀하게 구성하는것이 권장됩니다. (플러그인 테스트결과:2.7x 이후에서나 쓸만함)
- https://pekko.apache.org/docs/pekko/1.1/typed/index-persistence-durable-state.html

### [`ClusterTest.kt`](cluster/ClusterTest.kt)
- 로컬로 작성된 액터를 보유한 어플리케이션은 pekko.conf 설정만으로 클러스터 구성이 되며, 분산액터를 포함 클러스터내 단하나만 작동을 보장하는 액터를 구성할수 있으며 위치를 알필요없이 단일어플리케이션에서 인메모리를 이용하는것처럼 액터를 이용할수 있습니다.
- https://pekko.apache.org/docs/pekko/1.1/typed/cluster-singleton.html
- https://pekko.apache.org/docs/pekko/1.1/typed/cluster-sharding.html


## 추가 참고링크 

Akka버전을 베이스로 작성되었지만 Pekko와 호환됩니다.

- [Akka with Kotlin](https://wiki.webnori.com/display/AKKA/AKKA.Kotlin)
