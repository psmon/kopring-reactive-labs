package org.example.kotlinbootreactivelabs.actor.discovery

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Test for the Discovery actor.
 * Link : https://pekko.apache.org/docs/pekko/1.0/typed/actor-discovery.html
 * 개요 : 로컬에서만 액터를 사용시 액터참조(메모리)로 충분하지만, 분산환경에서 원격지의 액터를 찾으려면
 *  종속주입(DI)을 이용한 방식으로는 참조자를 찾는것이 불가할수 있습니다.
 *  클래식 Untyped 액터를 사용하는 경우 주소방식을 이용할수 있지만
 *  Typed 액터의 경우 Receptionist를 이용하여 액터를 찾을수있으며 동일방법으로 클러스터로 확장할수 있습니다.
 */

class DiscoveryTest {

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
    fun testPingerActor() {
        val pingerService = testKit.createTestProbe<PingServiceActorCommand>()

        val pingerActor: ActorRef<PingServiceActorCommand> = testKit.spawn(PingerActor.create(pingerService.ref))
        val pingerActor2: ActorRef<PingServiceActorCommand> = testKit.spawn(PingerActor.create(pingerService.ref))

        pingerService.expectMessage(Ping("Ping", pingerActor))

        pingerService.expectMessage(Ping("Ping", pingerActor2))

        pingerActor.tell(Pong("Pong"))

        pingerActor2.tell(Pong("Pong"))

        pingerService.expectTerminated(pingerActor)

        pingerService.expectTerminated(pingerActor2)
    }

    @Test
    fun testPingManagerActor(){

        val testProbe = testKit.createTestProbe<PingManagerActorResponse>()
        val pingManager = testKit.spawn(PingManagerActor.create())

        pingManager.tell(PingAll("Hello", testProbe.ref()))

        testProbe.expectMessage(PingAllResponse("Pinging all"))
    }

    @Test
    fun testGuardianActor(){

        val guardian = testKit.spawn(GuardianActor.create())

        val testProbe = testKit.createTestProbe<Any>()

        val pingerService = testKit.spawn(PingServiceActor.create())

        val pingerActor: ActorRef<PingServiceActorCommand> = testKit.spawn(PingerActor.create(pingerService))

        guardian.tell(KeepAlive(testProbe.ref()))

        testProbe.expectMessage("Guardian is alive")
    }


}