package org.example.kotlinbootreactivelabs.ws.actor

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

/** WsHelloActor 처리할 수 있는 명령들 */
sealed class WsHelloActorCommand
data class WsHello(val message: String, val replyTo: ActorRef<WsHelloActorResponse>) : WsHelloActorCommand()
data class WsGetHelloCount(val replyTo: ActorRef<WsHelloActorResponse>) : WsHelloActorCommand()
/** WsHelloActor 반환할 수 있는 응답들 */
sealed class WsHelloActorResponse
data class WsHelloResponse(val message: String) : WsHelloActorResponse()
data class WsHelloCountResponse(val count: Int) : WsHelloActorResponse()


/** 웹소켓 헬스체크용 WsHelloActor 클래스 */
class WsHelloActor private constructor(
    private val context: ActorContext<WsHelloActorCommand>,
) : AbstractBehavior<WsHelloActorCommand>(context) {

    companion object {
        fun create(): Behavior<WsHelloActorCommand> {
            return Behaviors.setup { context -> WsHelloActor(context) }
        }
    }

    override fun createReceive(): Receive<WsHelloActorCommand> {
        return newReceiveBuilder()
            .onMessage(WsHello::class.java, this::onHello)
            .onMessage(WsGetHelloCount::class.java, this::onGetHelloCount)
            .build()
    }

    private var helloCount: Int = 0

    private fun onHello(command: WsHello): Behavior<WsHelloActorCommand> {
        if (command.message == "Hello") {
            helloCount++
            context.log.info("[${context.self.path()}] Received valid Hello message. Count incremented to $helloCount")
            command.replyTo.tell(WsHelloResponse("Kotlin"))
        }
        else if (command.message == "InvalidMessage") {
            throw RuntimeException("Invalid message received!")
        }

        return this
    }

    private fun onGetHelloCount(command: WsGetHelloCount): Behavior<WsHelloActorCommand> {
        command.replyTo.tell(WsHelloCountResponse(helloCount))
        return this
    }
}