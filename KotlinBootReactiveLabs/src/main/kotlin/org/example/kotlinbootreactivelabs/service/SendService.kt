package org.example.kotlinbootreactivelabs.service

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import labs.common.model.EventTextMessage
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import org.springframework.web.reactive.socket.WebSocketSession as ReactiveWebSocketSession

enum class MessageType {
    CHAT,               //For Chat
    CHATBLOCK,          //For ChatBot Block
    PUSH,               //For Push Notification
    INFO, ERROR,        //For SystemMessage
    SESSIONID           //For Session ID Update
}

enum class MessageFrom {
    USER, COUNSELOR, SYSTEM
}

class EventTextMessage(
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val type: MessageType,

    val message: String,

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val from: MessageFrom,

    var id: String? = null,
    val jsondata: String? = null
)

@Component
class SendService {
    fun sendEventTextMessage(session: ReactiveWebSocketSession, eventTextMessage: EventTextMessage) {
        val objectMapper = jacksonObjectMapper()
        val jsonPayload = objectMapper.writeValueAsString(eventTextMessage)
        val message = session.textMessage(jsonPayload)
        session.send(Mono.just(message)).subscribe()
    }
}