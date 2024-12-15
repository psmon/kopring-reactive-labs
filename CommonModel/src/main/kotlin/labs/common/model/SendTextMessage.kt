package labs.common.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

enum class MessageType {
    CHAT,               //For Chat
    CHATBLOCK,          //For ChatBot Block
    PUSH,               //For Push Notification
    INFO, ERROR,        //For SystemMessage
    SESSIONID           //For Session ID Update
}

enum class MessageFrom {
    CUSTOM, COUNSELOR, SYSTEM
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

