package org.example.kotlinbootreactivelabs.ws.base

import labs.common.model.EventTextMessage
import labs.common.model.MessageFrom
import labs.common.model.MessageType
import org.example.kotlinbootreactivelabs.service.SendService
import org.springframework.web.reactive.socket.WebSocketSession as ReactiveWebSocketSession

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap


@Component
class SessionManager(private val sendService : SendService) {

    private val logger = LoggerFactory.getLogger(SessionManager::class.java)

    val reactiveSessions = ConcurrentHashMap<String, ReactiveWebSocketSession>()

    val topicSubscriptions = ConcurrentHashMap<String, MutableSet<String>>()

    fun addSession(session: ReactiveWebSocketSession) {
        reactiveSessions[session.id] = session
        logger.info("[SessionManager] Connected: ${session.id}")

        sendService.sendEventTextMessage(session, EventTextMessage(
            type = MessageType.INFO,
            message = "You are connected - ${session.id}",
            from = MessageFrom.SYSTEM,
            id = null,
            jsondata = null,
        ))
    }

    fun removeSession(session: ReactiveWebSocketSession) {
        reactiveSessions.remove(session.id)
        logger.info("[SessionManager] Disconnected: ${session.id}")
    }

    fun subscribeReactiveToTopic(sessionId: String, topic: String) {
        topicSubscriptions.computeIfAbsent(topic) { mutableSetOf() }.add(sessionId)
        logger.info("Session $sessionId subscribed to topic $topic")

        reactiveSessions[sessionId]?.let {
            sendService.sendEventTextMessage(
                it, EventTextMessage(
                    type = MessageType.PUSH,
                    message = "You are subscribed to topic $topic",
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                )
            )
        }
    }

    fun unsubscribeReactiveFromTopic(sessionId: String, topic: String) {
        topicSubscriptions[topic]?.remove(sessionId)
        logger.info("Session $sessionId unsubscribed from topic $topic")

        reactiveSessions[sessionId]?.let {
            sendService.sendEventTextMessage(
                it, EventTextMessage(
                    type = MessageType.PUSH,
                    message = "You are unsubscribed to topic $topic",
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                )
            )
        }
    }

    fun sendReactiveMessageToSession(sessionId: String, message: String) {
        reactiveSessions[sessionId]?.let {
            sendService.sendEventTextMessage(
                it, EventTextMessage(
                    type = MessageType.PUSH,
                    message = message,
                    from = MessageFrom.SYSTEM,
                    id = null,
                    jsondata = null,
                )
            )
        }
    }

    fun sendReactiveMessageToTopic(topic: String, message: String) {
        topicSubscriptions[topic]?.forEach { sessionId ->
            reactiveSessions[sessionId]?.let {
                sendService.sendEventTextMessage(
                    it, EventTextMessage(
                        type = MessageType.PUSH,
                        message = message,
                        from = MessageFrom.SYSTEM,
                        id = null,
                        jsondata = null,
                    )
                )
            }
        }
    }
}