package org.example.kotlinbootreactivelabs.actor.guide

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.javadsl.Source
import org.example.kotlinbootreactivelabs.actor.core.Hello
import org.example.kotlinbootreactivelabs.actor.core.HelloActor
import org.example.kotlinbootreactivelabs.actor.core.HelloCommand
import org.example.kotlinbootreactivelabs.actor.core.HelloResponse
import org.example.kotlinbootreactivelabs.actor.core.KHello
import org.example.kotlinbootreactivelabs.actor.core.KHelloActor
import org.example.kotlinbootreactivelabs.actor.core.KHelloResponse
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
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

    private fun getDelayString(): String {
        Thread.sleep(600);
        return "SlowWorld1"
    }

    private fun getDelayString2(): String {
        Thread.sleep(500);
        return "SlowWorld2"
    }

    //
    // Test Hello 다양한 동시성 Concurrent
    //

    @Test
    fun testHelloByCoroutine() = runBlocking {
        // Test using CompletableFuture to concatenate strings asynchronously
        val input = "Hello"
        val result = withContext(Dispatchers.Default) {
            val part1 = async { input + " World" }
            part1.await()
        }
        assertEquals("Hello World", result)
    }

    @Test
    fun testHelloByMono() {
        // Test using Reactor Mono to concatenate strings asynchronously
        val input = "Hello"
        val monoResult = Mono.just(input)
            .map { it + " World" }
            .block()

        assertEquals("Hello World", monoResult)
    }

    @Test
    fun testHelloByCompletableFuture() {
        // Test using CompletableFuture to concatenate strings asynchronously
        val future = CompletableFuture.supplyAsync { "Hello" }
            .thenApplyAsync { result -> result + " World" }

        val result = future.get()

        assertEquals("Hello World", result)
    }

    @Test
    fun testHelloByJavaStream() {
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


    //
    // Test Paralle Concurrent
    //

    @Test
    fun testConcurrentByCoroutine() = runBlocking {
        val input = "Hello"
        val result = withContext(Dispatchers.Default) {
            val part1 = async { input + " World" }
            val part2 = async { getDelayString() }
            val part3 = async { getDelayString2() }
            "${part1.await()} ${part2.await()} ${part3.await()}"
        }

        assertEquals("Hello World SlowWorld1 SlowWorld2", result)
    }


    @Test
    fun testConcurrentByMono() {
        val input = "Hello"
        val monoResult = Mono.just(input)
            .map { it + " World" }
            .zipWith(Mono.fromCallable { getDelayString() })
            .zipWith(Mono.fromCallable { getDelayString2() }) { part1, part2 ->
                "${part1.t1} ${part1.t2} $part2"
            }
            .block()

        assertEquals("Hello World SlowWorld1 SlowWorld2", monoResult)
    }

    @Test
    fun testConcurrentByFlux() {
        val input = listOf("Hello", "World")
        val fluxResult = Flux.fromIterable(input)
            .flatMap { data ->
                when (data) {
                    "Hello" -> Mono.fromCallable { getDelayString() }
                    "World" -> Mono.fromCallable { getDelayString2() }
                    else -> Mono.empty()
                }
            }
            .collectList()
            .map { results -> results.joinToString(" ") }
            .block()

        assertEquals("SlowWorld1 SlowWorld2", fluxResult)
    }

    @Test
    fun testConcurrentByCompletableFuture() {
        val future = CompletableFuture.supplyAsync { "Hello" }
            .thenApplyAsync { result -> result + " World" }
            .thenCombineAsync(CompletableFuture.supplyAsync { getDelayString() }) { part1, part2 ->
                "$part1 $part2"
            }
            .thenCombineAsync(CompletableFuture.supplyAsync { getDelayString2() }) { part1, part3 ->
                "$part1 $part3"
            }

        val result = future.get()

        assertEquals("Hello World SlowWorld1 SlowWorld2", result)
    }

    @Test
    fun testConcurrentByJavsStream() {
        val result = listOf("Hello")
            .stream()
            .map { input -> input + " World" }
            .map { part1 ->
                val part2 = CompletableFuture.supplyAsync { getDelayString() }.join()
                val part3 = CompletableFuture.supplyAsync { getDelayString2() }.join()
                "$part1 $part2 $part3"
            }
            .findFirst()
            .orElse("")

        assertEquals("Hello World SlowWorld1 SlowWorld2", result)
    }

    @Test
    fun testConcurrentByAkkaStream() {
        val materializer = Materializer.createMaterializer(actorSystem.system())

        val source = Source.single("Hello")
            .map { it + " World" }
            .zipWith(Source.single(getDelayString())) { part1, part2 -> Pair(part1, part2) }
            .zipWith(Source.single(getDelayString2())) { pair, part3 ->
                "${pair.first} ${pair.second} $part3"
            }
            .runWith(Sink.head(), materializer)

        val result = source.toCompletableFuture().get()

        assertEquals("Hello World SlowWorld1 SlowWorld2", result)
    }

    //
    // Test Actor Pattern with Various Concurrent Approaches
    //

    @Test
    fun testActorAskByCoroutine() = runBlocking {
        // Test using Actor Ask pattern with coroutines
        val actorRef = actorSystem.spawn(HelloActor.create())
        var response = AskPattern.ask(
            actorRef,
            { replyTo: ActorRef<HelloCommand> -> Hello("Hello", replyTo) },
            Duration.ofSeconds(3),
            actorSystem.scheduler()
        ).toCompletableFuture().await()

        assertEquals(HelloResponse("Kotlin"), response)
    }

    @Test
    fun testActorAskByWebflux() {
        val actorRef = actorSystem.spawn(HelloActor.create())
        val result = Mono.just("Hello")
            .flatMap { input ->
                Mono.fromFuture(
                    AskPattern.ask(
                        actorRef,
                        { replyTo: ActorRef<HelloCommand> -> Hello(input, replyTo) },
                        Duration.ofSeconds(3),
                        actorSystem.scheduler()
                    ).toCompletableFuture()
                )
            }
            .map { response -> (response as HelloResponse).message }
            .block()

        assertEquals("Kotlin", result)
    }

    @Test
    fun testActorAskByCompletableFuture() {
        val actorRef = actorSystem.spawn(HelloActor.create())
        val future = CompletableFuture.supplyAsync { "Hello" }
            .thenCompose { input ->
                AskPattern.ask(
                    actorRef,
                    { replyTo: ActorRef<HelloCommand> -> Hello(input, replyTo) },
                    Duration.ofSeconds(3),
                    actorSystem.scheduler()
                ).toCompletableFuture()
            }
            .thenApply { response -> (response as HelloResponse).message }

        val result = future.get()
        assertEquals("Kotlin", result)
    }

    @Test
    fun testActorAskByJavaStream() {
        val actorRef = actorSystem.spawn(HelloActor.create())

        val result = listOf("Hello")
            .stream()
            .map { input ->
                AskPattern.ask(
                    actorRef,
                    { replyTo: ActorRef<HelloCommand> -> Hello(input, replyTo) },
                    Duration.ofSeconds(3),
                    actorSystem.scheduler()
                ).toCompletableFuture().join()
            }
            .map { response -> (response as HelloResponse).message }
            .findFirst()
            .orElse("")

        assertEquals("Kotlin", result)
    }

    @Test
    fun testActorAskByAkkaStream() {
        val actorRef = actorSystem.spawn(HelloActor.create())
        val materializer = Materializer.createMaterializer(actorSystem.system())

        val source = Source.single("Hello")
            .mapAsync(1) { input ->
                AskPattern.ask(
                    actorRef,
                    { replyTo: ActorRef<HelloCommand> -> Hello(input, replyTo) },
                    Duration.ofSeconds(3),
                    actorSystem.scheduler()
                ).toCompletableFuture()
            }
            .map { response -> (response as HelloResponse).message }
            .runWith(Sink.head(), materializer)

        val result = source.toCompletableFuture().get()
        assertEquals("Kotlin", result)
    }

    @Test
    fun testActorAskByAkkaStreamWithCoroutine() = runBlocking {
        val actorRef = actorSystem.spawn(HelloActor.create())
        val materializer = Materializer.createMaterializer(actorSystem.system())

        val source = Source.single("Hello")
            .mapAsync(1) { input ->
                AskPattern.ask(
                    actorRef,
                    { replyTo: ActorRef<HelloCommand> -> Hello(input, replyTo) },
                    Duration.ofSeconds(3),
                    actorSystem.scheduler()
                ).toCompletableFuture()
            }
            .map { response -> (response as HelloResponse).message }
            .runWith(Sink.head(), materializer)

        val result = source.await()
        assertEquals("Kotlin", result)
    }

    // 코틀린에 도입된 액터모델로, AKKA의 액터모델을 커버하기보다
    // Java21에 도입된 경략화 스레드(Virtual Thread)에 대응할수 있을것으로 보입니다.

    @Test
    fun testKHelloActor() = runBlocking {
        // KHelloActor 생성 및 시작
        val actor = KHelloActor()
        val replyChannel = Channel<KHelloResponse>()

        val job = actor.start() // start 메서드가 Job을 반환하도록 수정

        try {
            // 메시지 전송 및 응답 수신
            withTimeout(3000) { // 타임아웃 설정
                actor.send(KHello("Hello", replyChannel))
                val response = replyChannel.receive()

                // 응답 검증
                assertEquals("Kotlin", response.message)
            }
        } finally {
            // 리소스 정리
            replyChannel.close()
            actor.close()
            job.cancel() // 액터의 코루틴 종료
        }
    }
}