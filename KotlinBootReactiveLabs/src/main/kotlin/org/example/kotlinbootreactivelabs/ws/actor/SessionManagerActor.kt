package org.example.kotlinbootreactivelabs.ws.actor

import labs.common.model.EventTextMessage
import labs.common.model.MessageFrom
import labs.common.model.MessageType
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.example.kotlinbootreactivelabs.service.SendService
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap


sealed class UserSessionCommand {
    data class AddSession(val session: WebSocketSession, val replyTo: ActorRef<UserSessionCommandResponse>) : UserSessionCommand()
    data class RemoveSession(val session: WebSocketSession, val replyTo: ActorRef<UserSessionCommandResponse>) : UserSessionCommand()
    data class SubscribeToTopic(val sessionId: String, val topic: String, val replyTo: ActorRef<UserSessionCommandResponse>) : UserSessionCommand()
    data class UnsubscribeFromTopic(val sessionId: String, val topic: String, val replyTo: ActorRef<UserSessionCommandResponse>) : UserSessionCommand()
    data class SendMessageToSession(val sessionId: String, val message: String, val replyTo: ActorRef<UserSessionCommandResponse>) : UserSessionCommand()
    data class SendMessageToTopic(val topic: String, val message: String, val replyTo: ActorRef<UserSessionCommandResponse>) : UserSessionCommand()
}

sealed class UserSessionCommandResponse {
    data class Information(val message: String) : UserSessionCommandResponse()
}

class SessionManagerActor private constructor(
    private val context: ActorContext<UserSessionCommand>
) : AbstractBehavior<UserSessionCommand>(context)  {

    private val logger = LoggerFactory.getLogger(SessionManagerActor::class.java)
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private val topicSubscriptions = ConcurrentHashMap<String, MutableSet<String>>()

    private val sendService = SendService()

    companion object {
        fun create(): Behavior<UserSessionCommand> {
            return Behaviors.setup { context -> SessionManagerActor(context) }
        }
    }

    override fun createReceive(): Receive<UserSessionCommand> {
        return newReceiveBuilder()
            .onMessage(UserSessionCommand.AddSession::class.java, this::onAddSession)
            .onMessage(UserSessionCommand.RemoveSession::class.java, this::onRemoveSession)
            .onMessage(UserSessionCommand.SubscribeToTopic::class.java, this::onSubscribeToTopic)
            .onMessage(UserSessionCommand.UnsubscribeFromTopic::class.java, this::onUnsubscribeFromTopic)
            .onMessage(UserSessionCommand.SendMessageToSession::class.java, this::onSendMessageToSession)
            .onMessage(UserSessionCommand.SendMessageToTopic::class.java, this::onSendMessageToTopic)
            .build()
    }

    private fun onAddSession(command: UserSessionCommand.AddSession): Behavior<UserSessionCommand> {
        sessions[command.session.id] = command.session
        logger.info("[SessionManagerActor] Connected: ${command.session.id}")

        sendService.sendEventTextMessage(command.session, EventTextMessage(
            type = MessageType.INFO,
            message = "You are connected - ${command.session.id}",
            from = MessageFrom.SYSTEM,
            id = null,
            jsondata = null,
        ))

        command.replyTo.tell(UserSessionCommandResponse.Information("Session added ${command.session.id}"))

        return Behaviors.same()
    }

    private fun onRemoveSession(command: UserSessionCommand.RemoveSession): Behavior<UserSessionCommand> {
        sessions.remove(command.session.id)
        logger.info("[SessionManagerActor] Disconnected: ${command.session.id}")

        command.replyTo.tell(UserSessionCommandResponse.Information("Session removed ${command.session.id}"))

        return Behaviors.same()
    }

    private fun onSubscribeToTopic(command: UserSessionCommand.SubscribeToTopic): Behavior<UserSessionCommand> {
        topicSubscriptions.computeIfAbsent(command.topic) { mutableSetOf() }.add(command.sessionId)
        logger.info("Session ${command.sessionId} subscribed to topic ${command.topic}")

        sessions[command.sessionId]?.let {
            sendService.sendEventTextMessage(
                it, EventTextMessage(
                    type = MessageType.PUSH,
                    message = "You are subscribed to topic ${command.topic}",
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                )
            )
        }

        command.replyTo.tell(UserSessionCommandResponse.Information("Subscribed to topic ${command.topic}"))

        return Behaviors.same()
    }

    private fun onUnsubscribeFromTopic(command: UserSessionCommand.UnsubscribeFromTopic): Behavior<UserSessionCommand> {
        topicSubscriptions[command.topic]?.remove(command.sessionId)
        logger.info("Session ${command.sessionId} unsubscribed from topic ${command.topic}")

        sessions[command.sessionId]?.let {
            sendService.sendEventTextMessage(
                it, EventTextMessage(
                    type = MessageType.PUSH,
                    message = "You are unsubscribed to topic ${command.topic}",
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                )
            )
        }

        command.replyTo.tell(UserSessionCommandResponse.Information("Unsubscribed from topic ${command.topic}"))

        return Behaviors.same()
    }

    private fun onSendMessageToSession(command: UserSessionCommand.SendMessageToSession): Behavior<UserSessionCommand> {
        sessions[command.sessionId]?.let {
            logger.info("Sending message to session ${command.sessionId}: ${command.message}")
            sendService.sendEventTextMessage(
                it, EventTextMessage(
                    type = MessageType.PUSH,
                    message = command.message,
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                )
            )
        }

        command.replyTo.tell(UserSessionCommandResponse.Information("Message sent to session ${command.sessionId}"))

        return Behaviors.same()
    }

    private fun onSendMessageToTopic(command: UserSessionCommand.SendMessageToTopic): Behavior<UserSessionCommand> {
        topicSubscriptions[command.topic]?.forEach { sessionId ->
            sessions[sessionId]?.let {
                logger.info("Sending message to topic ${command.topic}: ${command.message}")
                sendService.sendEventTextMessage(
                    it, EventTextMessage(
                        type = MessageType.PUSH,
                        message = command.message,
                        from = MessageFrom.SYSTEM,
                        id = null,
                        jsondata = null,
                    )
                )
            }
        }

        command.replyTo.tell(UserSessionCommandResponse.Information("Message sent to topic ${command.topic}"))

        return Behaviors.same()
    }
}