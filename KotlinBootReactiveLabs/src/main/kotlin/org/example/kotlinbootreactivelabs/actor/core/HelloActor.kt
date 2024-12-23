package org.example.kotlinbootreactivelabs.actor.core

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.PostStop
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

// https://nightlies.apache.org/pekko/docs/pekko/1.1/docs/typed/actors.html

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
            .onMessage(HelloResponse::class.java, this::onHelloResponse)
            .onSignal(PostStop::class.java, this::onPostStop)
            .build()
    }

    private fun onPostStop(command :PostStop): Behavior<HelloCommand> {
        context.log.info("HelloActor onPostStop - ${context.self.path().name()}")
        return this
    }

    private fun onHello(command: Hello): Behavior<HelloCommand> {
        context.log.info("Received Hello command with message: ${command.message} - ${context.self.path().name()}")
        if (command.message == "Hello") {
            command.replyTo.tell(HelloResponse("Kotlin"))
        }
        return this
    }

    private fun onHelloResponse(command: HelloResponse): Behavior<HelloCommand> {
        context.log.info("Received HelloResponse command with message: ${command.message} - ${context.self.path().name()}")
        return this
    }

}
