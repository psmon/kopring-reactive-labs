package com.example.pekkohttp.routes

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.javadsl.model.*
import org.apache.pekko.http.javadsl.server.Directives.*
import org.apache.pekko.http.javadsl.server.Route
import java.nio.charset.StandardCharsets

class SwaggerRoute(private val system: ActorSystem<*>) {
    
    fun createRoute(): Route {
        return concat(
            // Swagger UI HTML page
            pathSingleSlash {
                get {
                    redirect(Uri.create("/swagger-ui"), StatusCodes.MOVED_PERMANENTLY)
                }
            },
            path("swagger-ui") {
                get {
                    val html = generateSwaggerHtml()
                    complete(
                        HttpResponse.create()
                            .withStatus(StatusCodes.OK)
                            .withEntity(HttpEntities.create(ContentTypes.TEXT_HTML_UTF8, html))
                    )
                }
            },
            
            // Alternative swagger endpoints
            path("swagger") {
                get {
                    redirect(Uri.create("/swagger-ui"), StatusCodes.MOVED_PERMANENTLY)
                }
            },
            
            // OpenAPI spec JSON
            pathPrefix("swagger") {
                path("api-docs") {
                    get {
                        val json = generateOpenApiSpec()
                        complete(
                            HttpResponse.create()
                                .withStatus(StatusCodes.OK)
                                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, json))
                        )
                    }
                }
            },
            
            // Alternative API docs endpoints
            path("api-docs") {
                get {
                    val json = generateOpenApiSpec()
                    complete(
                        HttpResponse.create()
                            .withStatus(StatusCodes.OK)
                            .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, json))
                    )
                }
            },
            path("swagger.json") {
                get {
                    val json = generateOpenApiSpec()
                    complete(
                        HttpResponse.create()
                            .withStatus(StatusCodes.OK)
                            .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, json))
                    )
                }
            }
        )
    }
    
    private fun generateSwaggerHtml(): String {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>Pekko HTTP API Documentation</title>
            <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.9.0/swagger-ui.css">
            <style>
                body { margin: 0; padding: 0; font-family: sans-serif; }
                #swagger-ui { padding: 20px; }
            </style>
        </head>
        <body>
            <div id="swagger-ui"></div>
            <script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.9.0/swagger-ui-bundle.js"></script>
            <script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.9.0/swagger-ui-standalone-preset.js"></script>
            <script>
                window.onload = function() {
                    SwaggerUIBundle({
                        url: "/swagger/api-docs",
                        dom_id: '#swagger-ui',
                        deepLinking: true,
                        presets: [
                            SwaggerUIBundle.presets.apis,
                            SwaggerUIStandalonePreset
                        ],
                        plugins: [
                            SwaggerUIBundle.plugins.DownloadUrl
                        ],
                        layout: "StandaloneLayout",
                        tryItOutEnabled: true
                    });
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }
    
    private fun generateOpenApiSpec(): String {
        // Convert to JSON (simplified - in production, use proper OpenAPI generator)
        return """
        {
            "openapi": "3.0.0",
            "info": {
                "title": "Pekko HTTP API",
                "version": "1.0.0",
                "description": "Pekko HTTP Server API Documentation - A reactive web service built with Pekko HTTP and Actors",
                "contact": {
                    "name": "API Support",
                    "email": "api@example.com"
                },
                "license": {
                    "name": "Apache 2.0",
                    "url": "https://www.apache.org/licenses/LICENSE-2.0.html"
                }
            },
            "servers": [
                {
                    "url": "http://localhost:8080",
                    "description": "Local development server"
                }
            ],
            "tags": [
                {
                    "name": "Hello",
                    "description": "Hello World endpoints demonstrating basic actor communication"
                },
                {
                    "name": "Events",
                    "description": "Event streaming and processing with Pekko Streams"
                },
                {
                    "name": "WebSocket",
                    "description": "Real-time bidirectional communication"
                }
            ],
            "paths": {
                "/api/hello": {
                    "get": {
                        "tags": ["Hello"],
                        "summary": "Get default hello message",
                        "responses": {
                            "200": {
                                "description": "Successful response",
                                "content": {
                                    "application/json": {
                                        "schema": {
                                            "type": "object",
                                            "properties": {
                                                "message": {
                                                    "type": "string",
                                                    "example": "Pekko says hello to World!"
                                                }
                                            }
                                        },
                                        "example": {
                                            "message": "Pekko says hello to World!"
                                        }
                                    }
                                }
                            }
                        }
                    },
                    "post": {
                        "tags": ["Hello"],
                        "summary": "Send hello with name",
                        "requestBody": {
                            "content": {
                                "application/json": {
                                    "schema": {
                                        "type": "object",
                                        "required": ["name"],
                                        "properties": {
                                            "name": {
                                                "type": "string",
                                                "example": "Alice"
                                            }
                                        }
                                    },
                                    "example": {
                                        "name": "Alice"
                                    }
                                }
                            }
                        },
                        "responses": {
                            "200": {
                                "description": "Successful response"
                            }
                        }
                    }
                },
                "/api/hello/{name}": {
                    "get": {
                        "tags": ["Hello"],
                        "summary": "Get hello message with name",
                        "parameters": [
                            {
                                "name": "name",
                                "in": "path",
                                "required": true,
                                "description": "The name to greet",
                                "schema": {
                                    "type": "string",
                                    "example": "Bob"
                                }
                            }
                        ],
                        "responses": {
                            "200": {
                                "description": "Successful response"
                            }
                        }
                    }
                },
                "/api/events": {
                    "post": {
                        "tags": ["Events"],
                        "summary": "Send user event",
                        "requestBody": {
                            "content": {
                                "application/json": {
                                    "schema": {
                                        "type": "object",
                                        "required": ["userId", "eventType", "action"],
                                        "properties": {
                                            "userId": {
                                                "type": "string",
                                                "example": "user123"
                                            },
                                            "eventType": {
                                                "type": "string",
                                                "example": "click"
                                            },
                                            "action": {
                                                "type": "string",
                                                "example": "button_click"
                                            },
                                            "metadata": {
                                                "type": "object",
                                                "additionalProperties": true,
                                                "example": {"page": "home", "button": "submit"}
                                            }
                                        }
                                    },
                                    "example": {
                                        "userId": "user123",
                                        "eventType": "click",
                                        "action": "button_click",
                                        "metadata": {"page": "home"}
                                    }
                                }
                            }
                        },
                        "responses": {
                            "202": {
                                "description": "Event accepted"
                            }
                        }
                    }
                },
                "/api/events/stats": {
                    "get": {
                        "tags": ["Events"],
                        "summary": "Get event statistics",
                        "responses": {
                            "200": {
                                "description": "Event statistics",
                                "content": {
                                    "application/json": {
                                        "schema": {
                                            "type": "object",
                                            "properties": {
                                                "totalEvents": {
                                                    "type": "integer",
                                                    "format": "int64"
                                                },
                                                "eventsPerType": {
                                                    "type": "object",
                                                    "additionalProperties": {
                                                        "type": "integer",
                                                        "format": "int64"
                                                    }
                                                },
                                                "eventsPerUser": {
                                                    "type": "object",
                                                    "additionalProperties": {
                                                        "type": "integer",
                                                        "format": "int64"
                                                    }
                                                },
                                                "lastEventTime": {
                                                    "type": "string",
                                                    "format": "date-time"
                                                }
                                            }
                                        },
                                        "example": {
                                            "totalEvents": 150,
                                            "eventsPerType": {
                                                "click": 75,
                                                "view": 50,
                                                "submit": 25
                                            },
                                            "eventsPerUser": {
                                                "user123": 30,
                                                "user456": 45
                                            },
                                            "lastEventTime": "2024-01-15T10:30:00Z"
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                "/api/events/stream": {
                    "get": {
                        "tags": ["Events"],
                        "summary": "SSE event stream",
                        "responses": {
                            "200": {
                                "description": "Server-sent event stream"
                            }
                        }
                    }
                },
                "/ws": {
                    "get": {
                        "tags": ["WebSocket"],
                        "summary": "WebSocket connection",
                        "responses": {
                            "101": {
                                "description": "Switching Protocols"
                            }
                        }
                    }
                }
            }
        }
        """.trimIndent()
    }
}