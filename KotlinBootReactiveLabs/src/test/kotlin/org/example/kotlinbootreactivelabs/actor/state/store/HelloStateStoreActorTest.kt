package org.example.kotlinbootreactivelabs.actor.state.store

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.example.kotlinbootreactivelabs.repositories.durable.DurableRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import kotlin.test.assertEquals


@ExtendWith(SpringExtension::class)
@DataR2dbcTest
@ActiveProfiles("test")
@ComponentScan(basePackages = ["org.example.kotlinbootreactivelabs.repositories.durable"])
class HelloStateStoreActorTest {

    @Autowired
    lateinit var durableRepository: DurableRepository

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
    fun testHelloStore() {
        val probe: TestProbe<Any> = testKit.createTestProbe()
        val helloStateStoreActor = testKit.spawn(HelloStateStoreActor.create("test-persistence-id", durableRepository))

        helloStateStoreActor.tell(ChangeStateStore(HappyState.HAPPY))
        helloStateStoreActor.tell(HelloStore("Hello", probe.ref()))
        probe.expectMessage(HelloResponseStore("Kotlin"))

    }

    @Test
    fun testChangeState() {
        val probe: TestProbe<Any> = testKit.createTestProbe()
        val helloStateStoreActor = testKit.spawn(HelloStateStoreActor.create("test-persistence-id", durableRepository))

        helloStateStoreActor.tell(ChangeStateStore(HappyState.ANGRY))
        helloStateStoreActor.tell(HelloStore("Hello", probe.ref()))
        probe.expectMessage(HelloResponseStore("Don't talk to me!"))
    }

    @Test
    fun testResetHelloCount()  {

        var testId: String = "test-persistence-id-test-1"
        var expectedCount: Int = 2

        val probe: TestProbe<Any> = testKit.createTestProbe()
        val helloStateStoreActor = testKit.spawn(HelloStateStoreActor.create(testId, durableRepository))

        helloStateStoreActor.tell(ChangeStateStore(HappyState.HAPPY))
        helloStateStoreActor.tell(ResetHelloCountStore)

        helloStateStoreActor.tell(GetHelloCountStore(probe.ref()))
        probe.expectMessage(HelloCountResponseStore(0))

        helloStateStoreActor.tell(HelloStore("Hello", probe.ref()))
        helloStateStoreActor.tell(HelloStore("Hello", probe.ref()))

        probe.expectMessage(HelloResponseStore("Kotlin"))
        probe.expectMessage(HelloResponseStore("Kotlin"))

        for(i in 1..1000) {
            helloStateStoreActor.tell(GetHelloCountStore(probe.ref()))
        }

        for(i in 1..1000) {
            probe.expectMessage(HelloCountResponseStore(2))
        }

        // Read Count From Actor Model
        helloStateStoreActor.tell(GetHelloCountStore(probe.ref()))
        probe.expectMessage(HelloCountResponseStore(expectedCount))

        // Read Count From Db , Wait for the write to complete
        Thread.sleep(1000)

        durableRepository.findByIdEx<HelloStoreState>(testId ,1L).block()?.let {
            assertEquals(expectedCount, it.helloCount)
        }

    }
}