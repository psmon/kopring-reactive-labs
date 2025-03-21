package org.example.kotlinbootreactivelabs.ws.actor.basic

import labs.common.model.EventTextMessage
import labs.common.model.MessageFrom
import labs.common.model.MessageType
import org.apache.pekko.actor.typed.ActorRef
import org.example.kotlinbootreactivelabs.config.AkkaConfiguration
import org.example.kotlinbootreactivelabs.service.SendService
import org.example.kotlinbootreactivelabs.ws.actor.basic.SimpleSessionCommand.SimpleAddSession
import org.example.kotlinbootreactivelabs.ws.actor.basic.SimpleSessionCommand.SimpleRemoveSession
import org.example.kotlinbootreactivelabs.ws.actor.basic.SimpleSessionCommand.SimpleSubscribeToTopic
import org.example.kotlinbootreactivelabs.ws.actor.basic.SimpleSessionCommand.SimpleUnsubscribeFromTopic
import org.example.kotlinbootreactivelabs.ws.actor.chat.AddSession
import org.example.kotlinbootreactivelabs.ws.actor.chat.UserSessionCommand
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono

//data class WebSocketMessage(val type: String, val topic: String? = null, val data: String? = null)

@Component
class SocketActorHandler(
    private val sessionManager: ActorRef<SimpleSessionCommand>,
    private val sendService: SendService,
    private val akka: AkkaConfiguration
) : WebSocketHandler {

    override fun handle(session: WebSocketSession): Mono<Void> {
        val noSender = akka.getMainStage().ignoreRef<SimpleUserSessionCommandResponse>()
        sessionManager.tell(SimpleAddSession(session, noSender))

        return session.receive()
            .map { it.payloadAsText }
            .flatMap { payload ->
                when {
                    payload.startsWith("subscribe:") -> {
                        val topic = payload.substringAfter("subscribe:")
                        sessionManager.tell(SimpleSubscribeToTopic(session.id, topic, noSender))
                        Mono.empty<Void>()
                    }
                    payload.startsWith("unsubscribe:") -> {
                        val topic = payload.substringAfter("unsubscribe:")
                        sessionManager.tell(SimpleUnsubscribeFromTopic(session.id, topic, noSender))
                        Mono.empty<Void>()
                    }
                    else -> {
                        sendService.sendEventTextMessage(
                            session, EventTextMessage(
                                type = MessageType.CHAT,
                                message = "Echo: $payload",
                                from = MessageFrom.SYSTEM,
                                id = null,
                                jsondata = null,
                            )
                        )
                        Mono.empty<Void>()
                    }
                }
            }
            .then()
            .doFinally {
                sessionManager.tell(SimpleRemoveSession(session, noSender))
            }
    }
}
