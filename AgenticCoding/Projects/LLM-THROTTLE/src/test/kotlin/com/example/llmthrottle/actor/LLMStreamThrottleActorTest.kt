package com.example.llmthrottle.actor

import com.example.llmthrottle.model.*
import com.typesafe.config.ConfigFactory
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.ManualTime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class LLMStreamThrottleActorTest {
    
    companion object {
        private lateinit var testKit: ActorTestKit
        private lateinit var manualTime: ManualTime
        
        @BeforeAll
        @JvmStatic
        fun setup() {
            val config = ManualTime.config().withFallback(ConfigFactory.defaultApplication())
            testKit = ActorTestKit.create(config)
            manualTime = ManualTime.get(testKit.system())
        }
        
        @AfterAll
        @JvmStatic
        fun teardown() {
            testKit.shutdownTestKit()
        }
    }
    
    // Fixed token calculator for predictable testing
    class FixedTokenCalculator(private val tokensPerRequest: Int) : TokenCalculator {
        override fun calculateTokens(text: String): Int = tokensPerRequest
    }
    
    private fun generateRequestId() = UUID.randomUUID().toString()
    
    @Test
    fun `should process requests with stream throttling when under capacity`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val tokenCalculator = FixedTokenCalculator(100)
        val throttleActor = testKit.spawn(LLMStreamThrottleActor.create(tokenCalculator, 10_000))
        
        // Send requests that use 50% capacity
        val requests = (1..50).map { "request-$it" }
        requests.forEach { requestId ->
            throttleActor.tell(ProcessLLMRequest(requestId, "Test content", probe.ref()))
        }
        
        // Should receive all responses (some might be throttled notifications)
        repeat(50) {
            val response = probe.expectMessageClass(LLMThrottleResponse::class.java, Duration.ofSeconds(5))
            // Response can be either immediate LLMResponse or throttled notification
            when (response) {
                is LLMResponse -> {
                    response.tokensUsed shouldBe 100
                }
                is LLMThrottled -> {
                    response.currentCapacityPercent shouldBeLessThan 70.0
                }
                else -> {
                    // Unexpected response type
                }
            }
        }
        
        Thread.sleep(2000) // Wait for stream processing
        
        // Check final state
        val stateProbe = testKit.createTestProbe<ThrottleState>()
        throttleActor.tell(GetThrottleState(stateProbe.ref()))
        val state = stateProbe.expectMessageClass(ThrottleState::class.java)
        
        state.currentTokensUsed shouldBeGreaterThan 0
        state.failedRequestsCount shouldBe 0
    }
    
    @Test
    fun `should apply dynamic throttling based on capacity`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val tokenCalculator = FixedTokenCalculator(1000) // Large tokens to reach capacity quickly
        val throttleActor = testKit.spawn(LLMStreamThrottleActor.create(tokenCalculator, 10_000))
        
        // Send requests to reach 80% capacity
        repeat(8) { i ->
            throttleActor.tell(ProcessLLMRequest("request-$i", "Test", probe.ref()))
        }
        
        // Wait for some processing
        Thread.sleep(1000)
        
        // Send additional request that should be throttled
        val requestId = generateRequestId()
        throttleActor.tell(ProcessLLMRequest(requestId, "Test throttled", probe.ref()))
        
        // Should receive throttled response or eventual successful response
        val response = probe.expectMessageClass(LLMThrottleResponse::class.java, Duration.ofSeconds(5))
        when (response) {
            is LLMThrottled -> {
                response.currentCapacityPercent shouldBeGreaterThanOrEqual 70.0
            }
            is LLMResponse -> {
                // Stream processed it successfully
                response.tokensUsed shouldBe 1000
            }
            else -> {
                // Handle failed case
            }
        }
    }
    
    @Test
    fun `should handle queue overflow gracefully`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val tokenCalculator = FixedTokenCalculator(50) // Small tokens to avoid capacity issues
        val throttleActor = testKit.spawn(LLMStreamThrottleActor.create(tokenCalculator, 10_000))
        
        // Send many requests rapidly to potentially overflow queue
        repeat(150) { i ->
            throttleActor.tell(ProcessLLMRequest("request-$i", "Test", probe.ref()))
        }
        
        // Count different response types
        var successCount = 0
        var throttledCount = 0
        var failedCount = 0
        
        repeat(150) {
            val response = probe.expectMessageClass(LLMThrottleResponse::class.java, Duration.ofSeconds(10))
            when (response) {
                is LLMResponse -> successCount++
                is LLMThrottled -> throttledCount++
                is LLMFailed -> failedCount++
            }
        }
        
        // Should handle all requests in some way
        (successCount + throttledCount + failedCount) shouldBe 150
        
        // Check state
        val stateProbe = testKit.createTestProbe<ThrottleState>()
        throttleActor.tell(GetThrottleState(stateProbe.ref()))
        val state = stateProbe.expectMessageClass(ThrottleState::class.java)
        
        state.currentTokensUsed shouldBeGreaterThan 0
    }
    
    @Test
    fun `should reject requests over capacity and add to failed queue`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val tokenCalculator = FixedTokenCalculator(2000) // Large tokens per request
        val throttleActor = testKit.spawn(LLMStreamThrottleActor.create(tokenCalculator, 10_000))
        
        // Send requests to exceed capacity
        repeat(6) { i -> // 6 * 2000 = 12000 > 10000 limit
            throttleActor.tell(ProcessLLMRequest("request-$i", "Test", probe.ref()))
        }
        
        // Should receive mix of responses and failures
        var failedCount = 0
        repeat(6) {
            val response = probe.expectMessageClass(LLMThrottleResponse::class.java, Duration.ofSeconds(5))
            if (response is LLMFailed) {
                failedCount++
            }
        }
        
        // At least some should fail due to capacity
        failedCount shouldBeGreaterThan 0
        
        // Check failed requests
        val failedProbe = testKit.createTestProbe<FailedRequestsList>()
        throttleActor.tell(GetFailedRequests(failedProbe.ref()))
        val failedList = failedProbe.expectMessageClass(FailedRequestsList::class.java)
        
        failedList.requests.size shouldBeGreaterThan 0
    }
    
    @Test
    fun `should handle stream processing with automatic rate adjustment`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val tokenCalculator = FixedTokenCalculator(500)
        val throttleActor = testKit.spawn(LLMStreamThrottleActor.create(tokenCalculator, 10_000))
        
        // Send requests gradually to test rate adjustment
        repeat(10) { i ->
            throttleActor.tell(ProcessLLMRequest("request-$i", "Test", probe.ref()))
            Thread.sleep(100) // Small delay between requests
        }
        
        // All should be processed successfully or throttled
        repeat(10) {
            val response = probe.expectMessageClass(LLMThrottleResponse::class.java, Duration.ofSeconds(5))
            response.shouldBeInstanceOf<LLMThrottleResponse>()
        }
        
        // Check final state
        val stateProbe = testKit.createTestProbe<ThrottleState>()
        throttleActor.tell(GetThrottleState(stateProbe.ref()))
        val state = stateProbe.expectMessageClass(ThrottleState::class.java)
        
        state.currentTokensUsed shouldBe 5000 // 10 * 500
        state.capacityPercent shouldBe 50.0
    }
    
    @Test
    fun `should retry failed requests`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val tokenCalculator = FixedTokenCalculator(3000) // Large tokens to cause failures
        val throttleActor = testKit.spawn(LLMStreamThrottleActor.create(tokenCalculator, 10_000))
        
        // Send requests that will exceed capacity
        repeat(5) { i ->
            throttleActor.tell(ProcessLLMRequest("request-$i", "Test", probe.ref()))
        }
        
        // Wait for processing
        repeat(5) {
            probe.expectMessageClass(LLMThrottleResponse::class.java, Duration.ofSeconds(5))
        }
        
        // Check if any failed
        val failedProbe = testKit.createTestProbe<FailedRequestsList>()
        throttleActor.tell(GetFailedRequests(failedProbe.ref()))
        val failedList = failedProbe.expectMessageClass(FailedRequestsList::class.java)
        
        val initialFailedCount = failedList.requests.size
        
        // Retry failed requests
        throttleActor.tell(RetryFailedRequests)
        
        // Give some time for retry processing
        Thread.sleep(1000)
        
        // Check if failed count reduced
        throttleActor.tell(GetFailedRequests(failedProbe.ref()))
        val afterRetryList = failedProbe.expectMessageClass(FailedRequestsList::class.java)
        
        // After retry, failed count should be 0 or reduced
        afterRetryList.requests.size shouldBe 0
    }
    
    @Test
    fun `should handle sliding window token expiration`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val stateProbe = testKit.createTestProbe<ThrottleState>()
        val tokenCalculator = FixedTokenCalculator(1000)
        val throttleActor = testKit.spawn(LLMStreamThrottleActor.create(tokenCalculator, 10_000))
        
        // Use 50% capacity
        repeat(5) { i ->
            throttleActor.tell(ProcessLLMRequest("request-$i", "Test", probe.ref()))
        }
        
        // Wait for processing
        repeat(5) {
            probe.expectMessageClass(LLMThrottleResponse::class.java, Duration.ofSeconds(5))
        }
        
        // Check initial state
        throttleActor.tell(GetThrottleState(stateProbe.ref()))
        var state = stateProbe.expectMessageClass(ThrottleState::class.java)
        state.currentTokensUsed shouldBe 5000
        
        // Advance time past window
        manualTime.timePasses(Duration.ofSeconds(61))
        
        // Send new requests
        repeat(3) { i ->
            throttleActor.tell(ProcessLLMRequest("request-new-$i", "Test", probe.ref()))
        }
        
        // Wait for processing
        repeat(3) {
            probe.expectMessageClass(LLMThrottleResponse::class.java, Duration.ofSeconds(5))
        }
        
        // Check state - should only show new tokens
        throttleActor.tell(GetThrottleState(stateProbe.ref()))
        state = stateProbe.expectMessageClass(ThrottleState::class.java)
        state.currentTokensUsed shouldBe 3000 // Only new tokens
    }
}