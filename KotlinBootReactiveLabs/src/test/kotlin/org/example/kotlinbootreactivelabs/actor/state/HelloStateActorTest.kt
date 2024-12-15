package org.example.kotlinbootreactivelabs.actor.state

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.ManualTime
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.javadsl.Source
import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration

class HelloStateActorTest {

    companion object {
        private lateinit var testKit: ActorTestKit
        private lateinit var manualTime: ManualTime
        private lateinit var materializer: Materializer

        @BeforeAll
        @JvmStatic
        fun setup() {
            val config = ManualTime.config().withFallback(ConfigFactory.defaultApplication())
            testKit = ActorTestKit.create(config)
            manualTime = ManualTime.get(testKit.system())

            val newSystem = ActorSystem.create()
            materializer = Materializer.createMaterializer(newSystem)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            materializer.shutdown()
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun testHelloStateActorRespondsBasedOnState() {
        val probe = testKit.createTestProbe<Any>()

        val helloHelloStateActor = testKit.spawn(HelloStateActor.create(HelloState.HAPPY))

        // Test in HAPPY state
        helloHelloStateActor.tell(Hello("Hello", probe.ref()))
        probe.expectMessage(HelloResponse("Kotlin"))

        helloHelloStateActor.tell(GetHelloTotalCount(probe.ref()))
        probe.expectMessage(HelloCountResponse(1))

        // Change state to ANGRY
        helloHelloStateActor.tell(ChangeState(HelloState.ANGRY))

        // Test in ANGRY state
        helloHelloStateActor.tell(Hello("Hello", probe.ref()))
        probe.expectMessage(HelloResponse("Don't talk to me!"))

        helloHelloStateActor.tell(GetHelloTotalCount(probe.ref()))
        probe.expectMessage(HelloCountResponse(1)) // Count should not change
    }

    @Test
    fun testHelloLimitCommand() {
        val probe = testKit.createTestProbe<Any>()
        val helloHelloStateActor = testKit.spawn(HelloStateActor.create(HelloState.HAPPY))

        helloHelloStateActor.tell(StopResetTimer)

        val helloLimitSource = Source.queue<HelloLimit>(100, OverflowStrategy.backpressure())
            .throttle(3, Duration.ofSeconds(1))
            .to(Sink.foreach { cmd ->
                helloHelloStateActor.tell(Hello(cmd.message, cmd.replyTo))
            })
            .run(materializer)

        // Send 100 HelloLimit messages
        val startTime = System.currentTimeMillis()

        // Send 100 HelloLimit messages
        for (i in 1..100) {
            // # Actor에 Throllle탑재(A) vs Throttle을 외부로 분리(B)
            // # A방식
            // manualTime Test주입으로 AkkaStream-Throttle을 테스트하기 어려움, B방식으로 테스트가능
            //helloStateActor.tell(HelloLimit("Hello", probe.ref()))
            // # B방식
            helloLimitSource.offer(HelloLimit("Hello", probe.ref()))
        }

        // Expect 100 responses with increased timeout
        for (i in 1..100) {
            probe.expectMessage(Duration.ofSeconds(3), HelloResponse("Kotlin"))
        }

        val endTime = System.currentTimeMillis()

        // Calculate TPS
        val durationInSeconds = (endTime - startTime) / 1000.0
        val tps = 100 / durationInSeconds
        println("TPS: $tps")

        assert(tps > 2.0 && tps < 4.0)

        // Verify the hello count
        helloHelloStateActor.tell(GetHelloTotalCount(probe.ref()))
        probe.expectMessage(HelloCountResponse(100))
    }

    @Test
    fun testResetHelloCount() {
        val probe = testKit.createTestProbe<Any>()
        val helloHelloStateActor = testKit.spawn(HelloStateActor.create(HelloState.HAPPY))

        // Send Hello messages
        helloHelloStateActor.tell(Hello("Hello", probe.ref()))
        helloHelloStateActor.tell(Hello("Hello", probe.ref()))

        probe.expectMessage(HelloResponse("Kotlin"))
        probe.expectMessage(HelloResponse("Kotlin"))

        // Verify the hello count
        helloHelloStateActor.tell(GetHelloCount(probe.ref()))
        probe.expectMessage(HelloCountResponse(2))

        // Wait for the timer to reset the count
        //Thread.sleep(Duration.ofSeconds(11).toMillis())

        // Advance the time by 5 seconds
        manualTime.timePasses(Duration.ofSeconds(5))
        helloHelloStateActor.tell(GetHelloCount(probe.ref()))
        probe.expectMessage(HelloCountResponse(2))

        // Advance the time by 11 seconds
        manualTime.timePasses(Duration.ofSeconds(6))

        // Verify the hello count is reset
        helloHelloStateActor.tell(GetHelloCount(probe.ref()))
        probe.expectMessage(HelloCountResponse(0))
    }
}