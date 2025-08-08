package com.example.pekkohttp.model

import org.apache.pekko.actor.typed.ActorRef
import java.io.Serializable

sealed interface HelloCommand : Serializable
data class GetHello(val name: String, val replyTo: ActorRef<HelloResponse>) : HelloCommand
data class HelloResponse(val message: String) : HelloCommand

sealed interface EventCommand : Serializable
data class ProcessEvent(val event: UserEvent) : EventCommand
data class GetEventStats(val replyTo: ActorRef<EventStats>) : EventCommand
data class StreamComplete(val processed: Int) : EventCommand

sealed interface WebSocketCommand : Serializable
data class WSConnect(val connectionId: String, val replyTo: ActorRef<WSMessage>) : WebSocketCommand
data class WSMessage(val connectionId: String, val message: String) : WebSocketCommand
data class WSDisconnect(val connectionId: String) : WebSocketCommand
data class WSBroadcast(val message: String) : WebSocketCommand