package org.example.kotlinbootreactivelabs.controller.sse

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactor.mono
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.example.kotlinbootreactivelabs.actor.GetOrCreateUserEventActor
import org.example.kotlinbootreactivelabs.actor.MainStageActorCommand
import org.example.kotlinbootreactivelabs.actor.sse.GetEvent
import org.example.kotlinbootreactivelabs.actor.sse.UserEventCommand
import org.example.kotlinbootreactivelabs.config.AkkaConfiguration
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.time.Duration

@RestController
@Tag(name = "SseController")
class SseController(private val akka: AkkaConfiguration) {

    private val logger = LoggerFactory.getLogger(SseController::class.java)
    private val mainStageActor: ActorRef<MainStageActorCommand> = akka.getMainStage()

    @Operation(summary = "Server Sent Events",
        description = "SSE규약을 사용해 이벤트를 단방향 수신받을수 있습니다.")
    @GetMapping("/api/sse", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamEvents(@RequestParam brandId: String, @RequestParam userId: String): Flux<String> {

        val response = AskPattern.ask(
            mainStageActor,
            { replyTo: ActorRef<Any> -> GetOrCreateUserEventActor(brandId, userId, replyTo) },
            Duration.ofSeconds(3),
            akka.getScheduler()
        ).toCompletableFuture().get()

        val userEventActor = response as ActorRef<UserEventCommand>

        return Flux.interval(Duration.ofSeconds(3))
            .flatMap {
                mono {
                    val response = AskPattern.ask(
                        userEventActor,
                        { replyTo: ActorRef<Any> -> GetEvent(replyTo) },
                        Duration.ofSeconds(3),
                        akka.getScheduler()
                    ).toCompletableFuture().await()
                    response as String
                }
            }
    }
}