package org.example.kotlinbootreactivelabs.repositories.durable.dao
import org.example.kotlinbootreactivelabs.repositories.durable.dto.DurableState
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface DurableStateDAO {

    // Pure Version
    fun findAll(): Flux<DurableState>
    fun findById(persistenceId: String, revision: Long): Mono<DurableState>
    fun create(durableState: DurableState): Mono<DurableState>
    fun update(durableState: DurableState): Mono<DurableState>
    fun createOrUpdate(durableState: DurableState): Mono<DurableState>
    fun deleteById(persistenceId: String, revision: Long): Mono<Void>

    // Template Version for Actor
    fun <T> findByIdEx(persistenceId: String, revision: Long): Mono<T>
    fun <T: Any> createOrUpdateEx(persistenceId: String, revision: Long, entity: T): Mono<T>

}