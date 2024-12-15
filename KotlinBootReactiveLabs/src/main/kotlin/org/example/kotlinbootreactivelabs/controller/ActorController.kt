package org.example.kotlinbootreactivelabs.controller

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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Duration

@RestController
@RequestMapping("/api/actor")
@Tag(name = "Actor Controller")
class ActorController @Autowired constructor(private val akka: AkkaConfiguration) {

    private val helloState: ActorSystem<HelloStateActorCommand> = akka.getHelloState()

    @PostMapping("/hello")
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

    @GetMapping("/hello-count")
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