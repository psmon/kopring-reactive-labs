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
import org.example.kotlinbootreactivelabs.ws.actor.chat.AddSession
import org.example.kotlinbootreactivelabs.ws.actor.chat.CounselorManagerCommand
import org.example.kotlinbootreactivelabs.ws.actor.chat.CounselorManagerFound
import org.example.kotlinbootreactivelabs.ws.actor.chat.CounselorManagerResponse
import org.example.kotlinbootreactivelabs.ws.actor.chat.CounselorRoomCommand
import org.example.kotlinbootreactivelabs.ws.actor.chat.CounselorRoomFound
import org.example.kotlinbootreactivelabs.ws.actor.chat.FoundPersonalRoomActor
import org.example.kotlinbootreactivelabs.ws.actor.chat.GetCounselorManager
import org.example.kotlinbootreactivelabs.ws.actor.chat.GetPersonalRoomActor
import org.example.kotlinbootreactivelabs.ws.actor.chat.OnUserAction
import org.example.kotlinbootreactivelabs.ws.actor.chat.PersonalRoomCommand
import org.example.kotlinbootreactivelabs.ws.actor.chat.RemoveSession
import org.example.kotlinbootreactivelabs.ws.actor.chat.RequestCounseling
import org.example.kotlinbootreactivelabs.ws.actor.chat.SendMessageToActor
import org.example.kotlinbootreactivelabs.ws.actor.chat.SendToCounselorRoomForCounseling
import org.example.kotlinbootreactivelabs.ws.actor.chat.SubscribeToTopic
import org.example.kotlinbootreactivelabs.ws.actor.chat.SupervisorChannelCommand
import org.example.kotlinbootreactivelabs.ws.actor.chat.SupervisorChannelResponse
import org.example.kotlinbootreactivelabs.ws.actor.chat.UnsubscribeFromTopic
import org.example.kotlinbootreactivelabs.ws.actor.chat.UpdateSession
import org.example.kotlinbootreactivelabs.ws.actor.chat.UserSessionCommand
import org.example.kotlinbootreactivelabs.ws.actor.chat.UserSessionResponse
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

        sessionManagerActor.tell(AddSession(session))

        return session.receive()
            .map { it.payloadAsText }
            .flatMap { payload ->
                val webSocketMessage: PersnalWsMessage = objectMapper.readValue(payload)
                when (webSocketMessage.type) {
                    "login" -> {
                        handleLogin(session, webSocketMessage.data)
                    }
                    "requestCounseling" -> {
                        handleCounselingRequest(session, webSocketMessage.channel)
                    }
                    "sendchat" -> {
                        handleSendChat(session, webSocketMessage.data)
                    }
                    else -> {
                        handleOtherMessages(session, webSocketMessage)
                    }
                }
                Mono.empty<Void>()
            }
            .then()
            .doFinally {
                sessionManagerActor.tell(RemoveSession(session))
            }
    }

    private fun handleLogin(session: WebSocketSession, token: String?): Any {
        return if (token != null) {
            try {
                val authResponse = authService.getClaimsFromToken(token)
                if (authResponse.authType == "user") {
                    session.attributes["authType"] = "user"
                    session.attributes["token"] = token
                    session.attributes["id"] = authResponse.id
                    session.attributes["nick"] = authResponse.nick
                    session.attributes["identifier"] = authResponse.identifier

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

                    response.whenComplete { res, ex ->
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
                            sendService.sendEventTextMessage(session, EventTextMessage(
                                type = MessageType.ERROR,
                                message = "Failed to obtain CounselorRoomActor reference.",
                                from = MessageFrom.SYSTEM,
                                id = null,
                                jsondata = null,
                            ))
                        }
                    }
                } else {
                    sendService.sendEventTextMessage(session, EventTextMessage(
                        type = MessageType.ERROR,
                        message = "Login failed: Invalid user type",
                        from = MessageFrom.SYSTEM,
                        id = null,
                        jsondata = null,
                    ))
                }
            } catch (e: Exception) {
                sendService.sendEventTextMessage(session, EventTextMessage(
                    type = MessageType.ERROR,
                    message = "Login failed: ${e.message}",
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                ))
            }
        } else {
            sendService.sendEventTextMessage(session, EventTextMessage(
                type = MessageType.ERROR,
                message = "Login failed: Missing id or password",
                from = MessageFrom.SYSTEM,
                id = null,
                jsondata = null,
            ))
        }
    }

    private fun handleCounselingRequest(session: WebSocketSession, channel: String?): Any {
        val token = session.attributes["token"] as String?
        if (token == null || !isValidToken(token)) {
            sendService.sendEventTextMessage(session, EventTextMessage(
                type = MessageType.ERROR,
                message = "Invalid or missing token",
                from = MessageFrom.SYSTEM,
                id = null,
                jsondata = null,
            ))
            return Mono.empty<Void>()
        }

        if (channel != null) {
            val roomName = "${channel}_${UUID.randomUUID()}"
            sendService.sendEventTextMessage(session, EventTextMessage(
                type = MessageType.INFO,
                message = "Try Counselor : ${channel}",
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
                    ).thenAccept() { res2 ->
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
                            sendService.sendEventTextMessage(session, EventTextMessage(
                                type = MessageType.ERROR,
                                message = "Counseling request failed: $roomName",
                                from = MessageFrom.SYSTEM,
                                id = null,
                                jsondata = null,
                            ))
                        }
                    }

                } else {
                    sendService.sendEventTextMessage(
                        session, EventTextMessage(
                            type = MessageType.ERROR,
                            message = "Counselor manager not found for channel: $channel",
                            from = MessageFrom.SYSTEM,
                            id = null,
                            jsondata = null,
                        )
                    )
                }
            }

        }else {
            sendService.sendEventTextMessage(
                session, EventTextMessage(
                    type = MessageType.ERROR,
                    message = "Counseling request failed: Missing channel",
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                )
            )
        }

        return Mono.empty<Void>()
    }

    private fun handleSendChat(session: WebSocketSession, chatMessage: String?): Any {
        return if (chatMessage != null) {
            persnalRoomActor.tell(SendToCounselorRoomForCounseling(chatMessage))
            Mono.empty<Void>()
        } else {
            sendService.sendEventTextMessage(session, EventTextMessage(
                type = MessageType.ERROR,
                message = "Chat message is missing",
                from = MessageFrom.SYSTEM,
                id = null,
                jsondata = null,
            ))
        }
    }

    private fun handleOtherMessages(session: WebSocketSession, webSocketMessage: PersnalWsMessage): Any {
        val token = session.attributes["token"] as String?
        return if (token == null || !isValidToken(token)) {
            sendService.sendEventTextMessage(session, EventTextMessage(
                type = MessageType.ERROR,
                message = "Invalid or missing token",
                from = MessageFrom.SYSTEM,
                id = null,
                jsondata = null,
            ))
        } else {
            when (webSocketMessage.type) {
                "action" -> webSocketMessage.data?.let { data -> sessionManagerActor.tell(OnUserAction(session, data)) }
                "subscribe" -> webSocketMessage.topic?.let { topic -> sessionManagerActor.tell(SubscribeToTopic(session.id, topic)) }
                "unsubscribe" -> webSocketMessage.topic?.let { topic -> sessionManagerActor.tell(UnsubscribeFromTopic(session.id, topic)) }
                "message" -> session.attributes["identifier"]?.let { identifier -> sessionManagerActor.tell(SendMessageToActor(identifier.toString(), webSocketMessage.data.toString())) }
                else -> sendService.sendEventTextMessage(session, EventTextMessage(
                    type = MessageType.ERROR,
                    message = "Unknown message type: ${webSocketMessage.type}",
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                ))
            }
            Mono.empty<Void>()
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