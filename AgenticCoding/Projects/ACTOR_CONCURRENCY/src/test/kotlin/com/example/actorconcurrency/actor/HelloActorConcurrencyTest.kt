package com.example.actorconcurrency.actor

import com.example.actorconcurrency.model.Hello
import com.example.actorconcurrency.model.HelloCommand
import com.example.actorconcurrency.model.HelloResponse
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.test.runTest
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class HelloActorConcurrencyTest {

    private lateinit var testKit: ActorTestKit
    private lateinit var system: ActorSystem<Void>
    private lateinit var helloActor: ActorRef<HelloCommand>

    @BeforeEach
    fun setup() {
        testKit = ActorTestKit.create()
        system = testKit.system()
        helloActor = testKit.spawn(HelloActor.create())
    }

    @AfterEach
    fun teardown() {
        testKit.shutdownTestKit()
    }

    @Test
    fun `test HelloActor with Tell pattern using test receiver`() {
        // Create a test probe to receive messages
        val probe: TestProbe<HelloCommand> = testKit.createTestProbe()
        
        // Create HelloActor with test receiver
        val helloActorWithReceiver = testKit.spawn(
            HelloActor.create(probe.ref), 
            "helloActorWithReceiver"
        )
        
        // Send message using Tell pattern (fire and forget)
        helloActorWithReceiver.tell(Hello("Hello"))
        
        // Verify the receiver got the response
        probe.expectMessage(HelloResponse("Kotlin"))
    }

    @Test
    fun `test HelloActor with Ask pattern using CompletableFuture`() {
        // Using Ask pattern with CompletableFuture
        val future: CompletableFuture<HelloCommand> = AskPattern.ask(
            helloActor,
            { replyTo: ActorRef<HelloCommand> -> Hello("Hello", replyTo) },
            Duration.ofSeconds(3),
            system.scheduler()
        ).toCompletableFuture()
        
        // Get the response synchronously
        val response = future.get()
        
        // Verify response
        response shouldBe HelloResponse("Kotlin")
    }

    @Test
    fun `test HelloActor with Ask pattern using WebFlux Mono`() {
        // Using Ask pattern with Reactor Mono
        val responseMono: Mono<HelloCommand> = AskPattern.ask(
            helloActor,
            { replyTo: ActorRef<HelloCommand> -> Hello("Hello", replyTo) },
            Duration.ofSeconds(3),
            system.scheduler()
        ).toCompletableFuture().toMono()
        
        // Block and get the response
        val response = responseMono.block()
        
        // Verify response
        response shouldBe HelloResponse("Kotlin")
    }

    @Test
    fun `test HelloActor with Ask pattern using Kotlin Coroutines`() = runTest {
        // Using Ask pattern with Kotlin Coroutines
        val response = AskPattern.ask(
            helloActor,
            { replyTo: ActorRef<HelloCommand> -> Hello("Hello", replyTo) },
            Duration.ofSeconds(3),
            system.scheduler()
        ).toCompletableFuture().await()
        
        // Verify response
        response shouldBe HelloResponse("Kotlin")
    }

    @Test
    fun `test HelloActor with Ask pattern using Kotlin Coroutines and Reactor extension`() = runTest {
        // Using Ask pattern with Kotlin Coroutines via Reactor extension
        val response = AskPattern.ask(
            helloActor,
            { replyTo: ActorRef<HelloCommand> -> Hello("Hello", replyTo) },
            Duration.ofSeconds(3),
            system.scheduler()
        ).toCompletableFuture().toMono().awaitFirst()
        
        // Verify response
        response shouldBe HelloResponse("Kotlin")
    }

    @Test
    fun `test multiple concurrent requests with different patterns`() = runTest {
        // Mix of different concurrency patterns running concurrently
        
        // CompletableFuture
        val future1 = AskPattern.ask(
            helloActor,
            { replyTo: ActorRef<HelloCommand> -> Hello("Hello from Future", replyTo) },
            Duration.ofSeconds(3),
            system.scheduler()
        ).toCompletableFuture()
        
        // Mono
        val mono = AskPattern.ask(
            helloActor,
            { replyTo: ActorRef<HelloCommand> -> Hello("Hello from Mono", replyTo) },
            Duration.ofSeconds(3),
            system.scheduler()
        ).toCompletableFuture().toMono()
        
        // Coroutine
        val deferred = AskPattern.ask(
            helloActor,
            { replyTo: ActorRef<HelloCommand> -> Hello("Hello from Coroutine", replyTo) },
            Duration.ofSeconds(3),
            system.scheduler()
        ).toCompletableFuture()
        
        // Collect all responses
        val response1 = future1.get()
        val response2 = mono.block()
        val response3 = deferred.await()
        
        // All should receive the same "Kotlin" response
        response1 shouldBe HelloResponse("Kotlin")
        response2 shouldBe HelloResponse("Kotlin")
        response3 shouldBe HelloResponse("Kotlin")
    }
}