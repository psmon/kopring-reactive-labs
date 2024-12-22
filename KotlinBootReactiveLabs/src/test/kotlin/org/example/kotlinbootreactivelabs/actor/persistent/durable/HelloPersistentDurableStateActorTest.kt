package org.example.kotlinbootreactivelabs.actor.persistent.durable

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.apache.pekko.persistence.typed.PersistenceId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

// Warning: 이 액터는 akka 2.7에서 작동확인이 되었으나
// r2dbc 의존성때문에 pekko 1.1.2(akka 2.6.16)에서는 작동하지 않을수 있습니다.
// 다음 플러그인 의존성 확인필요 ( 1.0.0 )
// https://pekko.apache.org/docs/pekko-persistence-r2dbc/current/


class HelloPersistentDurableStateActorTest {

    companion object {

        private lateinit var testKit: ActorTestKit

        @BeforeAll
        @JvmStatic
        fun setup() {
            testKit = ActorTestKit.create()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun testHelloPersistentStateActorRespondsBasedOnState() {

        val probe: TestProbe<Any> = testKit.createTestProbe()
        val persistenceId = PersistenceId.ofUniqueId("HelloPersistentStateActor1")
        val helloPersistentDurableStateActor = testKit.spawn(HelloPersistentDurableStateActor.create(persistenceId))

        helloPersistentDurableStateActor.tell(GetHelloCountPersistentDurable(probe.ref()))

        val response = probe.expectMessageClass(HelloCountResponse::class.java)

        val totalCount: Number = response.count

        // Test in HAPPY state
        helloPersistentDurableStateActor.tell(ChangeState(State.HAPPY))
        helloPersistentDurableStateActor.tell(HelloPersistentDurable("Hello", probe.ref()))
        probe.expectMessage(HelloResponse("Kotlin"))

        val increaseCount :Number = totalCount.toInt() + 1

        helloPersistentDurableStateActor.tell(GetHelloCountPersistentDurable(probe.ref()))

        val updatedResponse = probe.expectMessageClass(HelloCountResponse::class.java)
        assertEquals(increaseCount.toInt(), updatedResponse.count.toInt())

        // Change state to ANGRY
        helloPersistentDurableStateActor.tell(ChangeState(State.ANGRY))

        // Test in ANGRY state
        helloPersistentDurableStateActor.tell(HelloPersistentDurable("Hello", probe.ref()))
        probe.expectMessage(HelloResponse("Don't talk to me!"))

        helloPersistentDurableStateActor.tell(GetHelloCountPersistentDurable(probe.ref()))

        // Count should not increase
        val updatedResponse2 = probe.expectMessageClass(HelloCountResponse::class.java)
        assertEquals(increaseCount.toInt(), updatedResponse2.count.toInt())

    }


    @ParameterizedTest
    @ValueSource(ints = [1000])
    fun testHelloPersistentStateActorRespondsBasedOnStatePerformTest(testCount: Int) {

        val probe: TestProbe<Any> = testKit.createTestProbe()
        val persistenceId = PersistenceId.ofUniqueId("HelloPersistentStateActor1")
        val helloPersistentDurableStateActor = testKit.spawn(HelloPersistentDurableStateActor.create(persistenceId))

        helloPersistentDurableStateActor.tell(GetHelloCountPersistentDurable(probe.ref()))

        val response = probe.expectMessageClass(HelloCountResponse::class.java)

        val totalCount: Number = response.count

        // Test in HAPPY state
        helloPersistentDurableStateActor.tell(ChangeState(State.HAPPY))

        for (i in 1..testCount) {
            helloPersistentDurableStateActor.tell(HelloPersistentDurable("Hello", probe.ref()))
        }

        for (i in 1..testCount) {
            probe.expectMessage(HelloResponse("Kotlin"))
        }

        //증가 카운트 검증
        val increaseCount :Number = totalCount.toInt() + testCount
        helloPersistentDurableStateActor.tell(GetHelloCountPersistentDurable(probe.ref()))
        val updatedResponse = probe.expectMessageClass(HelloCountResponse::class.java)
        assertEquals(increaseCount.toInt(), updatedResponse.count.toInt())

    }

    @Test
    fun testResetHelloCount() {
        val probe: TestProbe<Any> = testKit.createTestProbe()
        val persistenceId = PersistenceId.ofUniqueId("HelloPersistentStateActor2")
        val helloPersistentDurableStateActor = testKit.spawn(HelloPersistentDurableStateActor.create(persistenceId))

        helloPersistentDurableStateActor.tell(GetHelloCountPersistentDurable(probe.ref()))
        val response = probe.expectMessageClass(HelloCountResponse::class.java)
        val totalCount: Number = response.count

        // Send Hello messages
        helloPersistentDurableStateActor.tell(HelloPersistentDurable("Hello", probe.ref()))
        helloPersistentDurableStateActor.tell(HelloPersistentDurable("Hello", probe.ref()))

        probe.expectMessage(HelloResponse("Kotlin"))
        probe.expectMessage(HelloResponse("Kotlin"))

        val increaseCount :Number = totalCount.toInt() + 2

        // Verify the hello count
        helloPersistentDurableStateActor.tell(GetHelloCountPersistentDurable(probe.ref()))
        val updatedResponse = probe.expectMessageClass(HelloCountResponse::class.java)
        assertEquals(increaseCount.toInt(), updatedResponse.count.toInt())

        // Reset the hello count
        helloPersistentDurableStateActor.tell(ResetHelloCount)

        // Verify the hello count is reset
        helloPersistentDurableStateActor.tell(GetHelloCountPersistentDurable(probe.ref()))
        val resetResponse = probe.expectMessageClass(HelloCountResponse::class.java)
        assertEquals(0, resetResponse.count.toInt())
    }

    @ParameterizedTest
    @ValueSource(ints = [10] )
    fun testHelloPersistentStateActorConcurrentUsers(testCount: Int) {
        val probe: TestProbe<Any> = testKit.createTestProbe()

        val testUser: Int = 1000

        val totalTestCount : Int = testCount * testUser

        val actors = (1..testUser).map { i ->
            val persistenceId = PersistenceId.ofUniqueId("HelloPersistentStateActor$i")
            testKit.spawn(HelloPersistentDurableStateActor.create(persistenceId))
        }

        val startTime = System.currentTimeMillis()

        actors.forEach { actor ->
            actor.tell(GetHelloCountPersistentDurable(probe.ref()))
            val response = probe.expectMessageClass(HelloCountResponse::class.java)
            val totalCount: Number = response.count

            // Test in HAPPY state
            actor.tell(ChangeState(State.HAPPY))

            for (i in 1..testCount) {
                actor.tell(HelloPersistentDurable("Hello", probe.ref()))
            }

            for (i in 1..testCount) {
                probe.expectMessage(HelloResponse("Kotlin"))
            }

            // Verify the hello count
            val increaseCount: Number = totalCount.toInt() + testCount
            actor.tell(GetHelloCountPersistentDurable(probe.ref()))
            val updatedResponse = probe.expectMessageClass(HelloCountResponse::class.java)
            assertEquals(increaseCount.toInt(), updatedResponse.count.toInt())
        }

        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        val averageTimePerIteration = totalTime.toDouble() / totalTestCount

        println("Total execution time: $totalTime ms")
        println("Average execution time per iteration: $averageTimePerIteration ms")
    }
}