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
import org.example.kotlinbootreactivelabs.ws.actor.chat.*
import org.example.kotlinbootreactivelabs.ws.actor.chat.router.generateRandomSkillInfo
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletionStage

data class PersnalWsMessage(val type: String, val channel: String?, val topic: String? = null, val data: String? = null)

@Component
class SocketHandlerForPersnalRoom(
    private val actorSystem: ActorSystem<MainStageActorCommand>,
    private val supervisorChannelActor: ActorRef<SupervisorChannelCommand>,
    private val sessionManagerActor: ActorRef<UserSessionCommand>,
    private val authService: SimpleAuthService,
    private val sendService: SendService,
) : WebSocketHandler {

    private val objectMapper = jacksonObjectMapper()
    private lateinit var persnalRoomActor: ActorRef<PersonalRoomCommand>
    private lateinit var counselorManager: ActorRef<CounselorManagerCommand>
    private lateinit var counselorRoomActor: ActorRef<CounselorRoomCommand>

    override fun handle(session: WebSocketSession): Mono<Void> {
        return Mono.defer {
            sessionManagerActor.tell(AddSession(session))

            session.receive()
                .map { it.payloadAsText }
                .flatMap { payload ->
                    val webSocketMessage: PersnalWsMessage = objectMapper.readValue(payload)
                    when (webSocketMessage.type) {
                        "login" -> {
                            handleLogin(session, webSocketMessage.data)
                            Mono.empty<Void>()
                        }
                        "requestCounseling" -> {
                            handleCounselingRequest(session, webSocketMessage.channel)
                            Mono.empty<Void>()
                        }
                        "sendchat" -> {
                            handleSendChat(session, webSocketMessage.data)
                            Mono.empty<Void>()
                        }
                        else -> {
                            handleOtherMessages(session, webSocketMessage)
                            Mono.empty<Void>()
                        }
                    }
                }
                .doFinally {
                    sessionManagerActor.tell(RemoveSession(session))
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
            if (authResponse.authType == "user") {
                session.attributes.apply {
                    put("authType", "user")
                    put("token", token)
                    put("id", authResponse.id)
                    put("nick", authResponse.nick)
                    put("identifier", authResponse.identifier)
                }

                sendService.sendEventTextMessage(session, EventTextMessage(
                    type = MessageType.INFO,
                    message = "Login successful from User",
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                ))

                sessionManagerActor.tell(UpdateSession(session, authResponse))

                val response: CompletionStage<UserSessionResponse> = AskPattern.ask(
                    sessionManagerActor,
                    { replyTo: ActorRef<UserSessionResponse> ->
                        authResponse.identifier?.let { GetPersonalRoomActor(it, replyTo) }
                    },
                    Duration.ofSeconds(3),
                    actorSystem.scheduler()
                )

                response.whenComplete { res, _ ->
                    if (res is FoundPersonalRoomActor) {
                        persnalRoomActor = res.actorRef
                        sendService.sendEventTextMessage(session, EventTextMessage(
                            type = MessageType.INFO,
                            message = "PersonalRoomActor reference obtained.",
                            from = MessageFrom.SYSTEM,
                            id = null,
                            jsondata = null,
                        ))
                    } else {
                        sendErrorMessage(session, "Failed to obtain PersonalRoomActor reference.")
                    }
                }
            } else {
                sendErrorMessage(session, "Login failed: Invalid user type")
            }
        } catch (e: Exception) {
            sendErrorMessage(session, "Login failed: ${e.message}")
        }
    }

    private fun handleCounselingRequest(session: WebSocketSession, channel: String?) {
        val token = session.attributes["token"] as String?
        if (token == null || !isValidToken(token)) {
            sendErrorMessage(session, "Invalid or missing token")
            return
        }

        if (channel == null) {
            sendErrorMessage(session, "Counseling request failed: Missing channel")
            return
        }

        val roomName = "${channel}_${UUID.randomUUID()}"
        sendService.sendEventTextMessage(session, EventTextMessage(
            type = MessageType.INFO,
            message = "Try Counselor : $channel",
            from = MessageFrom.SYSTEM,
            id = null,
            jsondata = null,
        ))

        AskPattern.ask(
            supervisorChannelActor,
            { replyTo: ActorRef<SupervisorChannelResponse> -> GetCounselorManager(channel, replyTo) },
            Duration.ofSeconds(3),
            actorSystem.scheduler()
        ).thenAccept { res ->
            if (res is CounselorManagerFound) {
                counselorManager = res.actorRef
                sendService.sendEventTextMessage(session, EventTextMessage(
                    type = MessageType.INFO,
                    message = "Counseling CounselorManagerFound : ${res.channel}",
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                ))

                AskPattern.ask(
                    counselorManager,
                    { replyTo: ActorRef<CounselorManagerResponse> -> RequestCounseling(roomName,
                        generateRandomSkillInfo(), persnalRoomActor, replyTo) },
                    Duration.ofSeconds(3),
                    actorSystem.scheduler()
                ).thenAccept { res2 ->
                    if (res2 is CounselorRoomFound) {
                        sendService.sendEventTextMessage(session, EventTextMessage(
                            type = MessageType.INFO,
                            message = "Counseling room created: $roomName",
                            from = MessageFrom.SYSTEM,
                            id = null,
                            jsondata = null,
                        ))
                        counselorRoomActor = res2.actorRef
                    } else {
                        sendErrorMessage(session, "Counseling request failed: $roomName")
                    }
                }
            } else {
                sendErrorMessage(session, "Counselor manager not found for channel: $channel")
            }
        }
    }

    private fun handleSendChat(session: WebSocketSession, chatMessage: String?) {
        if (chatMessage != null) {
            persnalRoomActor.tell(SendToCounselorRoomForCounseling(chatMessage))
        } else {
            sendErrorMessage(session, "Chat message is missing")
        }
    }

    private fun handleOtherMessages(session: WebSocketSession, webSocketMessage: PersnalWsMessage) {
        val token = session.attributes["token"] as String?
        if (token == null || !isValidToken(token)) {
            sendErrorMessage(session, "Invalid or missing token")
            return
        }

        when (webSocketMessage.type) {
            "action" -> webSocketMessage.data?.let { data -> sessionManagerActor.tell(OnUserAction(session, data)) }
            "subscribe" -> webSocketMessage.topic?.let { topic -> sessionManagerActor.tell(SubscribeToTopic(session.id, topic)) }
            "unsubscribe" -> webSocketMessage.topic?.let { topic -> sessionManagerActor.tell(UnsubscribeFromTopic(session.id, topic)) }
            "message" -> session.attributes["identifier"]?.let { identifier -> sessionManagerActor.tell(SendMessageToActor(identifier.toString(), webSocketMessage.data.toString())) }
            else -> sendErrorMessage(session, "Unknown message type: ${webSocketMessage.type}")
        }
    }

    private fun isValidToken(token: String): Boolean {
        return try {
            authService.getClaimsFromToken(token)
            true
        } catch (_: Exception) {
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