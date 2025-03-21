package org.example.kotlinbootreactivelabs.ws.actor.handler

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import labs.common.model.EventTextMessage
import labs.common.model.MessageFrom
import labs.common.model.MessageType
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.example.kotlinbootreactivelabs.actor.MainStageActorCommand
import org.example.kotlinbootreactivelabs.service.SendService
import org.example.kotlinbootreactivelabs.service.SimpleAuthService
import org.example.kotlinbootreactivelabs.ws.actor.chat.CounselorActorFound
import org.example.kotlinbootreactivelabs.ws.actor.chat.CounselorCommand
import org.example.kotlinbootreactivelabs.ws.actor.chat.GetCounselorFromManager
import org.example.kotlinbootreactivelabs.ws.actor.chat.SendToRoomForPersonalTextMessage
import org.example.kotlinbootreactivelabs.ws.actor.chat.SetCounselorSocketSession
import org.example.kotlinbootreactivelabs.ws.actor.chat.SupervisorChannelCommand
import org.example.kotlinbootreactivelabs.ws.actor.chat.SupervisorChannelResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.CompletionStage

data class CounselorWsMessage(
    val type: String,
    val channel: String? = null,
    val roomName: String?,
    val counselorName: String? = null,
    val data: String? = null
)

@Component
class SocketHandleForCounselor(
    private val supervisorChannelActor: ActorRef<SupervisorChannelCommand>,
    private val authService: SimpleAuthService,
    private val sendService: SendService,
    private val actorSystem: ActorSystem<MainStageActorCommand>
) : WebSocketHandler {

    private val objectMapper = jacksonObjectMapper()

    private lateinit var counselorActor: ActorRef<CounselorCommand>

    override fun handle(session: WebSocketSession): Mono<Void> {

        return session.receive()
            .map { it.payloadAsText }
            .flatMap { payload ->
                val webSocketMessage: CounselorWsMessage = objectMapper.readValue(payload)

                when (webSocketMessage.type) {
                    "login" -> {
                        handleLogin(session, webSocketMessage.data)
                    }
                    else -> {
                        handleAuthMessages(session, webSocketMessage)
                    }
                }
                Mono.empty<Void>()
            }
            .then()
            .doFinally {
            }
    }

    private fun handleLogin(session: WebSocketSession, token: String?) {
        if (token == null) {
            sendService.sendEventTextMessage(
                session, EventTextMessage(
                    type = MessageType.ERROR,
                    message = "Login failed: Missing id or password",
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                )
            )
            return
        }

        try {
            val authResponse = authService.getClaimsFromToken(token)
            if (authResponse.authType != "counselor") {
                sendService.sendEventTextMessage(
                    session, EventTextMessage(
                        type = MessageType.ERROR,
                        message = "Login failed: Invalid user type",
                        from = MessageFrom.SYSTEM,
                        id = null,
                        jsondata = null,
                    )
                )
                return
            }

            session.attributes.apply {
                put("authType", "counselor")
                put("token", token)
                put("id", authResponse.id)
                put("nick", authResponse.nick)
                put("identifier", authResponse.identifier)
            }

            sendService.sendEventTextMessage(
                session, EventTextMessage(
                    type = MessageType.INFO,
                    message = "Login successful from Counselor",
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                )
            )

            val response: CompletionStage<SupervisorChannelResponse> = AskPattern.ask(
                supervisorChannelActor,
                { replyTo: ActorRef<SupervisorChannelResponse> ->
                    authResponse.identifier?.let { GetCounselorFromManager(it, authResponse.nick, replyTo) }
                },
                Duration.ofSeconds(3),
                actorSystem.scheduler()
            )

            response.whenComplete { res, _ ->
                if (res is CounselorActorFound) {
                    counselorActor = res.actorRef
                    counselorActor.tell(SetCounselorSocketSession(session))
                    sendService.sendEventTextMessage(
                        session, EventTextMessage(
                            type = MessageType.INFO,
                            message = "CounselorActor reference obtained.",
                            from = MessageFrom.SYSTEM,
                            id = null,
                            jsondata = null,
                        )
                    )
                } else {
                    sendService.sendEventTextMessage(
                        session, EventTextMessage(
                            type = MessageType.ERROR,
                            message = "Failed to obtain CounselorActor reference.",
                            from = MessageFrom.SYSTEM,
                            id = null,
                            jsondata = null,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            sendService.sendEventTextMessage(
                session, EventTextMessage(
                    type = MessageType.ERROR,
                    message = "Login failed: Invalid user type",
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                )
            )
            return
        }
    }

    private fun handleAuthMessages(session: WebSocketSession, webSocketMessage: CounselorWsMessage) {
        val token = session.attributes["token"] as String?
        if (token == null || !isValidToken(token)) {
            sendService.sendEventTextMessage(
                session, EventTextMessage(
                    type = MessageType.ERROR,
                    message = "Invalid or missing token",
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                )
            )
            return
        }

        when (webSocketMessage.type) {
            "action" -> webSocketMessage.data?.let { /* Handle action */ }
            "subscribe" -> webSocketMessage.channel?.let { /* Handle subscribe */ }
            "unsubscribe" -> webSocketMessage.channel?.let { /* Handle unsubscribe */ }
            "message" -> session.attributes["identifier"]?.let { /* Handle message */ }
            "sendToRoom" -> {
                val roomName = webSocketMessage.roomName
                val message = webSocketMessage.data
                if (roomName != null && message != null) {
                    counselorActor.tell(SendToRoomForPersonalTextMessage(roomName, message))
                } else {
                    sendService.sendEventTextMessage(
                        session, EventTextMessage(
                            type = MessageType.ERROR,
                            message = "Missing roomName or message",
                            from = MessageFrom.SYSTEM,
                            id = null,
                            jsondata = null,
                        )
                    )
                }
            }
            else -> {
                sendService.sendEventTextMessage(
                    session, EventTextMessage(
                        type = MessageType.ERROR,
                        message = "Unknown message type: ${webSocketMessage.type}",
                        from = MessageFrom.SYSTEM,
                        id = null,
                        jsondata = null,
                    )
                )
            }
        }
    }

    private fun isValidToken(token: String): Boolean {
        return try {
            authService.getClaimsFromToken(token)
            true
        } catch (e: Exception) {
            false
        }
    }
}
