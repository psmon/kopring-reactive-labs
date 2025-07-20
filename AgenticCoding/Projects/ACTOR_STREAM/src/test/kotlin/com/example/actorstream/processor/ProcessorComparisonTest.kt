package com.example.actorstream.processor

import com.example.actorstream.model.StreamResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import kotlin.system.measureTimeMillis

class ProcessorComparisonTest {
    
    private val javaStreamProcessor = JavaStreamProcessor()
    private val webFluxProcessor = WebFluxProcessor()
    private val coroutinesProcessor = CoroutinesProcessor()
    
    private val testText = "abc 345 def sdf"
    
    @Test
    fun `Java Streams API should process text correctly`() {
        val result = javaStreamProcessor.processText(testText).get()
        
        result.originalText shouldBe testText
        result.words shouldBe listOf("abc", "def", "sdf")
        result.wordCount shouldBe 3
        result.numberSum shouldBe 345
    }
    
    @Test
    fun `Java Streams API should handle batch processing`() {
        val texts = listOf(
            "hello 100 world",
            "test 200 example",
            "sample 300 data"
        )
        
        val results = javaStreamProcessor.processTextWithBuffering(texts)
        
        results.size shouldBe 3
        results.map { it.numberSum } shouldBe listOf(100, 200, 300)
        results.map { it.wordCount } shouldBe listOf(2, 2, 2)
    }
    
    @Test
    fun `WebFlux should process text correctly`() {
        StepVerifier.create(webFluxProcessor.processText(testText))
            .assertNext { result ->
                result.originalText shouldBe testText
                result.words shouldBe listOf("abc", "def", "sdf")
                result.wordCount shouldBe 3
                result.numberSum shouldBe 345
            }
            .verifyComplete()
    }
    
    @Test
    fun `WebFlux should handle backpressure`() {
        val texts = Flux.just(
            "first 10 test",
            "second 20 example",
            "third 30 sample"
        )
        
        StepVerifier.create(webFluxProcessor.processTextWithBackpressure(texts))
            .expectNextMatches { it.numberSum == 10 && it.wordCount == 2 }
            .expectNextMatches { it.numberSum == 20 && it.wordCount == 2 }
            .expectNextMatches { it.numberSum == 30 && it.wordCount == 2 }
            .verifyComplete()
    }
    
    @Test
    fun `Kotlin Coroutines should process text correctly`() = runTest {
        val result = coroutinesProcessor.processText(testText)
        
        result.originalText shouldBe testText
        result.words shouldBe listOf("abc", "def", "sdf")
        result.wordCount shouldBe 3
        result.numberSum shouldBe 345
    }
    
    @Test
    fun `Kotlin Coroutines Flow should process text correctly`() = runTest {
        val results = coroutinesProcessor.processTextAsFlow(testText).toList()
        
        results.size shouldBe 1
        val result = results[0]
        result.originalText shouldBe testText
        result.words shouldBe listOf("abc", "def", "sdf")
        result.wordCount shouldBe 3
        result.numberSum shouldBe 345
    }
    
    @Test
    fun `Kotlin Coroutines should handle backpressure`() = runTest {
        val texts = listOf(
            "first 10 test",
            "second 20 example",
            "third 30 sample"
        ).asFlow()
        
        val results = coroutinesProcessor.processTextsWithBackpressure(texts).toList()
        
        results.size shouldBe 3
        results.map { it.numberSum } shouldBe listOf(10, 20, 30)
        results.map { it.wordCount } shouldBe listOf(2, 2, 2)
    }
    
    @Test
    fun `Kotlin Coroutines with channels should process correctly`() = runTest {
        val result = coroutinesProcessor.processWithChannels(testText)
        
        result.originalText shouldBe testText
        result.words shouldBe listOf("abc", "def", "sdf")
        result.wordCount shouldBe 3
        result.numberSum shouldBe 345
    }
    
    @Test
    fun `performance comparison of all implementations`() = runBlocking {
        val iterations = 100  // Reduced for stability
        val testTexts = (1..iterations).map { "word$it 100 test$it 200 example$it" }
        
        // Java Streams
        val javaTime = measureTimeMillis {
            javaStreamProcessor.processTextWithBuffering(testTexts)
        }
        println("Java Streams processed $iterations texts in ${javaTime}ms")
        
        // WebFlux - Fixed to handle large data properly
        val webFluxTime = measureTimeMillis {
            webFluxProcessor.processTextWithBackpressure(Flux.fromIterable(testTexts))
                .collectList()
                .block(Duration.ofSeconds(30))
        }
        println("WebFlux processed $iterations texts in ${webFluxTime}ms")
        
        // Coroutines
        val coroutinesTime = measureTimeMillis {
            coroutinesProcessor.processTextsWithBackpressure(testTexts.asFlow()).toList()
        }
        println("Kotlin Coroutines processed $iterations texts in ${coroutinesTime}ms")
    }
}