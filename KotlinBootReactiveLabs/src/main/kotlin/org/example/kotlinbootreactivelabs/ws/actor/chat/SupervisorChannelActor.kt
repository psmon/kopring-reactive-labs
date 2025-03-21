package org.example.kotlinbootreactivelabs.ws.actor.chat

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import java.time.Duration
import java.util.concurrent.CompletionStage

sealed class SupervisorChannelCommand
data class CreateCounselorManager(val channel: String, val replyTo: ActorRef<SupervisorChannelResponse>) : SupervisorChannelCommand()
data class GetCounselorManager(val channel: String, val replyTo: ActorRef<SupervisorChannelResponse>) : SupervisorChannelCommand()
data class GetAllCounselorManagers(val replyTo: ActorRef<SupervisorChannelResponse>) : SupervisorChannelCommand()
data class GetCounselorFromManager(val channel: String, val counselorName: String, val replyTo: ActorRef<SupervisorChannelResponse>) : SupervisorChannelCommand()
data class RemoveCounselorManager(val channel: String, val replyTo: ActorRef<SupervisorChannelResponse>) : SupervisorChannelCommand()

sealed class SupervisorChannelResponse
data class CounselorManagerCreated(val channel: String) : SupervisorChannelResponse()
data class CounselorManagerFound(val channel: String, val actorRef: ActorRef<CounselorManagerCommand>) : SupervisorChannelResponse()
data class AllCounselorManagers(val channels: List<String>) : SupervisorChannelResponse()
data class SupervisorErrorStringResponse(val message: String) : SupervisorChannelResponse()
data class CounselorActorFound(val counselorName: String, val actorRef: ActorRef<CounselorCommand>) : SupervisorChannelResponse()
data class CounselorManagerRemoved(val channel: String) : SupervisorChannelResponse()


class SupervisorChannelActor private constructor(
    context: ActorContext<SupervisorChannelCommand>
) : AbstractBehavior<SupervisorChannelCommand>(context) {

    companion object {
        fun create(): Behavior<SupervisorChannelCommand> {
            return Behaviors.setup { context -> SupervisorChannelActor(context) }
        }
    }

    private val counselorManagers = mutableMapOf<String, ActorRef<CounselorManagerCommand>>()

    override fun createReceive(): Receive<SupervisorChannelCommand> {
        return newReceiveBuilder()
            .onMessage(CreateCounselorManager::class.java, this::onCreateCounselorManager)
            .onMessage(GetCounselorManager::class.java, this::onGetCounselorManager)
            .onMessage(GetAllCounselorManagers::class.java, this::onGetAllCounselorManagers)
            .onMessage(GetCounselorFromManager::class.java, this::onGetCounselorFromManager)
            .onMessage(RemoveCounselorManager::class.java, this::onRemoveCounselorManager)
            .build()
    }

    private fun onCreateCounselorManager(command: CreateCounselorManager): Behavior<SupervisorChannelCommand> {
        if (counselorManagers.containsKey(command.channel)) {
            command.replyTo.tell(SupervisorErrorStringResponse("CounselorManager for channel ${command.channel} already exists."))
        } else {
            val counselorManagerActor = context.spawn(CounselorManagerActor.create(), "CounselorManager-${command.channel}")
            counselorManagers[command.channel] = counselorManagerActor
            command.replyTo.tell(CounselorManagerCreated(command.channel))
        }
        return this
    }

    private fun onRemoveCounselorManager(removeCounselorManager: RemoveCounselorManager): Behavior<SupervisorChannelCommand> {
        val counselorManagerActor = counselorManagers.remove(removeCounselorManager.channel)
        if (counselorManagerActor != null) {
            removeCounselorManager.replyTo.tell(CounselorManagerRemoved(removeCounselorManager.channel))
        } else {
            removeCounselorManager.replyTo.tell(SupervisorErrorStringResponse("CounselorManager for channel ${removeCounselorManager.channel} not found."))
        }
        return this
    }

    private fun onGetCounselorManager(command: GetCounselorManager): Behavior<SupervisorChannelCommand> {
        val counselorManagerActor = counselorManagers[command.channel]
        if (counselorManagerActor != null) {
            command.replyTo.tell(CounselorManagerFound(command.channel, counselorManagerActor))
        } else {
            command.replyTo.tell(SupervisorErrorStringResponse("CounselorManager for channel ${command.channel} not found."))
        }
        return this
    }

    private fun onGetAllCounselorManagers(command: GetAllCounselorManagers): Behavior<SupervisorChannelCommand> {
        val channels = counselorManagers.keys.toList()
        command.replyTo.tell(AllCounselorManagers(channels))
        return this
    }

    private fun onGetCounselorFromManager(command: GetCounselorFromManager): Behavior<SupervisorChannelCommand> {
        val counselorManagerActor = counselorManagers[command.channel]
        if (counselorManagerActor != null) {
            val response: CompletionStage<CounselorManagerResponse> = AskPattern.ask(
                counselorManagerActor,
                { replyTo: ActorRef<CounselorManagerResponse> -> GetCounselor(command.counselorName, replyTo) },
                Duration.ofSeconds(3),
                context.system.scheduler()
            )

            response.whenComplete { res, ex ->
                if (res is CounselorFound) {
                    command.replyTo.tell(CounselorActorFound(res.name, res.actorRef))
                } else {
                    command.replyTo.tell(SupervisorErrorStringResponse("Counselor ${command.counselorName} not found."))
                }
            }
        } else {
            command.replyTo.tell(SupervisorErrorStringResponse("CounselorManager for channel ${command.channel} not found."))
        }
        return this
    }
}