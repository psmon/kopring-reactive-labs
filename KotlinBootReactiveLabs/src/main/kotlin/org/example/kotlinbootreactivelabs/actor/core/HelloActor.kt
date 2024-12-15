package org.example.kotlinbootreactivelabs.actor.core

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

sealed class HelloCommand
data class Hello(val message: String, val replyTo:ActorRef<HelloCommand>) : HelloCommand()

data class HelloResponse(val message: String) : HelloCommand()

class HelloActor private constructor(
    context: ActorContext<HelloCommand>,
) : AbstractBehavior<HelloCommand>(context) {

    companion object {
        fun create(): Behavior<HelloCommand> {
            return Behaviors.withTimers { timers ->
                Behaviors.setup { context -> HelloActor(context) }
            }
        }
    }

    override fun createReceive(): Receive<HelloCommand> {
        return newReceiveBuilder()
            .onMessage(Hello::class.java, this::onHello)
            .build()
    }

    private fun onHello(command: Hello): Behavior<HelloCommand> {
        context.log.info("Received Hello command with message: ${command.message}")
        if (command.message == "Hello") {
            command.replyTo.tell(HelloResponse("Kotlin"))
        }
        return this
    }
}
