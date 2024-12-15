package org.example.kotlinbootreactivelabs.actor.discovery

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive


class PingerActor(
    context: ActorContext<PingServiceActorCommand>,
    pingService: ActorRef<PingServiceActorCommand>
) : AbstractBehavior<PingServiceActorCommand>(context) {

    companion object {
        fun create(pingService: ActorRef<PingServiceActorCommand>): Behavior<PingServiceActorCommand> {
            return Behaviors.setup { context ->
                pingService.tell(Ping("Ping",context.self))
                PingerActor(context, pingService)
            }
        }
    }

    override fun createReceive(): Receive<PingServiceActorCommand> {
        return newReceiveBuilder()
            .onMessage(Pong::class.java, this::onPong)
            .build()
    }

    private fun onPong(command: Pong): Behavior<PingServiceActorCommand> {
        context.log.info("${context.self} was ponged with message: ${command.message}")
        return Behaviors.stopped()
    }
}