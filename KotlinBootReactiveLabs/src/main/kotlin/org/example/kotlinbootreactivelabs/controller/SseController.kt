package org.example.kotlinbootreactivelabs.controller

import io.swagger.v3.oas.annotations.Operation
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactor.mono
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef
import org.example.kotlinbootreactivelabs.actor.GetOrCreateUserEventActor
import org.example.kotlinbootreactivelabs.actor.MainStageActor
import org.example.kotlinbootreactivelabs.actor.MainStageActorCommand
import org.example.kotlinbootreactivelabs.actor.sse.GetEvent
import org.example.kotlinbootreactivelabs.actor.sse.UserEventActor
import org.example.kotlinbootreactivelabs.actor.sse.UserEventCommand
import org.example.kotlinbootreactivelabs.actor.state.Hello
import org.example.kotlinbootreactivelabs.config.AkkaConfiguration
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap

@RestController
class SseController(private val akka: AkkaConfiguration) {

    private val logger = LoggerFactory.getLogger(SseController::class.java)
    private val mainStageActor: ActorRef<MainStageActorCommand> = akka.getMainStage()

    @Operation(summary = "Server Sent Events",
        description = "SSE규약을 사용해 이벤트를 단방향 수신받을수 있습니다.")
    @GetMapping("/sse", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
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