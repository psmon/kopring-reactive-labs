package org.example.kotlinbootreactivelabs.actor.core

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration

class HelloWorldMainTest {

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
    fun testHelloWorldMain() {
        val probe: TestProbe<Any> = testKit.createTestProbe()
        val helloWorldMain: ActorRef<HelloWorldMainCommand> = testKit.spawn(HelloWorldMain.create())

        helloWorldMain.tell(SayHello("testActor", probe.ref))
        probe.expectMessage("World")
    }

    @Test
    fun testGracefulDownHelloWorldMain() {
        val probe: TestProbe<Any> = testKit.createTestProbe()
        val helloWorldMain: ActorRef<HelloWorldMainCommand> = testKit.spawn(HelloWorldMain.create())

        helloWorldMain.tell(SayHello("testActor1", probe.ref))
        probe.expectMessage("World")

        helloWorldMain.tell(SayHello("testActor2", probe.ref))
        probe.expectMessage("World")

        helloWorldMain.tell(SayHello("testActor3", probe.ref))
        probe.expectMessage("World")

        helloWorldMain.tell(GracefulShutdown)

        println("Waiting for the actor to stop gracefully")

        probe.expectNoMessage(Duration.ofSeconds(3))

    }
}