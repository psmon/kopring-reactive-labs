package org.example.kotlinbootreactivelabs.ws

import org.example.kotlinbootreactivelabs.ws.actor.SocketActorHandler
import org.example.kotlinbootreactivelabs.ws.base.SocketHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy
import org.springframework.web.reactive.socket.server.WebSocketService
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService

@Configuration
@EnableWebFlux
class ReactiveWebSocketConfig(
    private val socketHandler: SocketHandler,
    private val socketActorHandler: SocketActorHandler
) {

    @Bean
    fun webSocketHandlerAdapter(): WebSocketHandlerAdapter {
        return WebSocketHandlerAdapter(webSocketService())
    }

    @Bean
    fun webSocketService(): WebSocketService {
        return HandshakeWebSocketService(ReactorNettyRequestUpgradeStrategy())
    }

    @Bean
    fun webSocketHandlerMapping(): HandlerMapping {
        val map = mapOf(
            "/ws-reactive" to socketHandler,
            "/ws-actor" to socketActorHandler
        )
        val handlerMapping = SimpleUrlHandlerMapping()
        handlerMapping.order = 1
        handlerMapping.urlMap = map
        return handlerMapping
    }
}