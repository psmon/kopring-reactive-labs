package org.example.kotlinbootreactivelabs.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import labs.common.model.EventTextMessage
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import org.springframework.web.reactive.socket.WebSocketSession as ReactiveWebSocketSession


@Component
class SendService {

    fun sendReactiveEventTextMessage(session: ReactiveWebSocketSession, eventTextMessage: EventTextMessage) {
        val objectMapper = jacksonObjectMapper()
        val jsonPayload = objectMapper.writeValueAsString(eventTextMessage)
        val message = session.textMessage(jsonPayload)
        session.send(Mono.just(message)).subscribe()
    }
}