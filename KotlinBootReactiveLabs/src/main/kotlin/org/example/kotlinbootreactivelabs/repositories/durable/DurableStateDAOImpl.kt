package org.example.kotlinbootreactivelabs.repositories.durable

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.r2dbc.spi.ConnectionFactory
import org.example.kotlinbootreactivelabs.repositories.durable.dao.DurableStateDAO
import org.example.kotlinbootreactivelabs.repositories.durable.dto.DurableState
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
class DurableStateDAOImpl(private val connectionFactory: ConnectionFactory) : DurableStateDAO {

    private val client = DatabaseClient.create(connectionFactory)

    private val objectMapper = jacksonObjectMapper()

    override fun <T> findByIdEx(persistenceId: String, revision: Long): Mono<T> {
        return client.sql("SELECT * FROM durable_state WHERE persistence_id = :persistenceId AND revision = :revision")
            .bind("persistenceId", persistenceId)
            .bind("revision", revision)
            .map { row, _ ->
                val entityType = row.get("entity_type", String::class.java)!!
                val statePayload = row.get("state_payload", ByteArray::class.java)!!
                objectMapper.readValue(statePayload, Class.forName(entityType)) as T
            }
            .one()
            .switchIfEmpty(Mono.justOrEmpty(null))
    }

    override fun <T : Any> createOrUpdateEx(persistenceId: String, revision: Long, entity: T): Mono<T> {
        val entityType = entity::class.java.name
        val statePayload = objectMapper.writeValueAsBytes(entity)
        val durableState = DurableState(
            slice = 0, // Set appropriate values
            entityType = entityType,
            persistenceId = persistenceId,
            revision = revision,
            dbTimestamp = java.time.LocalDateTime.now(),
            stateSerId = 0, // Set appropriate values
            stateSerManifest = "",
            statePayload = statePayload,
            tags = ""
        )
        return createOrUpdate(durableState).thenReturn(entity)
    }

    override fun findAll(): Flux<DurableState> {
        return client.sql("SELECT * FROM durable_state")
            .map { row, _ ->
                DurableState(
                    row.get("slice", Int::class.java)!!,
                    row.get("entity_type", String::class.java)!!,
                    row.get("persistence_id", String::class.java)!!,
                    row.get("revision", Long::class.java)!!,
                    row.get("db_timestamp", java.time.LocalDateTime::class.java)!!,
                    row.get("state_ser_id", Int::class.java)!!,
                    row.get("state_ser_manifest", String::class.java )!!,
                    row.get("state_payload", ByteArray::class.java)!!,
                    row.get("tags", String::class.java)!!
                )
            }
            .all()
    }

    override fun findById(persistenceId: String, revision: Long): Mono<DurableState> {
        return client.sql("SELECT * FROM durable_state WHERE persistence_id = :persistenceId AND revision = :revision")
            .bind("persistenceId", persistenceId)
            .bind("revision", revision)
            .map { row, _ ->
                DurableState(
                    row.get("slice", Int::class.java)!!,
                    row.get("entity_type", String::class.java)!!,
                    row.get("persistence_id", String::class.java)!!,
                    row.get("revision", Long::class.java)!!,
                    row.get("db_timestamp", java.time.LocalDateTime::class.java)!!,
                    row.get("state_ser_id", Int::class.java)!!,
                    row.get("state_ser_manifest", String::class.java)!!,
                    row.get("state_payload", ByteArray::class.java)!!,
                    row.get("tags", String::class.java)!!
                )
            }
            .one()
            .switchIfEmpty(Mono.justOrEmpty(null))
    }

    override fun create(durableState: DurableState): Mono<DurableState> {
        return client.sql("INSERT INTO durable_state (slice, entity_type, persistence_id, revision, db_timestamp, state_ser_id, state_ser_manifest, state_payload, tags) VALUES (:slice, :entityType, :persistenceId, :revision, :dbTimestamp, :stateSerId, :stateSerManifest, :statePayload, :tags)")
            .bind("slice", durableState.slice)
            .bind("entityType", durableState.entityType)
            .bind("persistenceId", durableState.persistenceId)
            .bind("revision", durableState.revision)
            .bind("dbTimestamp", durableState.dbTimestamp)
            .bind("stateSerId", durableState.stateSerId)
            .bind("stateSerManifest", durableState.stateSerManifest)
            .bind("statePayload", durableState.statePayload)
            .bind("tags", durableState.tags)
            .then()
            .thenReturn(durableState)
    }

    override fun update(durableState: DurableState): Mono<DurableState> {
        return client.sql("UPDATE durable_state SET slice = :slice, entity_type = :entityType, db_timestamp = :dbTimestamp, state_ser_id = :stateSerId, state_ser_manifest = :stateSerManifest, state_payload = :statePayload, tags = :tags WHERE persistence_id = :persistenceId AND revision = :revision")
            .bind("slice", durableState.slice)
            .bind("entityType", durableState.entityType)
            .bind("dbTimestamp", durableState.dbTimestamp)
            .bind("stateSerId", durableState.stateSerId)
            .bind("stateSerManifest", durableState.stateSerManifest)
            .bind("statePayload", durableState.statePayload)
            .bind("tags", durableState.tags)
            .bind("persistenceId", durableState.persistenceId)
            .bind("revision", durableState.revision)
            .then()
            .thenReturn(durableState)
    }

    override fun createOrUpdate(durableState: DurableState): Mono<DurableState> {
        return findById(durableState.persistenceId, durableState.revision)
            .flatMap { _ ->
                update(durableState).thenReturn(durableState)
            }
            .switchIfEmpty(create(durableState).thenReturn(durableState))
    }

    override fun deleteById(persistenceId: String, revision: Long): Mono<Void> {
        return client.sql("DELETE FROM durable_state WHERE persistence_id = :persistenceId AND revision = :revision")
            .bind("persistenceId", persistenceId)
            .bind("revision", revision)
            .then()
    }
}