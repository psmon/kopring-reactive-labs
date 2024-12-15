package org.example.kotlinbootreactivelabs.repositories.durable

import org.example.kotlinbootreactivelabs.repositories.durable.dao.DurableStateDAO
import org.example.kotlinbootreactivelabs.repositories.durable.dto.DurableState
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
class DurableRepository(private val durableStateDAO: DurableStateDAO) {

    fun getAllDurableStates(): Flux<DurableState> {
        return durableStateDAO.findAll()
    }

    fun getDurableStateById(persistenceId: String, revision: Long): Mono<DurableState> {
        return durableStateDAO.findById(persistenceId, revision)
    }

    fun createDurableState(durableState: DurableState): Mono<DurableState> {
        return durableStateDAO.create(durableState)
    }

    fun updateDurableState(durableState: DurableState): Mono<DurableState> {
        return durableStateDAO.update(durableState)
    }

    fun createOrUpdateDurableState(durableState: DurableState) : Mono<DurableState>{
        return durableStateDAO.createOrUpdate(durableState)
    }

    fun deleteDurableStateById(persistenceId: String, revision: Long): Mono<Void> {
        return durableStateDAO.deleteById(persistenceId, revision)
    }

    fun<T: Any> createOrUpdateDurableStateEx(persistenceId: String, revision: Long, entity: T) : Mono<T> {
       return durableStateDAO.createOrUpdateEx(persistenceId, revision, entity)
    }

    fun <T> findByIdEx(persistenceId: String, revision: Long): Mono<T>{
        return durableStateDAO.findByIdEx(persistenceId, revision)
    }
}