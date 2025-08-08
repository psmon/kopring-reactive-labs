
# 프로젝트 생성지침
- AgenticCoding/Projects/PERSIST_DURABLE_CLUSTER 폴더에서 프로젝트를 생성합니다.
- 어플리케이션으로 작동이 아닌 코틀린기반 기능모듈만 만들고자합니다.
- 다음 기능요구사항을 충족하는 유닛테스트를 작성실행및 검증을 진행하려합니다.

## 구현모듈및 유닛테스트
- StandAlone으로 작동하는 AgenticCoding/Projects/PERSIST_DURABLE 를 분석해, Cluster모드로 업그레이드
- 분산키는 mallId-userId를 이용 해쉬화

## 로컬환경 추가지침
- PostGres는 DockerCompose를 이용해 구성합니다.
- 인프라를 구동후~ 유닛테스트를 수행해 , 빌드오류및 유닛테스트를 검증합니다.

## 유닛테스트 수행및 부가지침
- 코드 완성후, 완성된 코드 클러스터 모드로 유닛테스트 시도합니다. 
- 긴시간을 기다려야하는 테스트인경우, manualTime.timePasses 을 활용합니다.
- pekko testkit을 활용해, 완료대상 probe를 통해 검증및 확인합니다. 클러스터 테스트인경우 probe는 동일노드를 검증및 분리되어야합니다.
- 유닛테스트 코드가 완성되면 readme.md에 코드컨셉및 듀토리얼을 초보자를 위해 쉽게 설명합니다. 필요하면 mermaid이용 다이어그램 설명도 함께합니다.
- readme.md 에 추가로 pekko-persist를 클러스터화 상태 프로그래밍을 할때 kafka-ktable, apachi-flink 와 비교해 각각 장치의 장단점을 함께설명합니다.

## Pekko 참고지식

다음 디렉토리에 코틀린으로 작동되는 Pekko 샘플코드들이 있습니다.

```
current working directory:
├── CommonModel/
│   └── src/
├── Docs/
│   ├── eng/
│   └── kr/
├── KotlinBootReactiveLabs/
│   └── src/
│       ├── main/
│       └── test/
└── README.MD
```

### 참고대상
- 참고대상 디렉토리는 참고코드 위치 하위 디렉토리에 있는 파일을 참고
- 스프링 부트기반 코틀린으로 리액티브 스트림기반의 동시성처리및 다양한 액터모델이 구현되었습니다.
- 코드학습대상은 *.kt와 .md파일을 참고할것
- 유닛테스트가 필요하게될시 test 파일에 사용되는 방식을 참고하고 개선할것
- 필요한 디펜던시는 이 샘플코드와 동일할시, 버전을 동일하게 맞출것 - 그레이들사용
- 클러스터에 사용된 메시지 시리얼라이즈는 KotlinBootReactiveLabs/src/main/resources/application.conf 여기참고 하고 주변코드도 살펴볼것
- 클러스터 유닛테스트 KotlinBootReactiveLabs/src/test/kotlin/org/example/kotlinbootreactivelabs/actor/cluster/ClusterTest.kt 참고