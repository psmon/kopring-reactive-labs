package org.example.kotlinbootreactivelabs.ws.base

import labs.common.model.EventTextMessage
import labs.common.model.MessageFrom
import labs.common.model.MessageType
import org.example.kotlinbootreactivelabs.service.SendService
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono

@Component
class SocketHandler(
    private val sessionManager: SessionManager,
    private val sendService: SendService) : WebSocketHandler {

    override fun handle(session: WebSocketSession): Mono<Void> {
        sessionManager.addSession(session)

        return session.receive()
            .map { it.payloadAsText }
            .flatMap { payload ->
                when {
                    payload.startsWith("subscribe:") -> {
                        val topic = payload.substringAfter("subscribe:")
                        sessionManager.subscribeReactiveToTopic(session.id, topic)
                    }
                    payload.startsWith("unsubscribe:") -> {
                        val topic = payload.substringAfter("unsubscribe:")
                        sessionManager.unsubscribeReactiveFromTopic(session.id, topic)
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
                    }
                }
                Mono.empty<Void>()
            }
            .then()
            .doFinally { sessionManager.removeSession(session) }
    }
}