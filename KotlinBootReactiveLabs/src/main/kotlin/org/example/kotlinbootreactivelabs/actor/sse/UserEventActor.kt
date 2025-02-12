package org.example.kotlinbootreactivelabs.actor.sse

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.actor.typed.receptionist.Receptionist
import org.apache.pekko.actor.typed.receptionist.ServiceKey
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import org.example.kotlinbootreactivelabs.actor.PersitenceSerializable
import java.util.*

sealed class UserEventCommand : PersitenceSerializable

data class AddEvent @JsonCreator constructor(
    @JsonProperty("message") val message: String
) : UserEventCommand()

data class GetEvent @JsonCreator constructor(
    @JsonProperty("replyTo") val replyTo: ActorRef<Any>
) : UserEventCommand()

class UserEventActor(
    context: ActorContext<UserEventCommand>,
    private val brandId: String,
    private val userId: String
) : AbstractBehavior<UserEventCommand>(context) {

    private val eventQueue: Queue<String> = LinkedList()

    companion object {
        fun create(brandId: String, userId: String): Behavior<UserEventCommand> {
            return Behaviors.setup { context ->
                var uid = "UserEventActor-${brandId}-${userId}"
                UserEventActor(context, brandId, userId)
            }
        }
    }

    init {
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

}