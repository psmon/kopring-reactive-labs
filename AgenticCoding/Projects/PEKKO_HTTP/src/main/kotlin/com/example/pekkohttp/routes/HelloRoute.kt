package com.example.pekkohttp.routes

import com.example.pekkohttp.marshalling.JsonSupport
import com.example.pekkohttp.model.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.http.javadsl.model.StatusCodes
import org.apache.pekko.http.javadsl.server.Directives.*
import org.apache.pekko.http.javadsl.server.PathMatchers.*
import org.apache.pekko.http.javadsl.server.Route
import java.time.Duration
import java.util.concurrent.CompletionStage
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag

@Path("/api/hello")
@Tag(name = "Hello", description = "Hello endpoint for testing Pekko actor communication")
class HelloRoute(
    private val helloActor: ActorRef<HelloCommand>,
    private val system: ActorSystem<*>
) {
    
    private val timeout = Duration.ofSeconds(3)
    
    @GET
    @Path("/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Get hello message",
        description = "Sends a hello request to the Pekko actor and returns the response"
    )
    @ApiResponse(responseCode = "200", description = "Successful response with greeting message")
    fun createRoute(): Route {
        return concat(
            // GET /api/hello/{name}
            path(segment("api").slash("hello").slash(segment())) { name ->
                get {
                    onSuccess(askHello(name)) { response ->
                        complete(
                            StatusCodes.OK,
                            HelloResponseDto(response.message),
                            JsonSupport.marshaller<HelloResponseDto>()
                        )
                    }
                }
            },
            
            // POST /api/hello
            path(segment("api").slash("hello")) {
                post {
                    entity(JsonSupport.unmarshaller<HelloRequest>()) { request ->
                        onSuccess(askHello(request.name)) { response ->
                            complete(
                                StatusCodes.OK,
                                HelloResponseDto(response.message),
                                JsonSupport.marshaller<HelloResponseDto>()
                            )
                        }
                    }
                }
            },
            
            // GET /api/hello (default)
            path(segment("api").slash("hello")) {
                get {
                    onSuccess(askHello("World")) { response ->
                        complete(
                            StatusCodes.OK,
                            HelloResponseDto(response.message),
                            JsonSupport.marshaller<HelloResponseDto>()
                        )
                    }
                }
            }
        )
    }
    
    private fun askHello(name: String): CompletionStage<HelloResponse> {
        return AskPattern.ask(
            helloActor,
            { replyTo: ActorRef<HelloResponse> -> GetHello(name, replyTo) },
            timeout,
            system.scheduler()
        )
    }
}