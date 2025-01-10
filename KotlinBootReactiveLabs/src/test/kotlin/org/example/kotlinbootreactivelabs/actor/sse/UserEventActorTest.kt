package org.example.kotlinbootreactivelabs.actor.sse

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.pubsub.Topic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

class UserEventActorTest {

    companion object {
        private val testKit = ActorTestKit.create()

        @AfterAll
        @JvmStatic
        fun teardown() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun testAddAndGetEvent() {
        val brandId = "brand123"
        val userId = "user456"
        val userEventActor: ActorRef<UserEventCommand> = testKit.spawn(UserEventActor.create(brandId, userId))

        val addEvent = AddEvent("Test Event 1")
        userEventActor.tell(addEvent)

        val probe = testKit.createTestProbe<Any>()
        userEventActor.tell(GetEvent(probe.ref()))

        probe.expectMessage("Test Event 1")
    }

    @Test
    fun testSubscribeToTopics() {
        val brandId = "brand123"
        val userId = "user456"
        val userEventActor: ActorRef<UserEventCommand> = testKit.spawn(UserEventActor.create(brandId, userId))

        val brandTopic: ActorRef<Topic.Command<UserEventCommand>> = testKit.spawn(Topic.create(UserEventCommand::class.java, brandId))
        val userTopic: ActorRef<Topic.Command<UserEventCommand>> = testKit.spawn(Topic.create(UserEventCommand::class.java, userId))

        val probe = testKit.createTestProbe<UserEventCommand>()
        brandTopic.tell(Topic.subscribe(probe.ref))
        userTopic.tell(Topic.subscribe(probe.ref))

        val addEvent = AddEvent("Test Event")
        brandTopic.tell(Topic.publish(addEvent))

        probe.expectMessage(addEvent)
    }
}