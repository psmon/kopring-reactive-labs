
# 프로젝트 생성지침
- AgenticCoding/Projects/CONNECTOR_KAFKA 폴더에서 프로젝트를 생성합니다.
- 어플리케이션으로 작동이 아닌 코틀린기반 기능모듈만 만들고자합니다.
- 다음 기능요구사항을 충족하는 유닛테스트를 작성실행및 검증을 진행하려합니다.
- 작성이 모두되면 readme.md에 코드컨셉및 듀토리얼을 초보자를 위해 쉽게 설명합니다.

## 구현모듈및 유닛테스트
- 생산자는 topic명 `test-topic1`에 메시지를 전송합니다.
- 메시지는 자바 직렬화객체로 "eventType, eventId, eventString" 형태로 구성됩니다.
- 소비자는 test-topic1에서 메시지를 수신합니다.
- 소비자는 액터모델로 연결되었으며 마지막 eventString값을 상태로 저장합니다.
- 생산된 이벤트수와, 수신된 이벤트수를 검증합니다

## 로컬환경 추가지침
- Kafka는 DockerCompose를 이용해 구성합니다.
- 인프라를 구동후~ 유닛테스트를 수행해 , 빌드오류및 유닛테스트를 검증합니다.


## Kafka 활용샘플코드
의존성:
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/discovery.html#dependency

사용예제:
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/atleastonce.html#at-least-once-delivery
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/atleastonce.html#multiple-effects-per-commit
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/atleastonce.html#conditional-message-processing

고급기법:
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/transactions.html#transactional-source
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/transactions.html#transactional-sink-and-flow
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/transactions.html#consume-transform-produce-workflow
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/transactions.html#caveats
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/transactions.html#further-reading

에러핸들링:
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/errorhandling.html#failing-consumer
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/errorhandling.html#failing-producer
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/errorhandling.html#restarting-the-stream-with-a-backoff-stage
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/errorhandling.html#unexpected-consumer-offset-reset

유닛테스트:
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/testing.html#testing
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/testing.html#running-kafka-with-your-tests
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/testing.html#alternative-testing-libraries
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/testing.html#mocking-the-consumer-or-producer
- https://pekko.apache.org/docs/pekko-connectors-kafka/current/testing-testcontainers.html


## 유닛테스트 수행및 부가지침
- 코드 완성후, 완성된 코드 유닛테스트 시도합니다.
- pekko testkit을 활용해 , 완료대상 probe를 통해 검증및 tps확인합니다.
- 유닛테스트 코드가 완성되면 readme.md에 코드컨셉및 듀토리얼을 초보자를 위해 쉽게 설명합니다.

## 다국어 작성 지침 지원
- README.md 는 영문으로 작성
- README-kr.md 은 한글로작성(영문 작성버전을 한글로 번역)

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