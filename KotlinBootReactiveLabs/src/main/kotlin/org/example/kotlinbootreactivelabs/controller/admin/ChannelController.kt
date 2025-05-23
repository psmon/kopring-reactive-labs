package org.example.kotlinbootreactivelabs.controller.admin

import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.future.await
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.stream.javadsl.Source
import org.apache.pekko.stream.javadsl.Sink

import org.example.kotlinbootreactivelabs.actor.MainStageActorCommand
import org.example.kotlinbootreactivelabs.module.AkkaUtils
import org.example.kotlinbootreactivelabs.ws.actor.chat.AllCounselorManagers
import org.example.kotlinbootreactivelabs.ws.actor.chat.CounselorManagerCreated
import org.example.kotlinbootreactivelabs.ws.actor.chat.CreateCounselorManager
import org.example.kotlinbootreactivelabs.ws.actor.chat.GetAllCounselorManagers
import org.example.kotlinbootreactivelabs.ws.actor.chat.SupervisorChannelCommand
import org.example.kotlinbootreactivelabs.ws.actor.chat.SupervisorChannelResponse
import org.example.kotlinbootreactivelabs.ws.actor.chat.SupervisorErrorStringResponse

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.GetMapping
import reactor.core.publisher.Mono

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/api/admin/channel")
@Tag(name = "ChannelController")
class ChannelController(
    private val actorSystem: ActorSystem<MainStageActorCommand>,
    private val supervisorChannelActor: ActorRef<SupervisorChannelCommand>,
) {

    private val timeout: Duration = Duration.ofSeconds(5)

    @PostMapping("/add-counselor-manager")
    suspend fun addCounselorManager(@RequestParam channel: String): String {
        val response = AskPattern.ask(
            supervisorChannelActor,
            { replyTo: ActorRef<SupervisorChannelResponse> -> CreateCounselorManager(channel, replyTo) },
            timeout,
            actorSystem.scheduler()
        ).await()

        return when (response) {
            is CounselorManagerCreated -> "Counselor Manager for channel $channel created successfully."
            is SupervisorErrorStringResponse -> response.message
            else -> "Unknown error occurred."
        }
    }

    @GetMapping("/list-counselor-managers")
    suspend fun listCounselorManagers(): List<String> {
        val response = AskPattern.ask(
            supervisorChannelActor,
            { replyTo: ActorRef<SupervisorChannelResponse> -> GetAllCounselorManagers(replyTo) },
            timeout,
            actorSystem.scheduler()
        ).await()

        return when (response) {
            is AllCounselorManagers -> response.channels
            else -> emptyList()
        }
    }


    // 다양한 방법으로 동시성처리가 가능하며, 일관성있는 코루틴방식 권장되며 다양성 테크참고:
    // 코루틴을 사용하면 Mono/Flux의 체이닝보다 자연스러운 흐름을 만들 수 있어 도메인 로직을 깔끔하게 표현 가능
    // 다만, 완전한 코루틴 기반으로 바꾼다면 Reactor Context ↔ CoroutineContext 전환 고려해야 함

    @GetMapping("/list-counselor-managers-stream")
    fun listCounselorManagersByStream(): Mono<List<String>> {
        return Mono.fromCompletionStage(
            Source.single(Unit)
                .mapAsync(1) {
                    AskPattern.ask(
                        supervisorChannelActor,
                        { replyTo: ActorRef<SupervisorChannelResponse> -> GetAllCounselorManagers(replyTo) },
                        timeout,
                        actorSystem.scheduler()
                    )
                }
                .runWith(Sink.head(), actorSystem)
                .thenApply { response ->
                    when (response) {
                        is AllCounselorManagers -> response.channels
                        else -> emptyList()
                    }
                }
        )
    }

    @GetMapping("/list-counselor-managers-async")
    fun listCounselorManagersByCoroutines(): Mono<List<String>> {
        val response = AkkaUtils.runBlockingAsk(
            supervisorChannelActor,
            { replyTo: ActorRef<SupervisorChannelResponse> -> GetAllCounselorManagers(replyTo) },
            timeout, actorSystem
        )
        return Mono.justOrEmpty(
            when (response) {
                is AllCounselorManagers -> response.channels
                else -> emptyList()
            }
        )
    }

    @GetMapping("/list-counselor-managers-mono")
    fun listCounselorManagersByMono(): Mono<List<String>> {
        return AkkaUtils.askActorByMono(
            supervisorChannelActor,
            { replyTo: ActorRef<SupervisorChannelResponse> -> GetAllCounselorManagers(replyTo) },
            timeout,
            actorSystem
        ).map { response ->
            when (response) {
                is AllCounselorManagers -> response.channels
                else -> emptyList()
            }
        }
    }
}