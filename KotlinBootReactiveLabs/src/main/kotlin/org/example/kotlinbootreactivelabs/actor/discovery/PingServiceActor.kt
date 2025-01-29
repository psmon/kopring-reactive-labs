package org.example.kotlinbootreactivelabs.actor.discovery

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.receptionist.Receptionist
import org.apache.pekko.actor.typed.receptionist.ServiceKey
import org.example.kotlinbootreactivelabs.actor.PersitenceSerializable

sealed class PingServiceActorCommand : PersitenceSerializable

data class Ping @JsonCreator constructor(
    @JsonProperty("message") val message: String,
    @JsonProperty("replyTo") val replyTo: ActorRef<PingServiceActorCommand>
) : PingServiceActorCommand()

data class Pong @JsonCreator constructor(
    @JsonProperty("message") val message: String
) : PingServiceActorCommand()

object AutoPing: PingServiceActorCommand()

class PingServiceActor(context: ActorContext<PingServiceActorCommand>) : AbstractBehavior<PingServiceActorCommand>(context) {

    companion object {

        val PingServiceActorKey: ServiceKey<PingServiceActorCommand> = ServiceKey.create(PingServiceActorCommand::class.java, "pingService")

        fun create(): Behavior<PingServiceActorCommand> {
            return Behaviors.setup { context ->
                context.system.receptionist().tell(Receptionist.register(PingServiceActorKey, context.self))
                PingServiceActor(context)
            }
        }
    }

    init {
        context.log.info("PingServiceActor started - ${context.self.path()}")
    }

    override fun createReceive(): Receive<PingServiceActorCommand> {
        return newReceiveBuilder()
            .onMessage(Ping::class.java, this::onPing)
            .onMessage(AutoPing::class.java, this::onAutoPing)
            .build()
    }

    private fun onPing(command: Ping): Behavior<PingServiceActorCommand> {
        command.replyTo.tell(Pong("Pong"))
        return this
    }

    private fun onAutoPing(command: AutoPing): Behavior<PingServiceActorCommand> {
        context.log.info("AutoPing")
        return this
    }

}