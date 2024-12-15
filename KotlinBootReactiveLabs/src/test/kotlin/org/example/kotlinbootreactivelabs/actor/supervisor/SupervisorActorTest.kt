package org.example.kotlinbootreactivelabs.actor.supervisor

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.example.kotlinbootreactivelabs.actor.state.store.HelloResponseStore
import org.example.kotlinbootreactivelabs.repositories.durable.DurableRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@DataR2dbcTest
@ActiveProfiles("test")
@ComponentScan(basePackages = ["org.example.kotlinbootreactivelabs.repositories.durable"])
class SupervisorActorTest {

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
    fun testCreateChild() {
        val supervisor = testKit.spawn(SupervisorActor.create(durableRepository))
        val probe = testKit.createTestProbe<Any>()
        var probeChild = testKit.createTestProbe<Any>()

        supervisor.tell(CreateChild("child1", "persistId1", probeChild.ref))

        supervisor.tell(SendHello("child1", "Hello", probe.ref()))

        val response = probe.expectMessageClass(HelloResponseStore::class.java)

        println("response: ${response.message}")

    }

    @Test
    fun testGetChildCount() {
        val supervisor = testKit.spawn(SupervisorActor.create(durableRepository))
        val probe = testKit.createTestProbe<Int>()
        var probeChild = testKit.createTestProbe<Any>()

        supervisor.tell(CreateChild("child1", "persistId1", probeChild.ref))
        supervisor.tell(CreateChild("child2", "persistId2", probeChild.ref))
        supervisor.tell(GetChildCount(probe.ref()))

        val childCount = probe.receiveMessage()
        assertEquals(2, childCount)
    }

    @Test
    fun testTerminateChild() {
        val supervisor = testKit.spawn(SupervisorActor.create(durableRepository))
        val probe = testKit.createTestProbe<Int>()
        var probeChild = testKit.createTestProbe<Any>()

        supervisor.tell(CreateChild("child1", "persistId1", probeChild.ref))
        supervisor.tell(TerminateChild("child1"))

        // Wait for child to terminate , CoordinatedShutdown
        Thread.sleep(3000)

        supervisor.tell(GetChildCount(probe.ref()))
        val childCount = probe.receiveMessage()
        assertEquals(0, childCount)
    }

    @Test
    fun testCrashChildAndCount() {
        val supervisor = testKit.spawn(SupervisorActor.create(durableRepository))
        val probeCount = testKit.createTestProbe<Int>()
        val probeHello = testKit.createTestProbe<Any>()
        var probeChild = testKit.createTestProbe<Any>()

        supervisor.tell(CreateChild("child1", "persistId1", probeChild.ref))
        supervisor.tell(CreateChild("child2", "persistId2", probeChild.ref))

        supervisor.tell(SendHello("child2", "Hello", probeHello.ref()))
        val response = probeHello.expectMessageClass(HelloResponseStore::class.java)
        println("response: ${response.message}")

        // Wait for asyncWrite to complete
        Thread.sleep(1000)

        supervisor.tell(SendHello("child2", "Crash", probeHello.ref()))

        // Wait for child to terminate , CoordinatedShutdown
        Thread.sleep(1000)

        supervisor.tell(SendHello("child2", "Hello", probeHello.ref()))
        val response2 = probeHello.expectMessageClass(HelloResponseStore::class.java)
        println("response: ${response2.message}")

        supervisor.tell(GetChildCount(probeCount.ref()))
        val childCount = probeCount.receiveMessage()
        assertEquals(2, childCount)
    }
}