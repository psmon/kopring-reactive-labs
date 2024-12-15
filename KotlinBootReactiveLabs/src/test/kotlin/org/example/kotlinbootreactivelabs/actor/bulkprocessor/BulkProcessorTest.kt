package org.example.kotlinbootreactivelabs.actor.bulkprocessor

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.ManualTime
import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration

class BulkProcessorTest {

    companion object {
        private lateinit var testKit: ActorTestKit
        private lateinit var manualTime: ManualTime

        @BeforeAll
        @JvmStatic
        fun setup() {
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
    fun bulkInsertTest() {
        val probe = testKit.createTestProbe<Any>()
        val helloStateActor = testKit.spawn(BulkProcessor.create())

        // Manual Flush Test
        helloStateActor.tell(DataEvent("data1", probe.ref()))
        helloStateActor.tell(DataEvent("data2", probe.ref()))
        helloStateActor.tell(DataEvent("data3", probe.ref()))
        helloStateActor.tell(Flush)

        // Auto Flush Test over 100 , Send 110 DataEvent messages
        for (i in 1..110) {
            helloStateActor.tell(DataEvent("data$i", probe.ref()))
        }

        // Auto Flush Test TimeOut 3 seconds
        helloStateActor.tell(DataEvent("data1", probe.ref()))
        helloStateActor.tell(DataEvent("data2", probe.ref()))
        helloStateActor.tell(DataEvent("data3", probe.ref()))
        helloStateActor.tell(DataEvent("data1", probe.ref()))
        helloStateActor.tell(DataEvent("data2", probe.ref()))
        helloStateActor.tell(DataEvent("data3", probe.ref()))

        //Thread.sleep(Duration.ofSeconds(5).toMillis())
        manualTime.timePasses(Duration.ofSeconds(5))

        helloStateActor.tell(DataEvent("testend", probe.ref()))

        probe.expectMessage(BulkTaskCompleted("Bulk task completed"))
    }

}