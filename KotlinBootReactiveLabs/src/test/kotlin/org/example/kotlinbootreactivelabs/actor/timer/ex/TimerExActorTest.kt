package org.example.kotlinbootreactivelabs.actor.timer.ex

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.ManualTime
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class TimerExActorTest {

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
    fun testTaskLifecycle() {
        val probe: TestProbe<TimerActorCommand> = testKit.createTestProbe()
        val timerActor = testKit.spawn(TimerExActor.create())

        var timestamp: Instant = Instant.now()

        // Assign a task
        timerActor.tell(AssignTask("task1", timestamp))

        timestamp = timestamp.plusSeconds(5)
        manualTime.timePasses(Duration.ofSeconds(5))

        // Accept the task
        timerActor.tell(AcceptTask("task1", timestamp))

        timestamp = timestamp.plusSeconds(7)
        manualTime.timePasses(Duration.ofSeconds(7))

        // Complete the task
        timerActor.tell(CompleteTask("task1",timestamp))

        // Check monitor status
        timerActor.tell(CheckMonitor(timestamp))
        probe.awaitAssert {
            probe.expectNoMessage(Duration.ofSeconds(3))
        }
    }

    @Test
    fun testManyTaskLifecycle() {
        val probe: TestProbe<TimerActorCommand> = testKit.createTestProbe()
        val timerActor = testKit.spawn(TimerExActor.create())

        var timestamp: Instant = Instant.now()

        // Assign 30 tasks
        for (i in 1..30) {
            timestamp = timestamp.plusSeconds(1)
            manualTime.timePasses(Duration.ofSeconds(1))

            timerActor.tell(AssignTask("task$i", timestamp))
        }

        // Accept half of the tasks
        for (i in 1..15) {
            timerActor.tell(AcceptTask("task$i", timestamp))
            manualTime.timePasses(Duration.ofSeconds(1))

            timestamp = timestamp.plusSeconds(1)
        }

        // RejectTask some of the assigned tasks
        for (i in 20..25) {
            timerActor.tell(RejectTask("task$i", timestamp))

            manualTime.timePasses(Duration.ofSeconds(1))
            timestamp = timestamp.plusSeconds(1)
        }

        // Complete some of the accepted tasks
        for (i in 1..10) {
            timerActor.tell(CompleteTask("task$i", timestamp))
            manualTime.timePasses(Duration.ofSeconds(1))

            timestamp = timestamp.plusSeconds(1)
        }

        // Check monitor status
        timerActor.tell(CheckMonitor(timestamp))

        probe.awaitAssert {
            probe.expectNoMessage(Duration.ofSeconds(3))
        }
    }

}