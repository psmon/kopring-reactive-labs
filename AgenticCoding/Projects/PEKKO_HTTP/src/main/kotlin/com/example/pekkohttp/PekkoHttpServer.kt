package com.example.pekkohttp

import com.example.pekkohttp.actor.*
import com.example.pekkohttp.model.*
import com.example.pekkohttp.routes.*
import com.example.pekkohttp.middleware.CorsSupport
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.http.javadsl.Http
import org.apache.pekko.http.javadsl.ServerBinding
import org.apache.pekko.http.javadsl.server.Directives.*
import org.apache.pekko.http.javadsl.server.Route
import org.apache.pekko.http.javadsl.model.StatusCodes
import org.apache.pekko.http.javadsl.model.HttpRequest
import org.apache.pekko.http.javadsl.model.HttpResponse
import org.apache.pekko.http.javadsl.settings.ServerSettings
import org.apache.pekko.http.javadsl.settings.WebSocketSettings
import org.apache.pekko.stream.Materializer
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletionStage
import java.time.Duration

object PekkoHttpServer {
    private val logger = LoggerFactory.getLogger(PekkoHttpServer::class.java)
    
    @JvmStatic
    fun main(args: Array<String>) {
        val port = args.firstOrNull()?.toIntOrNull() ?: 8080
        val host = "0.0.0.0"
        
        // Create the main actor system
        val system = ActorSystem.create(createRootBehavior(), "PekkoHttpSystem")
        
        // Create actors
        val helloActor = system.systemActorOf(HelloActor.create(), "helloActor", org.apache.pekko.actor.typed.Props.empty())
        val eventActor = system.systemActorOf(EventStreamActor.create(), "eventActor", org.apache.pekko.actor.typed.Props.empty())
        val webSocketActor = system.systemActorOf(WebSocketActor.create(), "webSocketActor", org.apache.pekko.actor.typed.Props.empty())
        
        // Create routes
        val helloRoute = HelloRoute(helloActor, system)
        val eventRoute = EventRoute(eventActor, system)
        val webSocketRoute = WebSocketRoute(webSocketActor, system)
        val swaggerRoute = SwaggerRoute(system)
        val staticRoute = StaticRoute(system)
        
        // Combine all routes with CORS support
        val allRoutes = CorsSupport.withCors(
            createRoutes(helloRoute, eventRoute, webSocketRoute, swaggerRoute, staticRoute)
        )
        
        // Server configuration handled via application.conf
        
        // Start HTTP server
        val http = Http.get(system)
        val bindingFuture: CompletionStage<ServerBinding> = http.newServerAt(host, port)
            .bind(allRoutes)
        
        bindingFuture.whenComplete { binding, throwable ->
            if (binding != null) {
                logger.info("========================================")
                logger.info("Pekko HTTP Server started successfully!")
                logger.info("========================================")
                logger.info("Server URL: http://{}:{}", host, port)
                logger.info("Test Page:  http://{}:{}/test", host, port)
                logger.info("Swagger UI: http://{}:{}/swagger-ui", host, port)
                logger.info("API Docs:   http://{}:{}/api-docs", host, port)
                logger.info("Health:     http://{}:{}/health", host, port)
                logger.info("========================================")
                logger.info("Press CTRL+C to stop...")
                
                // Add shutdown hook
                Runtime.getRuntime().addShutdownHook(Thread {
                    logger.info("Shutting down Pekko HTTP Server...")
                    binding.unbind()
                        .thenAccept { system.terminate() }
                        .exceptionally { ex ->
                            logger.error("Error during shutdown", ex)
                            system.terminate()
                            null
                        }
                })
            } else {
                logger.error("Failed to bind HTTP server", throwable)
                system.terminate()
            }
        }
    }
    
    private fun createRootBehavior(): Behavior<Void> {
        return Behaviors.setup { context ->
            context.log.info("Pekko HTTP System initialized")
            Behaviors.empty()
        }
    }
    
    private fun createRoutes(
        helloRoute: HelloRoute,
        eventRoute: EventRoute,
        webSocketRoute: WebSocketRoute,
        swaggerRoute: SwaggerRoute,
        staticRoute: StaticRoute
    ): Route {
        return concat(
            // Health check endpoint
            path("health") {
                get {
                    complete(StatusCodes.OK, """{"status":"UP","service":"pekko-http-server"}""")
                }
            },
            
            // Root endpoint
            pathEndOrSingleSlash {
                get {
                    complete(StatusCodes.OK, """
                        |Welcome to Pekko HTTP Server!
                        |
                        |Available endpoints:
                        |- GET    /health              - Health check
                        |- GET    /test                - Interactive test page for SSE & WebSocket
                        |- GET    /api/hello           - Hello endpoint (default)
                        |- GET    /api/hello/{name}    - Hello endpoint with name
                        |- POST   /api/hello           - Hello endpoint with JSON body
                        |- POST   /api/events          - Send user event
                        |- POST   /api/events/batch    - Send batch of events
                        |- GET    /api/events/stats    - Get event statistics
                        |- GET    /api/events/stream   - SSE event stream
                        |- WS     /ws                  - WebSocket endpoint
                        |- WS     /ws/{userId}         - WebSocket with user ID
                        |- POST   /api/broadcast       - Broadcast message to all WS clients
                        |- GET    /swagger-ui          - Swagger UI documentation
                        |- GET    /api-docs            - OpenAPI specification
                    """.trimMargin())
                }
            },
            
            // API routes
            helloRoute.createRoute(),
            eventRoute.createRoute(),
            webSocketRoute.createRoute(),
            swaggerRoute.createRoute(),
            staticRoute.createRoute(),
            
            // Catch-all for undefined routes
            complete(StatusCodes.NOT_FOUND, """{"error":"Route not found"}""")
        )
    }
}