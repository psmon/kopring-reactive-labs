package org.example.kotlinbootreactivelabs.actor.guide

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.javadsl.Source
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.time.Duration
import java.util.concurrent.CompletableFuture

class ConcurrentTest {

    companion object {
        private val actorSystem = ActorTestKit.create()

        @BeforeAll
        @JvmStatic
        fun setup() {
            // Setup code if needed
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            actorSystem.shutdownTestKit()
        }
    }

    @Test
    fun testCompletableFuture() {
        // Test using CompletableFuture to concatenate strings asynchronously
        val future = CompletableFuture.supplyAsync { "Hello" }
            .thenApplyAsync { result -> result + " World" }

        val result = future.get()
        
        assertEquals("Hello World", result)
    }

    @Test
    fun testCompletableFutureWithStream() {
        // Test using CompletableFuture with Java Streams to concatenate strings asynchronously
        val future = CompletableFuture.supplyAsync {
            listOf("Hello")
                .stream()
                .map { it + " World" }
                .findFirst()
                .orElse("")
        }

        val result = future.get()

        assertEquals("Hello World", result)
    }

    @Test
    fun testCompletableFutureWithAkkaStream() {
        // Test using Akka Streams to concatenate strings asynchronously
        val materializer = Materializer.createMaterializer(actorSystem.system())

        val source = Source.single("Hello")
            .map { it + " World" }
            .runWith(Sink.head(), materializer)

        val result = source.toCompletableFuture().get()

        assertEquals("Hello World", result)
    }

    @Test
    fun testCoroutine() = runBlocking {
        // Test using CompletableFuture to concatenate strings asynchronously
        val input = "Hello"
        val result = withContext(Dispatchers.Default) {
            val part1 = async { input + " World" }
            part1.await()
        }

        assertEquals("Hello World", result)
    }

    @Test
    fun testMono() {
        // Test using Reactor Mono to concatenate strings asynchronously
        val input = "Hello"
        val monoResult = Mono.just(input)
            .map { it + " World" }
            .block()

        assertEquals("Hello World", monoResult)
    }

    @Test
    fun testActorByNonBlock() {
        // Test sending a message to an actor and receiving a response using a test probe
        var probe = actorSystem.createTestProbe<String>()
        val actor = actorSystem.spawn(HelloWorld.create())
        actor.tell(SayHello("Hello", probe.ref))

        probe.expectMessage("Hello World")
    }

    @Test
    fun testActorAskByCompletableFuture() {
        // Test using the ask pattern with CompletableFuture to send a message to an actor and receive a response
        val actor = ActorSystem.create(HelloWorld.create(), "HelloWorld")
        val response = AskPattern.ask(
            actor,
            { replyTo: ActorRef<String> -> SayHello("Hello", replyTo) },
            Duration.ofSeconds(3),
            actor.scheduler()
        ).toCompletableFuture().get()

        assertEquals("Hello World", response)
    }

    @Test
    fun testActorAskByMono() {
        // Test using the ask pattern with Reactor Mono to send a message to an actor and receive a response
        val actor = ActorSystem.create(HelloWorld.create(), "HelloWorld")
        val response = AskPattern.ask(
            actor,
            { replyTo: ActorRef<String> -> SayHello("Hello", replyTo) },
            Duration.ofSeconds(3),
            actor.scheduler()
        ).toCompletableFuture().toMono().block()

        assertEquals("Hello World", response)
    }

    @Test
    fun testActorAskByAwait() = runBlocking {
        // Test using the ask pattern with Kotlin coroutines to send a message to an actor and receive a response
        val actor = ActorSystem.create(HelloWorld.create(), "HelloWorld")
        val response = AskPattern.ask(
            actor,
            { replyTo: ActorRef<String> -> SayHello("Hello", replyTo) },
            Duration.ofSeconds(3),
            actor.scheduler()
        ).toCompletableFuture().await()

        assertEquals("Hello World", response)
    }
}