package org.example.kotlinbootreactivelabs.actor.timer

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.example.kotlinbootreactivelabs.actor.timer.basic.TimerActor
import org.example.kotlinbootreactivelabs.actor.timer.basic.TimerActorCommand
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration

class TimerActorLoadTest {

    companion object {
        private lateinit var testKit: ActorTestKit

        @BeforeAll
        @JvmStatic
        fun setup() {
            // Setup code if needed
            testKit = ActorTestKit.create()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun testTimerLoop() {
        val probe = testKit.createTestProbe<TimerActorCommand>()
        val testCount: Int = 10000

        for(i in 1..testCount) {
            val timerActor = testKit.spawn(TimerActor.create())
        }

        probe.awaitAssert {
            probe.expectNoMessage(Duration.ofSeconds(9))
        }
    }
}