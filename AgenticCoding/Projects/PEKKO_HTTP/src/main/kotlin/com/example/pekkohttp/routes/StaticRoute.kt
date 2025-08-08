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
        val inputStream: InputStream? = javaClass.getResourceAsStream(resourcePath)
        return if (inputStream != null) {
            val content = inputStream.bufferedReader().use { it.readText() }
            complete(
                HttpResponse.create()
                    .withStatus(StatusCodes.OK)
                    .withEntity(HttpEntities.create(ContentTypes.TEXT_HTML_UTF8, content))
            )
        } else {
            complete(
                HttpResponse.create()
                    .withStatus(StatusCodes.NOT_FOUND)
                    .withEntity("File not found: $resourcePath")
            )
        }
    }
    
    private fun serveStaticFile(resourcePath: String, contentType: ContentType): Route {
        val inputStream: InputStream? = javaClass.getResourceAsStream(resourcePath)
        return if (inputStream != null) {
            val bytes = inputStream.readBytes()
            complete(
                HttpResponse.create()
                    .withStatus(StatusCodes.OK)
                    .withEntity(HttpEntities.create(contentType, bytes))
            )
        } else {
            complete(
                HttpResponse.create()
                    .withStatus(StatusCodes.NOT_FOUND)
                    .withEntity("File not found: $resourcePath")
            )
        }
    }
}