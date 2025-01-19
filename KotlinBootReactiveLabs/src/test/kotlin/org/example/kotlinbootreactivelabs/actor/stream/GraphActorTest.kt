package org.example.kotlinbootreactivelabs.actor.stream

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class GraphActorTest {

    companion object {
        private val testKit = ActorTestKit.create()

        @BeforeAll
        @JvmStatic
        fun setup() {
            // Setup code if needed
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun testProcessNumberAdd() {
        val probe: TestProbe<GraphCommand> = testKit.createTestProbe()
        val graphActor: ActorRef<GraphCommand> = testKit.spawn(GraphActor.create())

        graphActor.tell(ProcessNumber(5, probe.ref))
        val response = probe.receiveMessage() as ProcessedNumber
        assertEquals(6, response.result)
    }

    @Test
    fun testProcessNumberMultiply() {
        val probe: TestProbe<GraphCommand> = testKit.createTestProbe()
        val graphActor: ActorRef<GraphCommand> = testKit.spawn(GraphActor.create())

        graphActor.tell(SwitchToMultiply)
        graphActor.tell(ProcessNumber(5, probe.ref))
        val response = probe.receiveMessage() as ProcessedNumber
        assertEquals(10, response.result)
    }

    @Test
    fun testSwitchOperations() {
        val probe: TestProbe<GraphCommand> = testKit.createTestProbe()
        val graphActor: ActorRef<GraphCommand> = testKit.spawn(GraphActor.create())

        graphActor.tell(ProcessNumber(5, probe.ref))
        var response = probe.receiveMessage() as ProcessedNumber
        assertEquals(6, response.result)

        graphActor.tell(SwitchToMultiply)
        graphActor.tell(ProcessNumber(5, probe.ref))
        response = probe.receiveMessage() as ProcessedNumber
        assertEquals(10, response.result)

        graphActor.tell(SwitchToAdd)
        graphActor.tell(ProcessNumber(5, probe.ref))
        response = probe.receiveMessage() as ProcessedNumber
        assertEquals(6, response.result)
    }
}