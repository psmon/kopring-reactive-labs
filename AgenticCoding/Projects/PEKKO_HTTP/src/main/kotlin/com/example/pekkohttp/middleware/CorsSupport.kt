package com.example.pekkohttp.middleware

import org.apache.pekko.http.javadsl.model.HttpHeader
import org.apache.pekko.http.javadsl.model.HttpResponse
import org.apache.pekko.http.javadsl.model.StatusCodes
import org.apache.pekko.http.javadsl.model.headers.RawHeader
import org.apache.pekko.http.javadsl.server.Directives.*
import org.apache.pekko.http.javadsl.server.Route
import java.util.*

object CorsSupport {
    
    // Create headers that allow any origin
    private fun createCorsHeaders(): List<HttpHeader> {
        return Arrays.asList(
            RawHeader.create("Access-Control-Allow-Origin", "*"),
            RawHeader.create("Access-Control-Allow-Credentials", "true"),
            RawHeader.create("Access-Control-Allow-Headers", 
                "Authorization, Content-Type, X-Requested-With, Accept, Origin, Access-Control-Request-Method, Access-Control-Request-Headers"),
            RawHeader.create("Access-Control-Allow-Methods", 
                "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH"),
            RawHeader.create("Access-Control-Max-Age", "3600")
        )
    }
    
    private val corsResponseHeaders = createCorsHeaders()
    
    fun corsHandler(route: Route): Route {
        return concat(
            // Handle preflight OPTIONS requests
            options {
                complete(
                    HttpResponse.create()
                        .withStatus(StatusCodes.OK)
                        .withHeaders(corsResponseHeaders)
                )
            },
            
            // Add CORS headers to all responses
            respondWithHeaders(corsResponseHeaders) {
                route
            }
        )
    }
    
    /**
     * Wraps a route with CORS support
     */
    fun withCors(route: Route): Route {
        return respondWithHeaders(corsResponseHeaders) {
            concat(
                options {
                    complete(StatusCodes.OK)
                },
                route
            )
        }
    }
}