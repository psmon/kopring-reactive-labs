package com.example.pekkohttp.actor

import com.example.pekkohttp.model.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

class WebSocketActor private constructor(
    context: ActorContext<WebSocketCommand>
) : AbstractBehavior<WebSocketCommand>(context) {

    companion object {
        fun create(): Behavior<WebSocketCommand> {
            return Behaviors.setup { context ->
                WebSocketActor(context)
            }
        }
    }

    private val connections = mutableMapOf<String, ActorRef<WSMessage>>()

    override fun createReceive(): Receive<WebSocketCommand> {
        return newReceiveBuilder()
            .onMessage(WSConnect::class.java, this::onConnect)
            .onMessage(WSMessage::class.java, this::onMessage)
            .onMessage(WSDisconnect::class.java, this::onDisconnect)
            .onMessage(WSBroadcast::class.java, this::onBroadcast)
            .build()
    }

    private fun onConnect(command: WSConnect): Behavior<WebSocketCommand> {
        context.log.info("WebSocket connected: ${command.connectionId}")
        connections[command.connectionId] = command.replyTo
        
        // Send welcome message
        command.replyTo.tell(WSMessage(
            connectionId = command.connectionId,
            message = "Welcome! You are connected with ID: ${command.connectionId}"
        ))
        
        // Notify other connections
        broadcastToOthers(
            command.connectionId,
            "User ${command.connectionId} has joined the chat"
        )
        
        return this
    }

    private fun onMessage(command: WSMessage): Behavior<WebSocketCommand> {
        context.log.info("WebSocket message from ${command.connectionId}: ${command.message}")
        
        // Echo back to sender
        connections[command.connectionId]?.tell(WSMessage(
            connectionId = "server",
            message = "Echo: ${command.message}"
        ))
        
        // Broadcast to others
        broadcastToOthers(
            command.connectionId,
            "[${command.connectionId}]: ${command.message}"
        )
        
        return this
    }

    private fun onDisconnect(command: WSDisconnect): Behavior<WebSocketCommand> {
        context.log.info("WebSocket disconnected: ${command.connectionId}")
        connections.remove(command.connectionId)
        
        // Notify other connections
        broadcastToOthers(
            command.connectionId,
            "User ${command.connectionId} has left the chat"
        )
        
        return this
    }

    private fun onBroadcast(command: WSBroadcast): Behavior<WebSocketCommand> {
        context.log.info("Broadcasting message: ${command.message}")
        
        connections.forEach { (id, ref) ->
            ref.tell(WSMessage(
                connectionId = "broadcast",
                message = command.message
            ))
        }
        
        return this
    }

    private fun broadcastToOthers(excludeId: String, message: String) {
        connections.forEach { (id, ref) ->
            if (id != excludeId) {
                ref.tell(WSMessage(
                    connectionId = "system",
                    message = message
                ))
            }
        }
    }
}