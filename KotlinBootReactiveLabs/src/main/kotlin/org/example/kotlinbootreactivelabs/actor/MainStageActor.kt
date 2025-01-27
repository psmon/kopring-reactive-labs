package org.example.kotlinbootreactivelabs.actor

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import org.example.kotlinbootreactivelabs.actor.cluster.CounterActor
import org.example.kotlinbootreactivelabs.actor.cluster.CounterCommand
import org.example.kotlinbootreactivelabs.actor.cluster.CounterState
import org.example.kotlinbootreactivelabs.actor.sse.AddEvent
import org.example.kotlinbootreactivelabs.actor.sse.UserEventActor
import org.example.kotlinbootreactivelabs.actor.sse.UserEventCommand
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

sealed class MainStageActorCommand
data class GetOrCreateUserEventActor(val brandId: String, val userId: String, val replyTo: ActorRef<Any>) : MainStageActorCommand()
data class PublishToTopic(val topic: String, val message: String) : MainStageActorCommand()

sealed class MainStageActorResponse

class MainStageActor private constructor(
    private val context: ActorContext<MainStageActorCommand>,
) : AbstractBehavior<MainStageActorCommand>(context) {

    private val userEventActors = ConcurrentHashMap<String, ActorRef<UserEventCommand>>()
    private val topics = ConcurrentHashMap<String, ActorRef<Topic.Command<UserEventCommand>>>()

    companion object {
        fun create(): Behavior<MainStageActorCommand> {
            return Behaviors.setup { context -> MainStageActor(context) }
        }
    }

    override fun createReceive(): Receive<MainStageActorCommand> {
        return newReceiveBuilder()
            .onMessage(GetOrCreateUserEventActor::class.java, this::onGetOrCreateUserEventActor)
            .onMessage(PublishToTopic::class.java, this::onPublishToTopic)
            .build()
    }

    private fun onGetOrCreateUserEventActor(command: GetOrCreateUserEventActor): Behavior<MainStageActorCommand> {
        val actorKey = "${command.brandId}-${command.userId}"
        val userEventActor = userEventActors.computeIfAbsent(actorKey) {

            val sigleton:ClusterSingleton = ClusterSingleton.get(context.system)

            var proxyActor:ActorRef<UserEventCommand> = sigleton.init(
                SingletonActor.of(
                    UserEventActor.create(command.brandId, command.userId), actorKey)
            )
            proxyActor
        }
        command.replyTo.tell(userEventActor)
        return this
    }

    private fun onPublishToTopic(command: PublishToTopic): Behavior<MainStageActorCommand> {
        val topic = topics.getOrPut(command.topic) {

            val sigleton:ClusterSingleton = ClusterSingleton.get(context.system)

            var proxyActor: ActorRef<Topic.Command<UserEventCommand>> = sigleton.init(
                SingletonActor.of(
                    Topic.create(UserEventCommand::class.java, command.topic), command.topic)
            )

            proxyActor
        }
        topic.tell(Topic.publish(AddEvent(command.message)))
        return this
    }
}