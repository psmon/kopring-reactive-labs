package org.example.kotlinbootreactivelabs.actor.core

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class HelloActorTest {

    companion object {
        private val testKit = ActorTestKit.create()

        @BeforeAll
        @JvmStatic
        fun setup() {
            // Setup code if needed
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun testHelloActor() {
        val helloActor: ActorRef<HelloCommand> = testKit.spawn(HelloActor.create(), "hello-actor")
        val probe: TestProbe<HelloCommand> = testKit.createTestProbe()

        helloActor.tell(Hello("Hello", probe.ref))
        probe.expectMessage(HelloResponse("Kotlin"))
    }
}