package org.example.kotlinbootreactivelabs.actor

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import org.example.kotlinbootreactivelabs.actor.sse.AddEvent
import org.example.kotlinbootreactivelabs.actor.sse.UserEventActor
import org.example.kotlinbootreactivelabs.actor.sse.UserEventCommand
import org.example.kotlinbootreactivelabs.ws.actor.SessionManagerActor
import org.example.kotlinbootreactivelabs.ws.actor.UserSessionCommand
import java.util.concurrent.ConcurrentHashMap

sealed class MainStageActorCommand : PersitenceSerializable
data class CreateSocketSessionManager(val replyTo: ActorRef<MainStageActorResponse>) : MainStageActorCommand()

sealed class MainStageActorResponse : PersitenceSerializable
data class SocketSessionManagerCreated(val actorRef: ActorRef<UserSessionCommand>) : MainStageActorResponse()


data class GetOrCreateUserEventActor @JsonCreator constructor(
    @JsonProperty("brandId") val brandId: String,
    @JsonProperty("userId") val userId: String,
    @JsonProperty("replyTo") val replyTo: ActorRef<Any>
) : MainStageActorCommand()

data class PublishToTopic @JsonCreator constructor(
    @JsonProperty("topic") val topic: String,
    @JsonProperty("message") val message: String
) : MainStageActorCommand()


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
            .onMessage(CreateSocketSessionManager::class.java, this::onSocketSessionManager)
            .build()
    }

    // For WebSocketClient
    private fun onSocketSessionManager(command: CreateSocketSessionManager): Behavior<MainStageActorCommand> {
        val sessionManagerActor = context.spawn(
            Behaviors.supervise(SessionManagerActor.create())
                .onFailure(SupervisorStrategy.resume()),
            "sessionManagerActor"
        )
        context.watch(sessionManagerActor)

        command.replyTo.tell(SocketSessionManagerCreated(sessionManagerActor))

        return this
    }

    // For SES Client
    private fun onGetOrCreateUserEventActor(command: GetOrCreateUserEventActor): Behavior<MainStageActorCommand> {
        val sigleton:ClusterSingleton = ClusterSingleton.get(context.system)

        val topicA = topics.getOrPut(command.brandId) {
            val proxyActor:ActorRef<Topic.Command<UserEventCommand>> = sigleton.init(
                SingletonActor.of(
                    Topic.create(UserEventCommand::class.java, command.brandId),
                    command.brandId
                )
            )
            proxyActor
        }

        val topicB = topics.getOrPut(command.userId) {
            val proxyActor:ActorRef<Topic.Command<UserEventCommand>> = sigleton.init(
                SingletonActor.of(
                    Topic.create(UserEventCommand::class.java, command.userId),
                    command.userId
                )
            )
            proxyActor
        }

        val actorKey = "${command.brandId}-${command.userId}"
        val userEventActor = userEventActors.computeIfAbsent(actorKey) {
            var proxyActor:ActorRef<UserEventCommand> = sigleton.init(
                SingletonActor.of(
                    UserEventActor.create(command.brandId, command.userId), actorKey)
            )
            proxyActor
        }

        topicA.tell(Topic.subscribe(userEventActor))
        topicB.tell(Topic.subscribe(userEventActor))

        command.replyTo.tell(userEventActor)
        return this
    }

    // For Rest API
    private fun onPublishToTopic(command: PublishToTopic): Behavior<MainStageActorCommand> {
        val sigleton:ClusterSingleton = ClusterSingleton.get(context.system)
        val topic = topics.getOrPut(command.topic) {
            val proxyActor:ActorRef<Topic.Command<UserEventCommand>> = sigleton.init(
                SingletonActor.of(
                    Topic.create(UserEventCommand::class.java, command.topic),
                    command.topic
                )
            )
            proxyActor
        }
        topic.tell(Topic.publish(AddEvent(command.message)))
        return this
    }
}