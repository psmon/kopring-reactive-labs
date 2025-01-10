package org.example.kotlinbootreactivelabs.controller


import io.swagger.v3.oas.annotations.tags.Tag
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.example.kotlinbootreactivelabs.actor.GetOrCreateUserEventActor
import org.example.kotlinbootreactivelabs.actor.MainStageActor
import org.example.kotlinbootreactivelabs.actor.MainStageActorCommand
import org.example.kotlinbootreactivelabs.actor.PublishToTopic
import org.example.kotlinbootreactivelabs.actor.sse.AddEvent
import org.example.kotlinbootreactivelabs.actor.sse.UserEventCommand
import org.example.kotlinbootreactivelabs.config.AkkaConfiguration
import org.example.kotlinbootreactivelabs.ws.WebSocketSessionManager
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.time.Duration

@RestController
@RequestMapping("/api/pubsub")
@Tag(name = "PubSub Controller")
class PubSubController(
    private val sessionManager: WebSocketSessionManager,
    private val akka: AkkaConfiguration
) {
    private val mainStageActor: ActorRef<MainStageActorCommand> = akka.getMainStage()

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

    @PostMapping("/publish-to-user-event")
    fun publishToUserEvent(@RequestParam topic: String, @RequestBody message: String): Mono<String> {
        return Mono.fromCallable{
            mainStageActor.tell(PublishToTopic(topic, message))
            "OK"
        }
    }

    @GetMapping("/health")
    fun healthCheck(): Mono<String> {
        return Mono.just("WebSocketController is healthy")
    }
}