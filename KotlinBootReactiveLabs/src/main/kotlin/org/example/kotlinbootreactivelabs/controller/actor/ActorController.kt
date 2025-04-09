package org.example.kotlinbootreactivelabs.controller.actor

import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactor.mono
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.example.kotlinbootreactivelabs.actor.state.GetHelloCount
import org.example.kotlinbootreactivelabs.actor.state.Hello
import org.example.kotlinbootreactivelabs.actor.state.HelloCountResponse
import org.example.kotlinbootreactivelabs.actor.state.HelloResponse
import org.example.kotlinbootreactivelabs.actor.state.HelloStateActorCommand
import org.example.kotlinbootreactivelabs.config.AkkaConfiguration
import org.example.kotlinbootreactivelabs.module.AkkaUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Duration

@RestController
@RequestMapping("/api/actor")
@Tag(name = "Actor Controller", description = "Pekko Actor 관련 API를 제공합니다.")
class ActorController @Autowired constructor(private val akka: AkkaConfiguration) {

    private val helloState: ActorSystem<HelloStateActorCommand> = akka.getHelloState()

    @PostMapping("/hello")
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Hello 명령 전송",
        description = "Pekko Actor에 Hello 명령을 전송하고 응답 메시지를 반환합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "성공적으로 Hello 명령이 처리되었습니다.",
        content = [io.swagger.v3.oas.annotations.media.Content(
            mediaType = "application/json",
            schema = io.swagger.v3.oas.annotations.media.Schema(implementation = String::class)
        )]
    )
    fun helloCommand(): Mono<String> {
        // 비동기완료 await 활용
        return mono {
            val response = AskPattern.ask(
                helloState,
                { replyTo: ActorRef<Any> -> Hello("Hello", replyTo) },
                Duration.ofSeconds(3),
                akka.getScheduler()
            ).toCompletableFuture().await()

            val helloResponse = response as HelloResponse
            "helloResponse.message: ${helloResponse.message}"
        }
    }

    @PostMapping("/hello2")
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Hello 명령 전송 (Mono 활용)",
        description = "Pekko Actor에 Hello 명령을 Mono 방식으로 전송하고 응답 메시지를 반환합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "성공적으로 Hello 명령이 처리되었습니다.",
        content = [io.swagger.v3.oas.annotations.media.Content(
            mediaType = "application/json",
            schema = io.swagger.v3.oas.annotations.media.Schema(implementation = String::class)
        )]
    )
    fun helloCommandByMono(): Mono<String> {
        return AkkaUtils.askActorByMono(
            helloState,
            { replyTo: ActorRef<Any> -> Hello("Hello", replyTo) },
            Duration.ofSeconds(3),
            akka.getMainStage()
        ).map { response ->
            val helloResponse = response as HelloResponse
            "helloResponse.message: ${helloResponse.message}"
        }
    }

    @GetMapping("/hello-count")
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Hello Count 조회",
        description = "Pekko Actor의 Hello 명령 호출 횟수를 조회합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "성공적으로 Hello Count가 조회되었습니다.",
        content = [io.swagger.v3.oas.annotations.media.Content(
            mediaType = "application/json",
            schema = io.swagger.v3.oas.annotations.media.Schema(implementation = String::class)
        )]
    )
    fun helloCountCommand(): Mono<String> {
        // 비동기완료 CompletableFuture 활용
        return Mono.fromFuture(
            AskPattern.ask(
                helloState,
                { replyTo: ActorRef<Any> -> GetHelloCount(replyTo) },
                Duration.ofSeconds(3),
                akka.getScheduler()
            ).toCompletableFuture()
        ).map { response ->
            val helloCountResponse = response as HelloCountResponse
            "helloCountResponse.count: ${helloCountResponse.count}"
        }
    }
}