package org.example.kotlinbootreactivelabs.actor.guide

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive


sealed class HelloWorldCommand
class SayHello(var message:String, var replyTo:ActorRef<String>) : HelloWorldCommand()

class HelloWorld private constructor(
    context: ActorContext<HelloWorldCommand>
) : AbstractBehavior<HelloWorldCommand>(context) {

    companion object {
        fun create(): Behavior<HelloWorldCommand> {
            return Behaviors.setup { context -> HelloWorld(context) }
        }
    }

    override fun createReceive(): Receive<HelloWorldCommand> {
        return newReceiveBuilder()
            .onMessage(SayHello::class.java, this::onSayHello)
            .build()
    }

    private fun onSayHello(command:SayHello) : Behavior<HelloWorldCommand> {
        context.log.info("Received Hello command with message: ${command.message} - ${context.self.path().name()}")
        command.replyTo.tell("${command.message} World")
        return this
    }

}