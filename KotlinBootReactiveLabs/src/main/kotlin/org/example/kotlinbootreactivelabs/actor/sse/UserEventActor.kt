package org.example.kotlinbootreactivelabs.actor.sse

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.pubsub.Topic
import java.util.*

sealed class UserEventCommand

data class AddEvent(val message: String) : UserEventCommand()
data class GetEvent(val replyTo: ActorRef<Any>) : UserEventCommand()

class UserEventActor(
    context: ActorContext<UserEventCommand>,
    private val brandId: String,
    private val userId: String
) : AbstractBehavior<UserEventCommand>(context) {

    private val eventQueue: Queue<String> = LinkedList()
    private val topics = mutableMapOf<String, ActorRef<Topic.Command<UserEventCommand>>>()

    companion object {
        fun create(brandId: String, userId: String): Behavior<UserEventCommand> {
            return Behaviors.setup { context -> UserEventActor(context, brandId, userId) }
        }
    }

    init {
        subscribeToTopic(brandId)
        subscribeToTopic(userId)
    }

    override fun createReceive(): Receive<UserEventCommand> {
        return newReceiveBuilder()
            .onMessage(AddEvent::class.java) { command -> onAddEvent(command) }
            .onMessage(GetEvent::class.java) { command -> onGetEvent(command) }
            .build()
    }

    private fun onAddEvent(command: AddEvent): Behavior<UserEventCommand> {
        eventQueue.add(command.message)
        return this
    }

    private fun onGetEvent(command: GetEvent): Behavior<UserEventCommand> {
        val event = eventQueue.poll()
        if(event != null) {
            context.log.info("Retrieved event: $event")
            command.replyTo.tell(event)
        } else {
            command.replyTo.tell("No events available")
        }

        return this
    }

    private fun subscribeToTopic(topicName: String) {
        val topic = topics.getOrPut(topicName) {
            context.spawn(Topic.create(UserEventCommand::class.java, topicName), topicName)
        }
        topic.tell(Topic.subscribe(context.self))
    }
}