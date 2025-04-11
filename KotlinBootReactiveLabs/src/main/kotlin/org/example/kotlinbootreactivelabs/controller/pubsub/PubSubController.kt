package org.example.kotlinbootreactivelabs.controller.pubsub

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.apache.pekko.actor.typed.ActorRef
import org.example.kotlinbootreactivelabs.actor.MainStageActorCommand
import org.example.kotlinbootreactivelabs.actor.PublishToTopic
import org.example.kotlinbootreactivelabs.config.AkkaConfiguration
import org.example.kotlinbootreactivelabs.ws.actor.basic.SimpleSessionCommand
import org.example.kotlinbootreactivelabs.ws.actor.basic.SimpleUserSessionCommandResponse
import org.example.kotlinbootreactivelabs.ws.base.SessionManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/pubsub")
@Tag(name = "PubSub Controller")
class PubSubController(
    private val sessionManager: SessionManager,
    private val simpleSessionActor: ActorRef<SimpleSessionCommand>,
    private val akka: AkkaConfiguration
) {
    private val mainStageActor: ActorRef<MainStageActorCommand> = akka.getMainStage()

    @Operation(summary = "웹소켓 특정 세션에 메시지 전송",
        description = "sessionId : 세션아이디")
    @PostMapping("/publish-to-session")
    suspend fun sendMessageToSession(@RequestParam sessionId: String, @RequestBody message: String): ResponseEntity<String> {
        sessionManager.sendReactiveMessageToSession(sessionId, message)
        val noSender = akka.getMainStage().ignoreRef<SimpleUserSessionCommandResponse>()
        simpleSessionActor.tell(SimpleSessionCommand.SimpleSendMessageToSession(sessionId, message, noSender))
        return ResponseEntity.ok("Message sent to session $sessionId")
    }

    @Operation(summary = "웹소켓 특정 토픽 구독자에게 메시지를 보냅니다.",
        description = "topic : 토픽명")
    @PostMapping("/publish-to-topic")
    suspend fun sendMessageToTopic(@RequestParam topic: String, @RequestBody message: String): ResponseEntity<String> {
        sessionManager.sendReactiveMessageToTopic(topic, message)
        val noSender = akka.getMainStage().ignoreRef<SimpleUserSessionCommandResponse>()
        simpleSessionActor.tell(SimpleSessionCommand.SimpleSendMessageToTopic(topic, message, noSender))
        return ResponseEntity.ok("Message sent to topic $topic")
    }

    @Operation(summary = "특정세션에 특정 토픽을 구독을 시킵니다.",
        description = "도메인 로직에 의해 특정세션을 구독시킬수 있습니다.")
    @PostMapping("/subscribe-to-topic")
    suspend fun subscribeToTopic(@RequestParam sessionId: String, @RequestParam topic: String): ResponseEntity<String> {
        sessionManager.subscribeReactiveToTopic(sessionId, topic)
        return ResponseEntity.ok("Session $sessionId subscribed to topic $topic")
    }

    @Operation(summary = "특정 토픽에 구독해제 요청합니다.",
        description = "도메인 로직에 의해 특정세션을 구독해제 시킬수 있습니다.")
    @PostMapping("/unsubscribe-to-topic")
    suspend fun unsubscribeToTopic(@RequestParam sessionId: String, @RequestParam topic: String): ResponseEntity<String> {
        sessionManager.unsubscribeReactiveFromTopic(sessionId, topic)
        return ResponseEntity.ok("Session $sessionId unsubscribed to topic $topic")
    }

    @Operation(summary = "Server Sent Events",
        description = "SSE규약을 사용해 이벤트를 단방향 수신받을수 있습니다.")
    @PostMapping("/publish-to-user-event")
    suspend fun publishToUserEvent(@RequestParam topic: String, @RequestBody message: String): ResponseEntity<String> {
        mainStageActor.tell(PublishToTopic(topic, message))
        return ResponseEntity.ok("OK")
    }

    @Operation(summary = "Server Sent Events",
        description = "SSE규약을 사용해 이벤트를 단방향 수신받을수 있습니다.")
    @GetMapping("/health")
    suspend fun healthCheck(): ResponseEntity<String> {
        return ResponseEntity.ok("WebSocketController is healthy")
    }
}