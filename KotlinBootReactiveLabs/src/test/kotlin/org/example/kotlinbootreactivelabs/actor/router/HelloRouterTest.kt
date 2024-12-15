package org.example.kotlinbootreactivelabs.actor.router

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.example.kotlinbootreactivelabs.actor.state.HelloResponse
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class HelloRouterTest {

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
    fun testDistributedHello() {
        val routerCount = 5
        val testCount = routerCount * 10
        val router: ActorRef<HelloRouterCommand> = testKit.spawn(HelloRouter.create(routerCount), "hello-router")
        val probe: TestProbe<Any> = testKit.createTestProbe()

        for(i in 1..routerCount * testCount) {
            router.tell(DistributedHello("Hello", probe.ref))
        }

        for(i in 1..routerCount * testCount) {
            probe.expectMessage(HelloResponse("Kotlin"))
        }
    }
}