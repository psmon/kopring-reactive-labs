package com.example.ssepushsystem.controller

import com.example.ssepushsystem.actor.TopicManagerActor
import com.example.ssepushsystem.actor.UserEventActor
import com.example.ssepushsystem.model.TopicEvent
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.UUID

@RestController
@RequestMapping("/api/sse")
@Tag(name = "SSE Controller", description = "Server-Sent Events streaming endpoints")
class SseController(
    private val userEventActor: ActorRef<UserEventActor.Command>,
    private val topicManagerActor: ActorRef<TopicManagerActor.Command>,
    private val actorSystem: ActorSystem<Nothing>
) {
    
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(
        summary = "Subscribe to SSE stream",
        description = "Establishes an SSE connection for receiving real-time events from subscribed topics"
    )
    @ApiResponse(responseCode = "200", description = "SSE stream established")
    suspend fun streamEvents(
        @Parameter(description = "User ID for the SSE connection", required = true)
        @RequestParam userId: String,
        @Parameter(description = "Comma-separated list of topics to subscribe to", required = false)
        @RequestParam(required = false) topics: String?
    ): Flux<ServerSentEvent<TopicEvent>> {
        
        val sink = Sinks.many().multicast().onBackpressureBuffer<TopicEvent>()
        
        // Connect user
        val connected = AskPattern.ask(
            userEventActor,
            { replyTo: ActorRef<UserEventActor.Connected> ->
                UserEventActor.Connect(userId, sink, replyTo)
            },
            Duration.ofSeconds(5),
            actorSystem.scheduler()
        ).await()
        
        // Subscribe to requested topics
        topics?.split(",")?.forEach { topic ->
            userEventActor.tell(UserEventActor.SubscribeToTopic(userId, topic.trim()))
        }
        
        // Get recent events for subscribed topics
        val topicSet = topics?.split(",")?.map { it.trim() }?.toSet() ?: emptySet()
        if (topicSet.isNotEmpty()) {
            val recentEvents = AskPattern.ask(
                topicManagerActor,
                { replyTo: ActorRef<List<TopicEvent>> ->
                    TopicManagerActor.GetRecentEvents(userId, topicSet, replyTo)
                },
                Duration.ofSeconds(5),
                actorSystem.scheduler()
            ).await()
            
            // Emit recent events
            recentEvents.forEach { event ->
                sink.tryEmitNext(event)
            }
        }
        
        // Create keepalive flux to send periodic heartbeats
        val keepAlive = Flux.interval(Duration.ofSeconds(30))
            .map {
                ServerSentEvent.builder<TopicEvent>()
                    .comment("keepalive")
                    .build()
            }
        
        // Create SSE flux
        val eventFlux = sink.asFlux()
            .map { event ->
                ServerSentEvent.builder<TopicEvent>()
                    .id(event.id)
                    .event(event.topic)
                    .data(event)
                    .build()
            }
        
        // Merge event flux with keepalive flux
        return Flux.merge(eventFlux, keepAlive)
            .doOnCancel {
                userEventActor.tell(UserEventActor.Disconnect(userId))
            }
            .doOnError { error ->
                userEventActor.tell(UserEventActor.Disconnect(userId))
            }
    }
    
    @PostMapping("/subscribe")
    @Operation(
        summary = "Subscribe to a topic",
        description = "Subscribe an active SSE connection to a specific topic"
    )
    @ApiResponse(responseCode = "200", description = "Successfully subscribed to topic")
    suspend fun subscribeToTopic(
        @Parameter(description = "User ID", required = true)
        @RequestParam userId: String,
        @Parameter(description = "Topic to subscribe to", required = true)
        @RequestParam topic: String
    ): Map<String, String> {
        userEventActor.tell(UserEventActor.SubscribeToTopic(userId, topic))
        return mapOf("status" to "subscribed", "userId" to userId, "topic" to topic)
    }
    
    @PostMapping("/unsubscribe")
    @Operation(
        summary = "Unsubscribe from a topic",
        description = "Unsubscribe an active SSE connection from a specific topic"
    )
    @ApiResponse(responseCode = "200", description = "Successfully unsubscribed from topic")
    suspend fun unsubscribeFromTopic(
        @Parameter(description = "User ID", required = true)
        @RequestParam userId: String,
        @Parameter(description = "Topic to unsubscribe from", required = true)
        @RequestParam topic: String
    ): Map<String, String> {
        userEventActor.tell(UserEventActor.UnsubscribeFromTopic(userId, topic))
        return mapOf("status" to "unsubscribed", "userId" to userId, "topic" to topic)
    }
    
    @GetMapping("/history/{topic}")
    @Operation(
        summary = "Get topic history",
        description = "Retrieve historical events for a specific topic"
    )
    @ApiResponse(responseCode = "200", description = "Topic history retrieved successfully")
    suspend fun getTopicHistory(
        @Parameter(description = "Topic name", required = true)
        @PathVariable topic: String
    ): Map<String, Any> {
        val history = AskPattern.ask(
            topicManagerActor,
            { replyTo: ActorRef<com.example.ssepushsystem.model.EventHistory> ->
                TopicManagerActor.GetTopicHistory(topic, replyTo)
            },
            Duration.ofSeconds(5),
            actorSystem.scheduler()
        ).await()
        
        return mapOf(
            "topic" to history.topic,
            "eventCount" to history.events.size,
            "events" to history.events
        )
    }
}