package com.example.pekkohttp.routes

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.javadsl.model.*
import org.apache.pekko.http.javadsl.server.Directives.*
import org.apache.pekko.http.javadsl.server.Route
import java.io.InputStream

class StaticRoute(private val system: ActorSystem<*>) {
    
    fun createRoute(): Route {
        return concat(
            // Test page
            path("test") {
                get {
                    serveHtmlFile("/test.html")
                }
            },
            path("test.html") {
                get {
                    serveHtmlFile("/test.html")
                }
            },
            
            // Swagger UI resources
            pathPrefix("swagger") {
                concat(
                    path("index.html") {
                        get {
                            serveHtmlFile("/swagger/index.html")
                        }
                    }
                )
            }
        )
    }
    
    private fun serveHtmlFile(resourcePath: String): Route {
        return complete {
            val inputStream: InputStream? = javaClass.getResourceAsStream(resourcePath)
            if (inputStream != null) {
                val content = inputStream.bufferedReader().use { it.readText() }
                HttpResponse.create()
                    .withStatus(StatusCodes.OK)
                    .withEntity(HttpEntities.create(ContentTypes.TEXT_HTML_UTF8, content))
            } else {
                HttpResponse.create()
                    .withStatus(StatusCodes.NOT_FOUND)
                    .withEntity("File not found: $resourcePath")
            }
        }
    }
    
    private fun serveStaticFile(resourcePath: String, contentType: ContentType): Route {
        return complete {
            val inputStream: InputStream? = javaClass.getResourceAsStream(resourcePath)
            if (inputStream != null) {
                val bytes = inputStream.readBytes()
                HttpResponse.create()
                    .withStatus(StatusCodes.OK)
                    .withEntity(HttpEntities.create(contentType, bytes))
            } else {
                HttpResponse.create()
                    .withStatus(StatusCodes.NOT_FOUND)
                    .withEntity("File not found: $resourcePath")
            }
        }
    }
}