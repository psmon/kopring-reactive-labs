package org.example.kotlinbootreactivelabs.actor.cluster

import org.example.kotlinbootreactivelabs.actor.PersitenceSerializable

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.pubsub.Topic



sealed class PubSubCommand : PersitenceSerializable

data class PublishMessage @JsonCreator constructor(
    @JsonProperty("channel") val channel: String,
    @JsonProperty("message") val message: String
) : PubSubCommand()

data class Subscribe @JsonCreator constructor(
    @JsonProperty("channel") val channel: String,
    @JsonProperty("subscriber") val subscriber: ActorRef<String>
) : PubSubCommand()

class PubSubActor(context: ActorContext<PubSubCommand>) : AbstractBehavior<PubSubCommand>(context) {

    companion object {
        fun create(): Behavior<PubSubCommand> {
            return Behaviors.setup {
                context -> PubSubActor(context)
            }
        }
    }

    private val topics = mutableMapOf<String, ActorRef<Topic.Command<String>>>()

    override fun createReceive(): Receive<PubSubCommand> {
        return newReceiveBuilder()
            .onMessage(PublishMessage::class.java, this::onPublishMessage)
            .onMessage(Subscribe::class.java, this::onSubscribe)
            .build()
    }

    private fun onPublishMessage(command: PublishMessage): Behavior<PubSubCommand> {
        val topic = topics.getOrPut(command.channel) {
            context.spawn(Topic.create(String::class.java, command.channel), command.channel)
        }
        topic.tell(Topic.publish(command.message))
        return this
    }

    private fun onSubscribe(command: Subscribe): Behavior<PubSubCommand> {
        val topic = topics.getOrPut(command.channel) {
            context.spawn(Topic.create(String::class.java, command.channel), command.channel)
        }
        topic.tell(Topic.subscribe(command.subscriber))
        return this
    }
}