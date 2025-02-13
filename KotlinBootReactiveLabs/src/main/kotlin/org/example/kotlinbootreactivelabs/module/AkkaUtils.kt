package org.example.kotlinbootreactivelabs.module

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import reactor.core.publisher.Mono
import java.time.Duration

object AkkaUtils {
    suspend fun <T, R> askActor(
        actor: ActorRef<T>,
        message: (ActorRef<R>) -> T,
        timeout: Duration,
        actorSystem: ActorSystem<*>
    ): R {
        return AskPattern.ask(
            actor,
            message,
            timeout,
            actorSystem.scheduler()
        ).await()
    }

    fun <T, R> askActorByMono(
        actor: ActorRef<T>,
        message: (ActorRef<R>) -> T,
        timeout: Duration,
        actorSystem: ActorSystem<*>
    ): Mono<R> {
        return Mono.fromCompletionStage(
            AskPattern.ask(
                actor,
                message,
                timeout,
                actorSystem.scheduler()
            )
        )
    }

    fun <T, R> runBlockingAsk(
        actor: ActorRef<T>,
        message: (ActorRef<R>) -> T,
        timeout: Duration,
        actorSystem: ActorSystem<*>
    ): R = runBlocking {
        askActor(actor, message, timeout, actorSystem)
    }
}