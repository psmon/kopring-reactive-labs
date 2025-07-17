package com.example.actorconcurrency.actor

import com.example.actorconcurrency.model.HelloCommand
import com.example.actorconcurrency.model.HelloResponse
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import java.util.concurrent.ConcurrentLinkedQueue

sealed class TestReceiverCommand
data class GetMessages(val replyTo: ActorRef<Messages>) : TestReceiverCommand()
data class Messages(val messages: List<HelloCommand>) : TestReceiverCommand()

class TestReceiverActor private constructor(
    context: ActorContext<TestReceiverCommand>
) : AbstractBehavior<TestReceiverCommand>(context) {

    private val receivedMessages = ConcurrentLinkedQueue<HelloCommand>()

    companion object {
        fun create(): Behavior<TestReceiverCommand> {
            return Behaviors.setup { context ->
                TestReceiverActor(context)
            }
        }
    }

    fun receiveHelloCommand(): Behavior<HelloCommand> {
        return Behaviors.receive { context, message ->
            context.log.info("TestReceiver received: $message")
            receivedMessages.offer(message)
            Behaviors.same()
        }
    }

    override fun createReceive(): Receive<TestReceiverCommand> {
        return newReceiveBuilder()
            .onMessage(GetMessages::class.java, this::onGetMessages)
            .build()
    }

    private fun onGetMessages(command: GetMessages): Behavior<TestReceiverCommand> {
        command.replyTo.tell(Messages(receivedMessages.toList()))
        return this
    }
}