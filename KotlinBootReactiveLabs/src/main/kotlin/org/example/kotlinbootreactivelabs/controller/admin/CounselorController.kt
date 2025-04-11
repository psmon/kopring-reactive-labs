package org.example.kotlinbootreactivelabs.controller.admin

import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.example.kotlinbootreactivelabs.actor.MainStageActorCommand
import org.example.kotlinbootreactivelabs.module.AkkaUtils
import org.example.kotlinbootreactivelabs.ws.actor.chat.CounselorCreated
import org.example.kotlinbootreactivelabs.ws.actor.chat.CounselorManagerFound
import org.example.kotlinbootreactivelabs.ws.actor.chat.CounselorManagerResponse
import org.example.kotlinbootreactivelabs.ws.actor.chat.CreateCounselor
import org.example.kotlinbootreactivelabs.ws.actor.chat.GetCounselorManager
import org.example.kotlinbootreactivelabs.ws.actor.chat.SupervisorChannelCommand
import org.example.kotlinbootreactivelabs.ws.actor.chat.SupervisorChannelResponse
import org.example.kotlinbootreactivelabs.ws.actor.chat.SupervisorErrorStringResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping("/api/admin/counselor")
@Tag(name = "CounselorController")
class CounselorController (
    private val actorSystem: ActorSystem<MainStageActorCommand>,
    private val supervisorChannelActor: ActorRef<SupervisorChannelCommand>,
){
    private val timeout: Duration = Duration.ofSeconds(5)

    @PostMapping("/add-counselor")
    suspend fun addCounselor(@RequestParam channel: String, @RequestParam id: String): String {
        val response = AskPattern.ask(
            supervisorChannelActor,
            { replyTo: ActorRef<SupervisorChannelResponse> -> GetCounselorManager(channel, replyTo) },
            timeout,
            actorSystem.scheduler()
        ).await()

        return when (response) {
            is CounselorManagerFound -> {
                val counselorResponse = AskPattern.ask(
                    response.actorRef,
                    { replyTo: ActorRef<CounselorManagerResponse> -> CreateCounselor(id, replyTo) },
                    timeout,
                    actorSystem.scheduler()
                ).await()

                when (counselorResponse) {
                    is CounselorCreated -> "Counselor $id created successfully."
                    else -> "Unknown error occurred."
                }
            }
            is SupervisorErrorStringResponse -> response.message
            else -> "Unknown error occurred."
        }
    }

    @PostMapping("/add-counselor-sync")
    fun addCounselorAsync(@RequestParam channel: String, @RequestParam id: String): Mono<String> {
        return Mono.fromCallable {
            runBlocking {
                val response = AkkaUtils.askActor(
                    supervisorChannelActor,
                    { replyTo: ActorRef<SupervisorChannelResponse> -> GetCounselorManager(channel, replyTo) },
                    timeout,
                    actorSystem
                )

                when (response) {
                    is CounselorManagerFound -> {
                        val counselorResponse = AkkaUtils.askActor(
                            response.actorRef,
                            { replyTo: ActorRef<CounselorManagerResponse> -> CreateCounselor(id, replyTo) },
                            timeout,
                            actorSystem
                        )
                        when (counselorResponse) {
                            is CounselorCreated -> "Counselor $id created successfully."
                            else -> "Unknown error occurred."
                        }
                    }
                    is SupervisorErrorStringResponse -> response.message
                    else -> "Unknown error occurred."
                }
            }
        }
    }
}