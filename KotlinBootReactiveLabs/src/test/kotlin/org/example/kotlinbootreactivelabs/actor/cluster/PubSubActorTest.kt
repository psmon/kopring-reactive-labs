package org.example.kotlinbootreactivelabs.actor.cluster

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test

class PubSubActorTest {

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

    @Test
    fun testPubSubSingleNode(){

        val topic: ActorRef<Topic.Command<String>> = nodeA.spawn(Topic.create(String::class.java,
            "pubsub-topic"))

        val probe = nodeA.createTestProbe<String>()
        val probe2 = nodeA.createTestProbe<String>()

        topic.tell(Topic.subscribe(probe.ref))
        topic.tell(Topic.subscribe(probe2.ref))

        topic.tell(Topic.publish("Test Message"))

        probe.expectMessage("Test Message")
        probe2.expectMessage("Test Message")
    }

    @Test
    fun testPubSubMultiNodeWithSigleTone(){

        val sigleton1:ClusterSingleton = ClusterSingleton.get(nodeA.system())

        var proxy1:ActorRef<Topic.Command<String>> = sigleton1.init(SingletonActor.of(Topic.create(String::class.java,
            "pubsub-topic"), "pubsub-actor"))

        val sigleton2:ClusterSingleton = ClusterSingleton.get(nodeB.system())

        var proxy2:ActorRef<Topic.Command<String>> = sigleton2.init(SingletonActor.of(Topic.create(String::class.java,
            "pubsub-topic"), "pubsub-actor"))


        val probe = nodeA.createTestProbe<String>()

        val probe2 = nodeB.createTestProbe<String>()

        proxy1.tell(Topic.subscribe(probe.ref))
        proxy2.tell(Topic.subscribe(probe2.ref))

        proxy1.tell(Topic.publish("Test Message"))

        probe.expectMessage("Test Message")
        probe2.expectMessage("Test Message")
    }

}