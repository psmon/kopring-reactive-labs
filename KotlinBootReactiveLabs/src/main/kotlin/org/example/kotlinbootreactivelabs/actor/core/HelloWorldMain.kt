package org.example.kotlinbootreactivelabs.actor.core

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.PostStop
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

sealed class HelloWorldMainCommand
class SayHello(val name: String, val testProbe: ActorRef<Any>): HelloWorldMainCommand()
object GracefulShutdown : HelloWorldMainCommand()

// https://nightlies.apache.org/pekko/docs/pekko/1.1/docs/typed/actor-lifecycle.html

class HelloWorldMain private constructor(
    context: ActorContext<HelloWorldMainCommand>
) : AbstractBehavior<HelloWorldMainCommand>(context) {

    private val greeter: ActorRef<HelloCommand> = context.spawn(HelloActor.create(), "greeter")

    companion object {
        fun create(): Behavior<HelloWorldMainCommand> {
            return Behaviors.setup { context -> HelloWorldMain(context) }
        }
    }

    override fun createReceive(): Receive<HelloWorldMainCommand> {
        return newReceiveBuilder()
            .onMessage(SayHello::class.java, this::onStart)
            .onMessage(GracefulShutdown::class.java, this::onGracefulShutdown)
            .onSignal(PostStop::class.java, this::onPostStop)
            .build()
    }

    private fun onGracefulShutdown(command: GracefulShutdown): Behavior<HelloWorldMainCommand> {
        context.log.info("HelloWorldMain onGracefulShutdown - ${context.self.path().name()}")
        return Behaviors.stopped()
    }

    private fun onPostStop(command :PostStop): Behavior<HelloWorldMainCommand> {
        context.log.info("HelloWorldMain onPostStop - ${context.self.path().name()}")
        return this
    }

    private fun onStart(command: SayHello): Behavior<HelloWorldMainCommand> {
        val replyTo : ActorRef<HelloCommand> = context.spawn(HelloActor.create(), command.name )
        greeter.tell(Hello("Hello", replyTo))

        command.testProbe.tell("World")
        return this
    }
}