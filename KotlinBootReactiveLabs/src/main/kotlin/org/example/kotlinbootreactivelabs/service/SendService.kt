package org.example.kotlinbootreactivelabs.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import labs.common.model.EventTextMessage
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import org.springframework.web.reactive.socket.WebSocketSession as ReactiveWebSocketSession



@Component
class SendService {

    private var lastSentMessage: EventTextMessage? = null

    fun sendEventTextMessage(session: ReactiveWebSocketSession, eventTextMessage: EventTextMessage) {
        lastSentMessage = eventTextMessage
        val objectMapper = jacksonObjectMapper()
        val jsonPayload = objectMapper.writeValueAsString(eventTextMessage)
        val message = session.textMessage(jsonPayload)
        session.send(Mono.just(message)).subscribe()
    }

    fun getLastSentMessage(): EventTextMessage? {
        return lastSentMessage
    }
}