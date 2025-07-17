package com.example.actorconcurrency.actor

import com.example.actorconcurrency.model.Hello
import com.example.actorconcurrency.model.HelloCommand
import com.example.actorconcurrency.model.HelloResponse
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

class HelloActor private constructor(
    context: ActorContext<HelloCommand>,
    private val testReceiver: ActorRef<HelloCommand>? = null
) : AbstractBehavior<HelloCommand>(context) {

    companion object {
        fun create(testReceiver: ActorRef<HelloCommand>? = null): Behavior<HelloCommand> {
            return Behaviors.setup { context ->
                HelloActor(context, testReceiver)
            }
        }
    }

    override fun createReceive(): Receive<HelloCommand> {
        return newReceiveBuilder()
            .onMessage(Hello::class.java, this::onHello)
            .build()
    }

    private fun onHello(hello: Hello): Behavior<HelloCommand> {
        context.log.info("Received message: ${hello.message}")
        
        val response = HelloResponse("Kotlin")
        
        // Send response to the specified replyTo actor if provided
        hello.replyTo?.let { replyTo ->
            replyTo.tell(response)
        }
        
        // Also send to test receiver if configured
        testReceiver?.let { receiver ->
            receiver.tell(response)
        }
        
        return this
    }
}