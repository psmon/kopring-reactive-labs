package com.example.ssepushsystem.controller

import com.example.ssepushsystem.actor.TopicManagerActor
import com.example.ssepushsystem.model.TopicEvent
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.future.await
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.Duration
import java.util.UUID

@RestController
@RequestMapping("/api/push")
@Tag(name = "Push Controller", description = "Endpoints for publishing events to topics")
class PushController(
    private val topicManagerActor: ActorRef<TopicManagerActor.Command>,
    private val actorSystem: ActorSystem<Nothing>
) {
    
    data class PushEventRequest(
        @field:Schema(description = "Topic to publish to", example = "news", required = true)
        val topic: String,
        
        @field:Schema(description = "Event data", example = "Breaking news: Important update!", required = true)
        val data: String,
        
        @field:Schema(description = "Optional event ID. If not provided, a UUID will be generated", example = "event-123", required = false)
        val eventId: String? = null
    )
    
    data class PushEventResponse(
        @field:Schema(description = "Status of the operation", example = "success")
        val status: String,
        
        @field:Schema(description = "Published event details")
        val event: TopicEvent,
        
        @field:Schema(description = "Additional message", example = "Event published successfully")
        val message: String
    )
    
    @PostMapping("/event")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Publish an event to a topic",
        description = "Publishes a new event to the specified topic. The event will be delivered to all subscribers of that topic."
    )
    @ApiResponse(
        responseCode = "201",
        description = "Event published successfully",
        content = [Content(schema = Schema(implementation = PushEventResponse::class))]
    )
    @ApiResponse(responseCode = "400", description = "Invalid request")
    suspend fun publishEvent(
        @RequestBody request: PushEventRequest
    ): PushEventResponse {
        val event = TopicEvent(
            id = request.eventId ?: UUID.randomUUID().toString(),
            topic = request.topic,
            data = request.data
        )
        
        val result = AskPattern.ask(
            topicManagerActor,
            { replyTo: ActorRef<TopicManagerActor.EventPublished> ->
                TopicManagerActor.PublishEvent(event, replyTo)
            },
            Duration.ofSeconds(5),
            actorSystem.scheduler()
        ).await()
        
        return if (result.success) {
            PushEventResponse(
                status = "success",
                event = result.event,
                message = "Event published successfully to topic '${request.topic}'"
            )
        } else {
            throw RuntimeException("Failed to publish event")
        }
    }
    
    @PostMapping("/events/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Publish multiple events",
        description = "Publishes multiple events to their respective topics in a single request"
    )
    @ApiResponse(
        responseCode = "201",
        description = "Events published successfully"
    )
    suspend fun publishBatchEvents(
        @RequestBody requests: List<PushEventRequest>
    ): Map<String, Any> {
        val results = mutableListOf<PushEventResponse>()
        var successCount = 0
        var failureCount = 0
        
        requests.forEach { request ->
            try {
                val response = publishEvent(request)
                results.add(response)
                successCount++
            } catch (e: Exception) {
                failureCount++
                results.add(
                    PushEventResponse(
                        status = "failed",
                        event = TopicEvent(
                            id = request.eventId ?: UUID.randomUUID().toString(),
                            topic = request.topic,
                            data = request.data
                        ),
                        message = "Failed to publish: ${e.message}"
                    )
                )
            }
        }
        
        return mapOf(
            "totalEvents" to requests.size,
            "successCount" to successCount,
            "failureCount" to failureCount,
            "results" to results
        )
    }
    
    @PostMapping("/broadcast")
    @Operation(
        summary = "Broadcast to multiple topics",
        description = "Publishes the same event data to multiple topics simultaneously"
    )
    @ApiResponse(
        responseCode = "201",
        description = "Event broadcasted successfully"
    )
    suspend fun broadcastEvent(
        @Parameter(description = "Comma-separated list of topics", required = true)
        @RequestParam topics: String,
        @Parameter(description = "Event data to broadcast", required = true)
        @RequestParam data: String
    ): Map<String, Any> {
        val topicList = topics.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val eventId = UUID.randomUUID().toString()
        
        val requests = topicList.map { topic ->
            PushEventRequest(
                topic = topic,
                data = data,
                eventId = "$eventId-$topic"
            )
        }
        
        return publishBatchEvents(requests)
    }
}