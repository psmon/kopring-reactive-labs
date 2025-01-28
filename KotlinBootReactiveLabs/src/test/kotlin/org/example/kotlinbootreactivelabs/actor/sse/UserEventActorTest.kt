package org.example.kotlinbootreactivelabs.actor.sse

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.pubsub.Topic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class UserEventActorTest {

    companion object {
        private lateinit var testNode1: ActorTestKit
        private lateinit var testNode2: ActorTestKit

        @BeforeAll
        @JvmStatic
        fun setup(){
            val clusterConfigA = ConfigFactory.load("cluster1.conf")
            val clusterConfigB = ConfigFactory.load("cluster2.conf")

            testNode1 = ActorTestKit.create("ClusterSystem",clusterConfigA)
            testNode2 = ActorTestKit.create("ClusterSystem",clusterConfigB)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            testNode1.shutdownTestKit()
        }
    }

    @Test
    fun testAddAndGetEvent() {
        val brandId = "brand123"
        val userId = "user456"
        val userEventActor: ActorRef<UserEventCommand> = testNode1.spawn(UserEventActor.create(brandId, userId))

        val addEvent = AddEvent("Test Event 1")
        userEventActor.tell(addEvent)

        val probe = testNode1.createTestProbe<Any>()
        userEventActor.tell(GetEvent(probe.ref()))

        probe.expectMessage("Test Event 1")
    }

    @Test
    fun testSubscribeToTopics() {
        val brandId = "brand123"
        val userId = "user456"
        val userEventActor: ActorRef<UserEventCommand> = testNode1.spawn(UserEventActor.create(brandId, userId))

        val brandTopic: ActorRef<Topic.Command<UserEventCommand>> = testNode1.spawn(Topic.create(UserEventCommand::class.java, brandId))
        val userTopic: ActorRef<Topic.Command<UserEventCommand>> = testNode1.spawn(Topic.create(UserEventCommand::class.java, userId))

        val probe = testNode1.createTestProbe<UserEventCommand>()
        brandTopic.tell(Topic.subscribe(probe.ref))
        userTopic.tell(Topic.subscribe(probe.ref))

        val addEvent = AddEvent("Test Event")
        brandTopic.tell(Topic.publish(addEvent))

        probe.expectMessage(addEvent)
    }

    @Test
    fun testSubscribeToTopicsMultiNode() {
        val brandId = "brand123"
        val userId = "user456"


    }

}