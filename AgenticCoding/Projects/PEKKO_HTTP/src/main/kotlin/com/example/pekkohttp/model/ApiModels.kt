package com.example.pekkohttp.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Hello request")
data class HelloRequest(
    @Schema(description = "Name to greet", example = "World")
    val name: String
)

@Schema(description = "Hello response")
data class HelloResponseDto(
    @Schema(description = "Greeting message", example = "Pekko says hello to World!")
    val message: String
)

@Schema(description = "User event request")
data class EventRequest(
    @Schema(description = "User ID", example = "user123")
    val userId: String,
    @Schema(description = "Event type", example = "click")
    val eventType: String,
    @Schema(description = "Action performed", example = "button_click")
    val action: String,
    @Schema(description = "Additional metadata")
    val metadata: Map<String, Any> = emptyMap()
)

@Schema(description = "Event response")
data class EventResponse(
    @Schema(description = "Success status")
    val success: Boolean,
    @Schema(description = "Response message")
    val message: String,
    @Schema(description = "Event ID if successful")
    val eventId: String? = null
)

@Schema(description = "WebSocket message")
data class WebSocketMessageDto(
    @Schema(description = "Message type", example = "chat")
    val type: String,
    @Schema(description = "Message content")
    val content: String,
    @Schema(description = "Sender ID")
    val sender: String? = null
)