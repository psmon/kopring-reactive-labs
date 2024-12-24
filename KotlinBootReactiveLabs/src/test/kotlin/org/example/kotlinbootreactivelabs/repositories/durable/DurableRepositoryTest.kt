package org.example.kotlinbootreactivelabs.repositories.durable

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.example.kotlinbootreactivelabs.actor.state.model.HelloStoreState

import org.example.kotlinbootreactivelabs.repositories.durable.dto.DurableState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.LocalDateTime

@ExtendWith(SpringExtension::class)
@DataR2dbcTest
@ActiveProfiles("test")
@ComponentScan(basePackages = ["org.example.kotlinbootreactivelabs.repositories.durable"])
class DurableRepositoryTest {

    @Autowired
    lateinit var durableRepository: DurableRepository

    @BeforeEach
    fun setUp() {
    }

    @Test
    fun testFindByIdEx() = runBlocking {

        val result = durableRepository.findByIdEx<HelloStoreState>("test-id-xxxx", 1L).awaitFirstOrNull()

        assertNull(result)

    }

    @Test
    fun `test create and find durable state`() = runBlocking {

        val testId: String = "test-id-01"
        val testSlice: Int = 1
        val testRevision: Long = 1L
        val testStateSerId: Int = 1

        val durableState = DurableState(
            slice = testSlice,
            entityType = "TestEntity",
            persistenceId = testId,
            revision = testRevision,
            dbTimestamp = LocalDateTime.now(),
            stateSerId = testStateSerId,
            stateSerManifest = "manifest",
            statePayload = byteArrayOf(1, 2, 3),
            tags = "tag1,tag2"
        )

        var retrievedState = durableRepository.createOrUpdateDurableState(durableState).awaitSingle()

        assertEquals(testId, retrievedState.persistenceId)
        assertEquals(testRevision, retrievedState.revision)
        assertEquals(testSlice, retrievedState.slice)
        assertEquals("TestEntity", retrievedState.entityType)
        assertEquals("manifest", retrievedState.stateSerManifest)
        assertArrayEquals(byteArrayOf(1, 2, 3), retrievedState.statePayload)
        assertEquals("tag1,tag2", retrievedState.tags)
    }

    @Test
    fun `test createOrUpdateEx`() = runBlocking {
        val testId: String = "test-id-02"
        val testRevision: Long = 1L

        // Define a test entity
        data class TestEntity(val name: String, val value: Int)

        val testEntity = TestEntity("TestName", 123)

        // Create or update the entity
        val resultEntity = durableRepository.createOrUpdateDurableStateEx<TestEntity>(testId, testRevision, testEntity).awaitSingle()

        // Verify the result
        assertEquals(testEntity.name, resultEntity.name)
        assertEquals(testEntity.value, resultEntity.value)

        // Retrieve the entity to verify it was saved correctly
        val retrievedEntity = durableRepository.findByIdEx<TestEntity>(testId, testRevision).awaitSingle()

        // Verify the retrieved entity
        assertEquals(testEntity.name, retrievedEntity.name)
        assertEquals(testEntity.value, retrievedEntity.value)
    }

}