package org.example.kotlinbootreactivelabs.actor.cluster

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import org.example.kotlinbootreactivelabs.actor.discovery.GuardianActor
import org.example.kotlinbootreactivelabs.actor.discovery.KeepAlive
import org.example.kotlinbootreactivelabs.actor.discovery.Ping
import org.example.kotlinbootreactivelabs.actor.discovery.PingServiceActor
import org.example.kotlinbootreactivelabs.actor.discovery.PingServiceActorCommand
import org.example.kotlinbootreactivelabs.actor.discovery.PingerActor
import org.example.kotlinbootreactivelabs.actor.discovery.Pong
import org.example.kotlinbootreactivelabs.actor.discovery.ReceptionCounter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReceptionTest {

    private lateinit var nodeA: ActorTestKit
    private lateinit var nodeB: ActorTestKit

    @BeforeEach
    fun setup(){
        val clusterConfigA = ConfigFactory.load("cluster1.conf")
        val clusterConfigB = ConfigFactory.load("cluster2.conf")

        nodeA = ActorTestKit.create("ClusterSystem",clusterConfigA)
        nodeB = ActorTestKit.create("ClusterSystem",clusterConfigB)

        val clusterA = Cluster.get(nodeA.system())
        val clusterB = Cluster.get(nodeB.system())
    }

    @AfterEach
    fun teardown() {
        nodeB.shutdownTestKit()
        nodeA.shutdownTestKit()
    }

    // context.system.receptionist().tell(Receptionist.register(PingServiceActorKey, context.self))

    @Test
    fun testPingerActor() {
        // Test code
        val pingerService = nodeA.createTestProbe<PingServiceActorCommand>()

        val pingerActor: ActorRef<PingServiceActorCommand> = nodeA.spawn(PingerActor.create(pingerService.ref))
        val pingerActor2: ActorRef<PingServiceActorCommand> = nodeA.spawn(PingerActor.create(pingerService.ref))

        pingerService.expectMessageClass<Ping>(Ping::class.java)

        pingerService.expectMessageClass<Ping>(Ping::class.java)

        pingerActor.tell(Pong("Pong"))

        pingerActor2.tell(Pong("Pong"))

        pingerService.expectTerminated(pingerActor)

        pingerService.expectTerminated(pingerActor2)

    }

    @Test
    fun testGuardianActor(){

        val guardian = nodeA.spawn(GuardianActor.create())

        val testProbe = nodeA.createTestProbe<Any>()

        val pingerService = nodeA.spawn(PingServiceActor.create())

        val pingerActor: ActorRef<PingServiceActorCommand> = nodeA.spawn(PingerActor.create(pingerService))

        guardian.tell(KeepAlive(testProbe.ref()))

        testProbe.expectMessage("Guardian is alive")
    }

    @Test
    fun testReception() {

        // GuardianActor is a singleton actor
        val sigleton1:ClusterSingleton = ClusterSingleton.get(nodeA.system())
        val sigleton2:ClusterSingleton = ClusterSingleton.get(nodeB.system())

        var guardian1:ActorRef<Any> = sigleton1.init(
            SingletonActor.of(
                GuardianActor.create(),
                "GuardianActor"))

        var guardian2:ActorRef<Any> = sigleton2.init(
            SingletonActor.of(
                GuardianActor.create(),
                "GuardianActor"))

        var testProbe1 = nodeA.createTestProbe<Any>()
        var testProbe2 = nodeB.createTestProbe<Int>()

        // PingService Count 1
        val pingerService = nodeA.spawn(PingServiceActor.create())

        val pingerActor = nodeA.createTestProbe<PingServiceActorCommand>()

        // Test PingServiceActor in Node A
        pingerService.tell(Ping("Ping", pingerActor.ref()))
        pingerActor.expectMessage<Pong>(Pong("Pong"))

        guardian1.tell(KeepAlive(testProbe1.ref()))
        testProbe1.expectMessage("Guardian is alive")

        // Check Service Count at Node B
        guardian2.tell(ReceptionCounter(testProbe2.ref()))
        testProbe2.expectMessage<Int>(1)
    }

}