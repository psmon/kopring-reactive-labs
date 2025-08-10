# Project Creation Guidelines
- Create a project in the AgenticCoding/Projects/PERSIST_EVENTSOURCE folder.
- I want to create a Kotlin-based functional module only, not as an application.
- I want to write, execute, and verify unit tests that meet the following functional requirements.

## Implementation Module and Unit Tests
- I want to create virtual wallet functionality using event sourcing techniques.
- Store virtual wallet point deposits (DepositAdded), withdrawals (WithdrawalMade), etc. as events.
- If there are additional functional ideas that the virtual wallet should have, apply them additionally
- When replayed, it can always accurately reconstruct the account balance and transaction history, implementing **audit** and **traceability** functions
- Also utilize snapshot techniques during event sourcing

## Local Environment Additional Guidelines
- Configure PostgreSQL using DockerCompose.
- After running the infrastructure~ perform unit tests to verify build errors and unit tests.

## Usage Sample Code

There are pekko sample codes and reference documents that utilize eventsource. Analyze and utilize the following:

- https://github.com/apache/pekko-samples/blob/main/pekko-sample-persistence-java/src/main/java/sample/persistence/ShoppingCart.java
- https://pekko.apache.org/docs/pekko/current/typed/persistence.html#event-sourcing

## Unit Test Execution and Additional Guidelines
- After completing the code, attempt unit testing of the completed code.
- For tests that require waiting for a long time, use manualTime.timePasses.
- Use pekko testkit to verify and confirm through completion target probe.
- Also perform load tests to understand the advantages of performance benefits, audit, and tracking benefits compared to implementing CRUD through unit tests.
- Once the unit test code is completed, explain the code concept and tutorial in readme.md in an easy-to-understand way for beginners. If necessary, also include diagram explanations using mermaid.
- Additionally in readme.md, compare the advantages and disadvantages of traditional CRUD and event sourcing techniques, and among CQRS techniques, compare event sourcing with various devices such as DurableState, persistence query, state store plugin, etc.

## Multi-language Writing Guidelines Support
- README.md should be written in English
- README-kr.md should be written in Korean (translate the English version to Korean)

## 
To use the persistence supported by pekko, use the following schema in PostgreSQL:

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

## Pekko Reference Knowledge

There are Pekko sample codes that work with Kotlin in the following directories:

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

### Reference Targets
- Reference target directories refer to files in the subdirectories of the reference code locations
- Reactive stream-based concurrency processing and various actor models are implemented in Kotlin based on Spring Boot.
- For code learning targets, refer to *.kt and .md files
- When unit tests are needed, refer to and improve upon the methods used in test files
- When dependencies are the same as this sample code, match the same versions - using Gradle