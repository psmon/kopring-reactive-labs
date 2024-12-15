package org.example.kotlinbootreactivelabs.controller


import io.swagger.v3.oas.annotations.tags.Tag
import org.example.kotlinbootreactivelabs.ws.WebSocketSessionManager
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/pubsub")
@Tag(name = "PubSub Controller")
class PubSubController(private val sessionManager: WebSocketSessionManager) {

    @PostMapping("/send-to-session")
    fun sendMessageToSession(@RequestParam sessionId: String, @RequestBody message: String): Mono<String> {
        return Mono.fromCallable {
            sessionManager.sendReactiveMessageToSession(sessionId, message)
            "Message sent to session $sessionId"
        }
    }

    @PostMapping("/publish-to-topic")
    fun sendMessageToTopic(@RequestParam topic: String, @RequestBody message: String): Mono<String> {
        return Mono.fromCallable {
            sessionManager.sendReactiveMessageToTopic(topic, message)
            "Message sent to topic $topic"
        }
    }

    @PostMapping("/subscribe-to-topic")
    fun subscribeToTopic(@RequestParam sessionId: String, @RequestParam topic: String): Mono<String> {
        return Mono.fromCallable {
            sessionManager.subscribeReactiveToTopic(sessionId, topic)
            "Session $sessionId subscribed to topic $topic"
        }
    }

    @PostMapping("/unsubscribe-to-topic")
    fun unsubscribeToTopic(@RequestParam sessionId: String, @RequestParam topic: String): Mono<String> {
        return Mono.fromCallable {
            sessionManager.unsubscribeReactiveFromTopic(sessionId, topic)
            "Session $sessionId unsubscribed to topic $topic"
        }
    }

    @GetMapping("/health")
    fun healthCheck(): Mono<String> {
        return Mono.just("WebSocketController is healthy")
    }
}