package org.example.kotlinbootreactivelabs.ws.actor.chat

import labs.common.model.EventTextMessage
import labs.common.model.MessageFrom
import labs.common.model.MessageType
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.example.kotlinbootreactivelabs.service.SendService
import org.example.kotlinbootreactivelabs.service.TokenClaims
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import org.springframework.web.reactive.socket.WebSocketSession

sealed class UserSessionCommand
sealed class UserSessionResponse

data class AddSession(val session: WebSocketSession) : UserSessionCommand()
data class RemoveSession(val session: WebSocketSession) : UserSessionCommand()

data class SubscribeToTopic(val sessionId: String, val topic: String) : UserSessionCommand()
data class UnsubscribeFromTopic(val sessionId: String, val topic: String) : UserSessionCommand()

data class UpdateSession(val session: WebSocketSession, val claims: TokenClaims) : UserSessionCommand()
data class OnUserAction(val session: WebSocketSession, val action: String) : UserSessionCommand()

// Server To Session
data class SendMessageToSession(val sessionId: String, val message: String) : UserSessionCommand()
data class SendMessageToTopic(val topic: String, val message: String) : UserSessionCommand()
data class SendMessageToAll(val message: String) : UserSessionCommand()

// Session To Server
data class SendMessageToActor(val identifier: String, val message: String) : UserSessionCommand()

data class GetSessions(val replyTo: ActorRef<UserSessionResponse>) : UserSessionCommand()
data class SessionsResponse(val sessions: Map<String, WebSocketSession>) : UserSessionResponse()

data class Ping(val replyTo: ActorRef<UserSessionResponse>) : UserSessionCommand()
data class Pong(val message: String) : UserSessionResponse()

data class GetPersonalRoomActor(val identifier: String, val replyTo: ActorRef<UserSessionResponse>) : UserSessionCommand()

data class FoundPersonalRoomActor(val actorRef: ActorRef<PersonalRoomCommand>) : UserSessionResponse()


class UserSessionManagerActor private constructor(
    context: ActorContext<UserSessionCommand>
) : AbstractBehavior<UserSessionCommand>(context) {

    companion object {
        fun create(): Behavior<UserSessionCommand> {
            return Behaviors.setup { context -> UserSessionManagerActor(context) }
        }
    }

    private val sendService = SendService()

    private val logger = LoggerFactory.getLogger(UserSessionManagerActor::class.java)
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private val topicSubscriptions = ConcurrentHashMap<String, MutableSet<String>>()

    override fun createReceive(): Receive<UserSessionCommand> {
        return newReceiveBuilder()
            .onMessage(AddSession::class.java, this::onAddSession)
            .onMessage(RemoveSession::class.java, this::onRemoveSession)
            .onMessage(UpdateSession::class.java, this::onUpdateSession)
            .onMessage(SubscribeToTopic::class.java, this::onSubscribeToTopic)
            .onMessage(UnsubscribeFromTopic::class.java, this::onUnsubscribeFromTopic)
            .onMessage(SendMessageToSession::class.java, this::onSendMessageToSession)
            .onMessage(SendMessageToTopic::class.java, this::onSendMessageToTopic)
            .onMessage(SendMessageToAll::class.java, this::onSendMessageToAll)
            .onMessage(SendMessageToActor::class.java, this::onSendMessageToActor)
            .onMessage(GetSessions::class.java, this::onGetSessions)
            .onMessage(OnUserAction::class.java, this::onUserAction)
            .onMessage(GetPersonalRoomActor::class.java, this::onGetPersonalRoomActor)
            .onMessage(Ping::class.java, this::onPing)
            .build()
    }

    private fun onSendMessageToActor(sendMessageToActor: SendMessageToActor): Behavior<UserSessionCommand> {
        getPrivacyRoomActor(sendMessageToActor.identifier)?.tell(SendTextMessage(sendMessageToActor.message))
        return this
    }

    private fun onUserAction(command: OnUserAction): Behavior<UserSessionCommand> {
        when (command.action) {
            "cart" -> {
                sendService.sendEventTextMessage(
                    command.session, EventTextMessage(
                        type = MessageType.PUSH,
                        message = "장바구니에 상품을 담으셨군요 00한 상품은 어떤가요?",
                        from = MessageFrom.SYSTEM,
                        id = null,
                        jsondata = null,
                    )
                )
            }
            "buy" -> {
                sendService.sendEventTextMessage(
                    command.session, EventTextMessage(
                        type = MessageType.PUSH,
                        message = "상품을 구매하셨네요~",
                        from = MessageFrom.SYSTEM,
                        id = null,
                        jsondata = null,
                    )
                )
            }
        }
        return this
    }

    private fun onUpdateSession(command: UpdateSession): Behavior<UserSessionCommand> {
        sendService.sendEventTextMessage(
            command.session, EventTextMessage(
                type = MessageType.PUSH,
                message = "Welcome ${command.claims.nick}",
                from = MessageFrom.SYSTEM,
                id = null,
                jsondata = null,
            )
        )
        command.claims.identifier?.let { createPrivacyRoom(it, command.session) }
        return this
    }

    private fun onPing(command: Ping): Behavior<UserSessionCommand> {
        command.replyTo.tell(Pong("Pong"))
        return this
    }

    private fun onGetSessions(command: GetSessions): Behavior<UserSessionCommand> {
        command.replyTo.tell(SessionsResponse(sessions.toMap()))
        return this
    }

    private fun onAddSession(command: AddSession): Behavior<UserSessionCommand> {
        sessions[command.session.id] = command.session
        logger.info("Connected: ${command.session.id}")
        sendService.sendEventTextMessage(command.session, EventTextMessage(
            type = MessageType.SESSIONID,
            message = "Connected",
            from = MessageFrom.SYSTEM,
            id = command.session.id,
            jsondata = null,
        ))

        return this
    }

    private fun onRemoveSession(command: RemoveSession): Behavior<UserSessionCommand> {
        sessions.remove(command.session.id)
        logger.info("Disconnected: ${command.session.id}")

        // Remove the session from all topic subscriptions
        topicSubscriptions.forEach { (topic, sessionIds) ->
            sessionIds.remove(command.session.id)
            if (sessionIds.isEmpty()) {
                topicSubscriptions.remove(topic)
            }
        }

        getPrivacyRoomActor(command.session.id)?.tell(ClearSocketSession)

        return this
    }

    private fun onSendMessageToAll(command: SendMessageToAll): Behavior<UserSessionCommand> {
        sessions.values.forEach { session ->
            sendService.sendEventTextMessage(
                session, EventTextMessage(
                    type = MessageType.PUSH,
                    message = command.message,
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                )
            )
        }
        return this
    }

    private fun onSubscribeToTopic(command: SubscribeToTopic): Behavior<UserSessionCommand> {
        topicSubscriptions.computeIfAbsent(command.topic) { mutableSetOf() }.add(command.sessionId)
        logger.info("Session ${command.sessionId} subscribed to topic ${command.topic}")
        return this
    }

    private fun onUnsubscribeFromTopic(command: UnsubscribeFromTopic): Behavior<UserSessionCommand> {
        topicSubscriptions[command.topic]?.remove(command.sessionId)
        logger.info("Session ${command.sessionId} unsubscribed from topic ${command.topic}")
        return this
    }

    private fun onSendMessageToSession(command: SendMessageToSession): Behavior<UserSessionCommand> {
        sendService.sendEventTextMessage(
            sessions[command.sessionId]!!, EventTextMessage(
                type = MessageType.PUSH,
                message = command.message,
                from = MessageFrom.SYSTEM,
                id = null,
                jsondata = null,
            )
        )
        return this
    }

    private fun onSendMessageToTopic(command: SendMessageToTopic): Behavior<UserSessionCommand> {
        topicSubscriptions[command.topic]?.forEach { sessionId ->
            sendService.sendEventTextMessage(
                sessions[sessionId]!!, EventTextMessage(
                    type = MessageType.PUSH,
                    message = command.message,
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                )
            )
        }
        return this
    }

    private fun createPrivacyRoom(identifier: String, session: WebSocketSession) {
        val actorName = "PrivacyRoomActor-${identifier}"
        val roomActor = getPrivacyRoomActor(identifier)
        if(roomActor != null) {
            logger.info("PrivacyRoomActor already exists with identifier: $identifier")
            // Update Socket Session
            roomActor.tell(SetSocketSession(session))
            return
        }

        val childRoomActor = context.spawn(
            Behaviors.supervise(PersonalRoomActor.create(identifier))
                .onFailure(SupervisorStrategy.resume()),
            actorName
        )
        context.watch(childRoomActor)

        // Update Socket Session
        childRoomActor.tell(SetSocketSession(session))
        logger.info("PrivacyRoomActor created with identifier: $identifier")
    }

    private fun removePrivacyRoom(identifier: String) {
        val actorName = "PrivacyRoomActor-${identifier}"
        val actorRef = context.children.find { it.path().name() == actorName }
        actorRef?.let { context.stop(it) }
        logger.info("PrivacyRoomActor removed with identifier: $identifier")
    }

    private fun getPrivacyRoomActor(identifier: String): ActorRef<PersonalRoomCommand>? {
        val actorName = "PrivacyRoomActor-${identifier}"
        val actorRef = context.children.find { it.path().name() == actorName }?.unsafeUpcast<PersonalRoomCommand>()
        return actorRef
    }

    private fun onGetPersonalRoomActor(getPersonalRoomActor: GetPersonalRoomActor): Behavior<UserSessionCommand> {
        val actorRef = getPrivacyRoomActor(getPersonalRoomActor.identifier)
        getPersonalRoomActor.replyTo.tell(actorRef?.let { FoundPersonalRoomActor(it) })
        return this
    }
}