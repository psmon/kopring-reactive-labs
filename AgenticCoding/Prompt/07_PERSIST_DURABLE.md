
# 프로젝트 생성지침
- AgenticCoding/Projects/PERSIST_DURABLE 폴더에서 프로젝트를 생성합니다.
- 어플리케이션으로 작동이 아닌 코틀린기반 기능모듈만 만들고자합니다.
- 다음 기능요구사항을 충족하는 유닛테스트를 작성실행및 검증을 진행하려합니다.

## 구현모듈및 유닛테스트
- 불특정 발생하는 사용자의 행동이벤트로 부터, 특정 마지막 상태값을 유지 저장하는 액터모델을 만들고자합니다.
- pekko-r2dbc persist에서 지원하는 durable_state를 이용합니다.
- 유지해야할 상태정보는 마지막 로그인, 마지막 장바구니사용일, 최근본상품 3개, 마케팅수신 여부등이 있습니다. 
- 고유식별자는  mallId-userId 형태와 같이 구성합니다.
- 사용자별 30분동안 이벤트가 없으면, 액터모델이 스스로 셧다운하며 다음 일치하는 사용자의 요청이 있을시 액터모델은 다시 영속성을 복원작동합니다.


## 로컬환경 추가지침
- PostGres는 DockerCompose를 이용해 구성합니다.
- 인프라를 구동후~ 유닛테스트를 수행해 , 빌드오류및 유닛테스트를 검증합니다.


## 활용샘플코드

KotlinBootReactiveLabs하위 디렉토리에 기본 샘플코드가 있습니다. 다음을 분석후 활용
- HelloPersistentDurable.kt
- HelloPersistentDurableStateActorTest.kt

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


## 유닛테스트 수행및 부가지침
- 코드 완성후, 완성된 코드 유닛테스트 시도합니다.
- 긴시간을 기다려야하는 테스트인경우, manualTime.timePasses 을 활용합니다.
- pekko testkit을 활용해, 완료대상 probe를 통해 검증및 확인합니다.
- 유닛테스트 코드가 완성되면 readme.md에 코드컨셉및 듀토리얼을 초보자를 위해 쉽게 설명합니다. 필요하면 mermaid이용 다이어그램 설명도 함께합니다.
- readme.md 에 추가로 pekko-persist를 통해 상태 프로그래밍을 할때 kafka-ktable, apachi-flink 와 비교해 각각 장치의 장단점을 함께설명합니다.

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