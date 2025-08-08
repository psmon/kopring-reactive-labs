package com.example.pekkohttp.routes

import com.example.pekkohttp.marshalling.JsonSupport
import com.example.pekkohttp.model.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.javadsl.model.ws.Message
import org.apache.pekko.http.javadsl.model.ws.TextMessage
import org.apache.pekko.http.javadsl.server.Directives.*
import org.apache.pekko.http.javadsl.server.PathMatchers.*
import org.apache.pekko.http.javadsl.server.Route
import org.apache.pekko.stream.javadsl.Flow
import java.util.UUID
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "WebSocket", description = "WebSocket endpoint for real-time communication")
class WebSocketRoute(
    private val webSocketActor: ActorRef<WebSocketCommand>,
    private val system: ActorSystem<*>
) {
    
    @Operation(
        summary = "WebSocket connection",
        description = "Establishes a WebSocket connection for real-time bidirectional communication"
    )
    fun createRoute(): Route {
        return concat(
            // WebSocket endpoint: /ws
            path("ws") {
                handleWebSocketMessages(webSocketFlow())
            },
            
            // WebSocket with path parameter: /ws/{userId}
            pathPrefix("ws") {
                path(segment()) { userId ->
                    handleWebSocketMessages(webSocketFlowWithId(userId))
                }
            },
            
            // Broadcast endpoint for sending messages to all connected clients
            path(segment("api").slash("broadcast")) {
                post {
                    entity(JsonSupport.unmarshaller<Map<String, String>>()) { body ->
                        val message = body["message"] ?: "No message"
                        webSocketActor.tell(WSBroadcast(message))
                        complete("Message broadcast to all connected clients")
                    }
                }
            }
        )
    }
    
    private fun webSocketFlow(): Flow<Message, Message, *> {
        val connectionId = UUID.randomUUID().toString()
        return webSocketFlowWithId(connectionId)
    }
    
    private fun webSocketFlowWithId(connectionId: String): Flow<Message, Message, *> {
        // Simple echo flow for WebSocket
        return Flow.of(Message::class.java)
            .mapConcat { msg ->
                if (msg.isText) {
                    val text = msg.asTextMessage().strictText
                    
                    // Notify actor of the message
                    webSocketActor.tell(WSMessage(connectionId, text))
                    
                    // Echo the message back
                    val response = WebSocketMessageDto(
                        type = "echo",
                        content = "Echo: $text",
                        sender = connectionId
                    )
                    
                    listOf(TextMessage.create(JsonSupport.objectMapper.writeValueAsString(response)))
                } else {
                    listOf(TextMessage.create("""{"error":"Only text messages supported"}"""))
                }
            }
    }
}