package org.example.kotlinbootreactivelabs.ws.actor.basic

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


sealed class SimpleSessionCommand {
    data class SimpleAddSession(val session: WebSocketSession, val replyTo: ActorRef<SimpleUserSessionCommandResponse>) : SimpleSessionCommand()
    data class SimpleRemoveSession(val session: WebSocketSession, val replyTo: ActorRef<SimpleUserSessionCommandResponse>) : SimpleSessionCommand()
    data class SimpleSubscribeToTopic(val sessionId: String, val topic: String, val replyTo: ActorRef<SimpleUserSessionCommandResponse>) : SimpleSessionCommand()
    data class SimpleUnsubscribeFromTopic(val sessionId: String, val topic: String, val replyTo: ActorRef<SimpleUserSessionCommandResponse>) : SimpleSessionCommand()
    data class SimpleSendMessageToSession(val sessionId: String, val message: String, val replyTo: ActorRef<SimpleUserSessionCommandResponse>) : SimpleSessionCommand()
    data class SimpleSendMessageToTopic(val topic: String, val message: String, val replyTo: ActorRef<SimpleUserSessionCommandResponse>) : SimpleSessionCommand()
}

sealed class SimpleUserSessionCommandResponse {
    data class SimpleInformation(val message: String) : SimpleUserSessionCommandResponse()
}

class SimpleSessionManagerActor private constructor(
    private val context: ActorContext<SimpleSessionCommand>
) : AbstractBehavior<SimpleSessionCommand>(context)  {

    private val logger = LoggerFactory.getLogger(SimpleSessionManagerActor::class.java)
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private val topicSubscriptions = ConcurrentHashMap<String, MutableSet<String>>()

    private val sendService = SendService()

    companion object {
        fun create(): Behavior<SimpleSessionCommand> {
            return Behaviors.setup { context -> SimpleSessionManagerActor(context) }
        }
    }

    override fun createReceive(): Receive<SimpleSessionCommand> {
        return newReceiveBuilder()
            .onMessage(SimpleSessionCommand.SimpleAddSession::class.java, this::onAddSession)
            .onMessage(SimpleSessionCommand.SimpleRemoveSession::class.java, this::onRemoveSession)
            .onMessage(SimpleSessionCommand.SimpleSubscribeToTopic::class.java, this::onSubscribeToTopic)
            .onMessage(SimpleSessionCommand.SimpleUnsubscribeFromTopic::class.java, this::onUnsubscribeFromTopic)
            .onMessage(SimpleSessionCommand.SimpleSendMessageToSession::class.java, this::onSendMessageToSession)
            .onMessage(SimpleSessionCommand.SimpleSendMessageToTopic::class.java, this::onSendMessageToTopic)
            .build()
    }

    private fun onAddSession(command: SimpleSessionCommand.SimpleAddSession): Behavior<SimpleSessionCommand> {
        sessions[command.session.id] = command.session
        logger.info("[SessionManagerActor] Connected: ${command.session.id}")

        sendService.sendEventTextMessage(command.session, EventTextMessage(
            type = MessageType.INFO,
            message = "You are connected - ${command.session.id}",
            from = MessageFrom.SYSTEM,
            id = null,
            jsondata = null,
        ))

        command.replyTo.tell(SimpleUserSessionCommandResponse.SimpleInformation("Session added ${command.session.id}"))

        return Behaviors.same()
    }

    private fun onRemoveSession(command: SimpleSessionCommand.SimpleRemoveSession): Behavior<SimpleSessionCommand> {
        sessions.remove(command.session.id)
        logger.info("[SessionManagerActor] Disconnected: ${command.session.id}")

        command.replyTo.tell(SimpleUserSessionCommandResponse.SimpleInformation("Session removed ${command.session.id}"))

        return Behaviors.same()
    }

    private fun onSubscribeToTopic(command: SimpleSessionCommand.SimpleSubscribeToTopic): Behavior<SimpleSessionCommand> {
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

        command.replyTo.tell(SimpleUserSessionCommandResponse.SimpleInformation("Subscribed to topic ${command.topic}"))

        return Behaviors.same()
    }

    private fun onUnsubscribeFromTopic(command: SimpleSessionCommand.SimpleUnsubscribeFromTopic): Behavior<SimpleSessionCommand> {
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

        command.replyTo.tell(SimpleUserSessionCommandResponse.SimpleInformation("Unsubscribed from topic ${command.topic}"))

        return Behaviors.same()
    }

    private fun onSendMessageToSession(command: SimpleSessionCommand.SimpleSendMessageToSession): Behavior<SimpleSessionCommand> {
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

        command.replyTo.tell(SimpleUserSessionCommandResponse.SimpleInformation("Message sent to session ${command.sessionId}"))

        return Behaviors.same()
    }

    private fun onSendMessageToTopic(command: SimpleSessionCommand.SimpleSendMessageToTopic): Behavior<SimpleSessionCommand> {
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

        command.replyTo.tell(SimpleUserSessionCommandResponse.SimpleInformation("Message sent to topic ${command.topic}"))

        return Behaviors.same()
    }
}