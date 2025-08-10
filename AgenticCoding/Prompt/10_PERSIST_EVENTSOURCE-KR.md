
# 프로젝트 생성지침
- AgenticCoding/Projects/PERSIST_EVENTSOURCE 폴더에서 프로젝트를 생성합니다.
- 어플리케이션으로 작동이 아닌 코틀린기반 기능모듈만 만들고자합니다.
- 다음 기능요구사항을 충족하는 유닛테스트를 작성실행및 검증을 진행하려합니다.

## 구현모듈및 유닛테스트
- 가상월렛의 기능을 이벤트소싱 기법을 이용해 만드려합니다.
- 가상월렛 포인트 입금(DepositAdded), 출금(WithdrawalMade) 등을 이벤트로 저장.
- 가상월렛이 추가로 가져야할 기능 아이디어가 있다면 추가적용
- 재생하면 계좌의 잔액과 거래 이력을 언제나 정확하게 재구성할 수 있어, **감사(audit)**와 **추적(traceability)** 기능도 구현
- 이벤트 소싱중 스냅샵 기법도활용


## 로컬환경 추가지침
- PostGres는 DockerCompose를 이용해 구성합니다.
- 인프라를 구동후~ 유닛테스트를 수행해 , 빌드오류및 유닛테스트를 검증합니다.

## 활용샘플코드

eventsource를 활용한 pekko 샘플코드및 참고문서가 있습니다. 다음을 분석후 활용

- https://github.com/apache/pekko-samples/blob/main/pekko-sample-persistence-java/src/main/java/sample/persistence/ShoppingCart.java
- https://pekko.apache.org/docs/pekko/current/typed/persistence.html#event-sourcing


## 유닛테스트 수행및 부가지침
- 코드 완성후, 완성된 코드 유닛테스트 시도합니다.
- 긴시간을 기다려야하는 테스트인경우, manualTime.timePasses 을 활용합니다.
- pekko testkit을 활용해, 완료대상 probe를 통해 검증및 확인합니다.
- CRUD를 구현했을때보다 성능적인 이점, 감사,추적의 이점등도 유닛테스트를 통해 장점을 알수 있는 로드테스트도 수행해..
- 유닛테스트 코드가 완성되면 readme.md에 코드컨셉및 듀토리얼을 초보자를 위해 쉽게 설명합니다. 필요하면 mermaid이용 다이어그램 설명도 함께합니다.
- readme.md 에 추가로 전통적인 CRUD와 이벤트소싱기법의 장단점을 비교, CQRS 기법중에서도 이벤트소싱과 다음을 DurabbleState, persistence query, state store plugin 등 다양한 장치와도 비교


## 다국어 작성 지침 지원
- README.md 는 영문으로 작성
- README-kr.md 은 한글로작성(영문 작성버전을 한글로 번역)

##
pekko가 지원하는 영속성을 이용하기위해서는 PostgreSQL에서 다음과 같은 스키마를 이용

```postgresql
CREATE TABLE IF NOT EXISTS event_journal(
                                            slice INT NOT NULL,
                                            entity_type VARCHAR(255) NOT NULL,
    persistence_id VARCHAR(255) NOT NULL,
    seq_nr BIGINT NOT NULL,
    db_timestamp timestamp with time zone NOT NULL,

                               event_ser_id INTEGER NOT NULL,
                               event_ser_manifest VARCHAR(255) NOT NULL,
    event_payload BYTEA NOT NULL,

    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    writer VARCHAR(255) NOT NULL,
    adapter_manifest VARCHAR(255),
    tags TEXT ARRAY,

    meta_ser_id INTEGER,
    meta_ser_manifest VARCHAR(255),
    meta_payload BYTEA,

    PRIMARY KEY(persistence_id, seq_nr)
    );

-- `event_journal_slice_idx` is only needed if the slice based queries are used
CREATE INDEX IF NOT EXISTS event_journal_slice_idx ON event_journal(slice, entity_type, db_timestamp, seq_nr);

CREATE TABLE IF NOT EXISTS snapshot(
                                       slice INT NOT NULL,
                                       entity_type VARCHAR(255) NOT NULL,
    persistence_id VARCHAR(255) NOT NULL,
    seq_nr BIGINT NOT NULL,
    write_timestamp BIGINT NOT NULL,
    ser_id INTEGER NOT NULL,
    ser_manifest VARCHAR(255) NOT NULL,
    snapshot BYTEA NOT NULL,
    meta_ser_id INTEGER,
    meta_ser_manifest VARCHAR(255),
    meta_payload BYTEA,

    PRIMARY KEY(persistence_id)
    );

CREATE TABLE IF NOT EXISTS durable_state (
    slice INT NOT NULL,
    entity_type VARCHAR(255) NOT NULL,
    persistence_id VARCHAR(255) NOT NULL,
    revision BIGINT NOT NULL,
    db_timestamp timestamp with time zone NOT NULL,
    state_ser_id INTEGER NOT NULL,
    state_ser_manifest VARCHAR(255),
    state_payload BYTEA NOT NULL,
    tags TEXT ARRAY,

PRIMARY KEY(persistence_id, revision)
);

-- `durable_state_slice_idx` is only needed if the slice based queries are used
CREATE INDEX IF NOT EXISTS durable_state_slice_idx ON durable_state(slice, entity_type, db_timestamp, revision);

-- Primitive offset types are stored in this table.
-- If only timestamp based offsets are used this table is optional.
-- Configure pekko.projection.r2dbc.offset-store.offset-table="" if the table is not created.
CREATE TABLE IF NOT EXISTS projection_offset_store (
                                                       projection_name VARCHAR(255) NOT NULL,
    projection_key VARCHAR(255) NOT NULL,
    current_offset VARCHAR(255) NOT NULL,
    manifest VARCHAR(32) NOT NULL,
    mergeable BOOLEAN NOT NULL,
    last_updated BIGINT NOT NULL,
    PRIMARY KEY(projection_name, projection_key)
    );

-- Timestamp based offsets are stored in this table.
CREATE TABLE IF NOT EXISTS projection_timestamp_offset_store (
                                                                 projection_name VARCHAR(255) NOT NULL,
    projection_key VARCHAR(255) NOT NULL,
    slice INT NOT NULL,
    persistence_id VARCHAR(255) NOT NULL,
    seq_nr BIGINT NOT NULL,
    -- timestamp_offset is the db_timestamp of the original event
    timestamp_offset timestamp with time zone NOT NULL,
    -- timestamp_consumed is when the offset was stored
    -- the consumer lag is timestamp_consumed - timestamp_offset
    timestamp_consumed timestamp with time zone NOT NULL,
                                     PRIMARY KEY(slice, projection_name, timestamp_offset, persistence_id, seq_nr)
    );

CREATE TABLE IF NOT EXISTS projection_management (
                                                     projection_name VARCHAR(255) NOT NULL,
    projection_key VARCHAR(255) NOT NULL,
    paused BOOLEAN NOT NULL,
    last_updated BIGINT NOT NULL,
    PRIMARY KEY(projection_name, projection_key)
    );
```


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