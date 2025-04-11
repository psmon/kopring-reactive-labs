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
import org.example.kotlinbootreactivelabs.ws.actor.basic.SimpleUserSessionCommandResponse
import org.example.kotlinbootreactivelabs.ws.actor.chat.*
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
    private val actorSystem: ActorSystem<MainStageActorCommand>,
    private val supervisorChannelActor: ActorRef<SupervisorChannelCommand>,
    private val authService: SimpleAuthService,
    private val sendService: SendService,
) : WebSocketHandler {

    private val objectMapper = jacksonObjectMapper()
    private lateinit var counselorActor: ActorRef<CounselorCommand>

    override fun handle(session: WebSocketSession): Mono<Void> {
        return Mono.defer {
            session.receive()
                .map { it.payloadAsText }
                .flatMap { payload ->
                    val webSocketMessage: CounselorWsMessage = objectMapper.readValue(payload)
                    when (webSocketMessage.type) {
                        "login" -> handleLogin(session, webSocketMessage.data)
                        else -> handleAuthMessages(session, webSocketMessage)
                    }
                    Mono.empty<Void>()
                }
                .doFinally {
                    // TODO : 세션 종료 시 필요한 정리 작업
                    // counselorActor.tell(RemoveCounselorSocketSession(session))
                }
                .then()
        }
    }

    private fun handleLogin(session: WebSocketSession, token: String?) {
        if (token == null) {
            sendErrorMessage(session, "Login failed: Missing id or password")
            return
        }

        try {
            val authResponse = authService.getClaimsFromToken(token)
            if (authResponse.authType != "counselor") {
                sendErrorMessage(session, "Login failed: Invalid user type")
                return
            }

            session.attributes.apply {
                put("authType", "counselor")
                put("token", token)
                put("id", authResponse.id)
                put("nick", authResponse.nick)
                put("identifier", authResponse.identifier)
            }

            sendService.sendEventTextMessage(session, EventTextMessage(
                type = MessageType.INFO,
                message = "Login successful from Counselor",
                from = MessageFrom.SYSTEM,
                id = null,
                jsondata = null,
            ))

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
                    sendService.sendEventTextMessage(session, EventTextMessage(
                        type = MessageType.INFO,
                        message = "CounselorActor reference obtained.",
                        from = MessageFrom.SYSTEM,
                        id = null,
                        jsondata = null,
                    ))
                } else {
                    sendErrorMessage(session, "Failed to obtain CounselorActor reference.")
                }
            }
        } catch (e: Exception) {
            sendErrorMessage(session, "Login failed: ${e.message}")
        }
    }

    private fun handleAuthMessages(session: WebSocketSession, webSocketMessage: CounselorWsMessage) {
        val token = session.attributes["token"] as String?
        if (token == null || !isValidToken(token)) {
            sendErrorMessage(session, "Invalid or missing token")
            return
        }

        when (webSocketMessage.type) {
            "action" -> webSocketMessage.data?.let { /* Handle action */ }
            "subscribe" -> webSocketMessage.channel?.let { /* Handle subscribe */ }
            "unsubscribe" -> webSocketMessage.channel?.let { /* Handle unsubscribe */ }
            "message" -> session.attributes["identifier"]?.let { /* Handle message */ }
            "sendToRoom" -> handleSendToRoom(session, webSocketMessage)
            "addObserver" -> handleAddObserver(session, webSocketMessage)
            else -> sendErrorMessage(session, "Unknown message type: ${webSocketMessage.type}")
        }
    }

    private fun handleSendToRoom(session: WebSocketSession, webSocketMessage: CounselorWsMessage) {
        val roomName = webSocketMessage.roomName
        val message = webSocketMessage.data
        if (roomName != null && message != null) {
            counselorActor.tell(SendToRoomForPersonalTextMessage(roomName, message))
        } else {
            sendErrorMessage(session, "handleSendToRoom::Missing roomName or message")
        }
    }

    private fun handleAddObserver(session: WebSocketSession, webSocketMessage: CounselorWsMessage) {
        val roomName = webSocketMessage.roomName
        if (roomName != null) {
            counselorActor.tell(AddObserver(roomName))
        } else {
            sendErrorMessage(session, "handleAddObserver::Missing roomName or counselorName")
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

    private fun sendErrorMessage(session: WebSocketSession, message: String) {
        sendService.sendEventTextMessage(session, EventTextMessage(
            type = MessageType.ERROR,
            message = message,
            from = MessageFrom.SYSTEM,
            id = null,
            jsondata = null,
        ))
    }
}