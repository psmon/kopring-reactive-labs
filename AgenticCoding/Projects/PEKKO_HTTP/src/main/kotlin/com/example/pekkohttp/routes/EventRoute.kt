package com.example.pekkohttp.routes

import com.example.pekkohttp.marshalling.JsonSupport
import com.example.pekkohttp.model.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.http.javadsl.model.StatusCodes
import org.apache.pekko.http.javadsl.model.sse.ServerSentEvent
import org.apache.pekko.http.javadsl.server.Directives.*
import org.apache.pekko.http.javadsl.server.PathMatchers.*
import org.apache.pekko.http.javadsl.server.Route
import org.apache.pekko.http.javadsl.marshalling.sse.EventStreamMarshalling
import org.apache.pekko.stream.javadsl.Source
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletionStage
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag

@Path("/api/events")
@Tag(name = "Events", description = "Event streaming and processing endpoints")
class EventRoute(
    private val eventActor: ActorRef<EventCommand>,
    private val system: ActorSystem<*>
) {
    
    private val timeout = Duration.ofSeconds(5)
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Send user event",
        description = "Sends a user event to the event stream actor for processing"
    )
    @ApiResponse(responseCode = "202", description = "Event accepted for processing")
    fun createRoute(): Route {
        return concat(
            // POST /api/events
            path(segment("api").slash("events")) {
                post {
                    entity(JsonSupport.unmarshaller<EventRequest>()) { request ->
                        val event = UserEvent(
                            userId = request.userId,
                            eventType = request.eventType,
                            action = request.action,
                            metadata = request.metadata
                        )
                        
                        eventActor.tell(ProcessEvent(event))
                        
                        val response = EventResponse(
                            success = true,
                            message = "Event accepted for processing",
                            eventId = UUID.randomUUID().toString()
                        )
                        
                        complete(
                            StatusCodes.ACCEPTED,
                            response,
                            JsonSupport.marshaller<EventResponse>()
                        )
                    }
                }
            },
            
            // GET /api/events/stats
            path(segment("api").slash("events").slash("stats")) {
                get {
                    onSuccess(getEventStats()) { stats ->
                        complete(
                            StatusCodes.OK,
                            stats,
                            JsonSupport.marshaller<EventStats>()
                        )
                    }
                }
            },
            
            // GET /api/events/stream (simplified for now)
            path(segment("api").slash("events").slash("stream")) {
                get {
                    // Simplified version - returning current stats instead of SSE stream
                    onSuccess(getEventStats()) { stats ->
                        complete(
                            StatusCodes.OK,
                            stats,
                            JsonSupport.marshaller<EventStats>()
                        )
                    }
                }
            },
            
            // POST /api/events/batch
            path(segment("api").slash("events").slash("batch")) {
                post {
                    entity(JsonSupport.unmarshaller<List<EventRequest>>()) { requests ->
                        requests.forEach { request ->
                            val event = UserEvent(
                                userId = request.userId,
                                eventType = request.eventType,
                                action = request.action,
                                metadata = request.metadata
                            )
                            eventActor.tell(ProcessEvent(event))
                        }
                        
                        val response = mapOf(
                            "success" to true,
                            "message" to "Batch of ${requests.size} events accepted for processing"
                        )
                        
                        complete(
                            StatusCodes.ACCEPTED,
                            response,
                            JsonSupport.marshaller<Map<String, Any>>()
                        )
                    }
                }
            }
        )
    }
    
    private fun getEventStats(): CompletionStage<EventStats> {
        return AskPattern.ask(
            eventActor,
            { replyTo: ActorRef<EventStats> -> GetEventStats(replyTo) },
            timeout,
            system.scheduler()
        )
    }
    
    private fun createEventStream(): Source<ServerSentEvent, *> {
        return Source.tick(
            Duration.ofSeconds(0),
            Duration.ofSeconds(2),
            "tick"
        ).mapAsync(1) {
            getEventStats()
        }.map { stats ->
            val json = JsonSupport.objectMapper.writeValueAsString(stats)
            ServerSentEvent.create(json)
        }.keepAlive(Duration.ofSeconds(30)) {
            ServerSentEvent.heartbeat()
        }
    }
}