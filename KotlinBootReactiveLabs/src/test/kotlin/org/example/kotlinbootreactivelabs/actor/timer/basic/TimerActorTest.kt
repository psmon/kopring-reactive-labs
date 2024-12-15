package org.example.kotlinbootreactivelabs.actor.timer.basic

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.ManualTime
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration

class TimerActorTest {

    companion object {
        private lateinit var testKit: ActorTestKit
        private lateinit var manualTime: ManualTime

        @BeforeAll
        @JvmStatic
        fun setup() {
            // Setup code if needed
            val config = ManualTime.config().withFallback(ConfigFactory.defaultApplication())
            testKit = ActorTestKit.create(config)
            manualTime = ManualTime.get(testKit.system())
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
        val timerActor = testKit.spawn(TimerActor.Companion.create())

        manualTime.timePasses(Duration.ofSeconds(10))

        probe.awaitAssert {
            probe.expectNoMessage(Duration.ofSeconds(3))
        }
    }

    @Test
    fun testTimerStop() {
        val probe = testKit.createTestProbe<TimerActorCommand>()
        val timerActor = testKit.spawn(TimerActor.Companion.create())

        manualTime.timePasses(Duration.ofSeconds(5))
        timerActor.tell(TimerStop)

        probe.awaitAssert {
            probe.expectNoMessage(Duration.ofSeconds(3))
        }
    }

    @Test
    fun testTimerResume() {
        val probe = testKit.createTestProbe<TimerActorCommand>()
        val timerActor = testKit.spawn(TimerActor.Companion.create())

        manualTime.timePasses(Duration.ofSeconds(5))
        timerActor.tell(TimerStop)
        probe.awaitAssert {
            probe.expectNoMessage(Duration.ofSeconds(3))
        }

        manualTime.timePasses(Duration.ofSeconds(3))
        timerActor.tell(TimerResume)
        probe.awaitAssert {
            probe.expectNoMessage(Duration.ofSeconds(3))
        }

        manualTime.timePasses(Duration.ofSeconds(10))
        probe.awaitAssert {
            probe.expectNoMessage(Duration.ofSeconds(3))
        }
    }

}