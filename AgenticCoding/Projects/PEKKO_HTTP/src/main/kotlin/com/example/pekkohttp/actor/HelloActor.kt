package com.example.pekkohttp.actor

import com.example.pekkohttp.model.*
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

class HelloActor private constructor(
    context: ActorContext<HelloCommand>
) : AbstractBehavior<HelloCommand>(context) {

    companion object {
        fun create(): Behavior<HelloCommand> {
            return Behaviors.setup { context ->
                HelloActor(context)
            }
        }
    }

    override fun createReceive(): Receive<HelloCommand> {
        return newReceiveBuilder()
            .onMessage(GetHello::class.java, this::onGetHello)
            .build()
    }

    private fun onGetHello(command: GetHello): Behavior<HelloCommand> {
        context.log.info("Received hello request for name: ${command.name}")
        
        val response = when (command.name.lowercase()) {
            "hello" -> HelloResponse("Pekko responds with warm greetings!")
            "world" -> HelloResponse("Pekko says hello to the World!")
            "pekko" -> HelloResponse("Pekko talking to itself? How meta!")
            "akka" -> HelloResponse("Pekko is the new evolution of Akka!")
            else -> HelloResponse("Pekko says hello to ${command.name}!")
        }
        
        command.replyTo.tell(response)
        context.log.info("Sent response: ${response.message}")
        
        return this
    }
}