package org.example.kotlinbootreactivelabs.actor.cluster

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.cluster.typed.Cluster
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test

class PubSubActorTest {

    companion object{
        private lateinit var nodeA: ActorTestKit
        private lateinit var nodeB: ActorTestKit

        @BeforeAll
        @JvmStatic
        fun setup(){
            val clusterConfigA = ConfigFactory.load("cluster1.conf")
            val clusterConfigB = ConfigFactory.load("cluster2.conf")

            nodeA = ActorTestKit.create("ClusterSystem",clusterConfigA)
            nodeB = ActorTestKit.create("ClusterSystem",clusterConfigB)

            val clusterA = Cluster.get(nodeA.system())
            val clusterB = Cluster.get(nodeB.system())
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            nodeB.shutdownTestKit()
            nodeA.shutdownTestKit()
        }
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
}