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

class LLMThrottleActorTest {
    
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
    fun `should process requests immediately when under 70% capacity`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val tokenCalculator = FixedTokenCalculator(100) // 100 tokens per request
        val throttleActor = testKit.spawn(LLMThrottleActor.create(tokenCalculator, 10_000))
        
        // Send requests that use 50% capacity (50 * 100 = 5000 tokens out of 10000)
        repeat(50) { i ->
            val requestId = "request-$i"
            throttleActor.tell(ProcessLLMRequest(requestId, "Test content $i", probe.ref()))
            
            val response = probe.expectMessageClass(LLMThrottleResponse::class.java)
            response.shouldBeInstanceOf<LLMResponse>()
            response.requestId shouldBe requestId
            response.tokensUsed shouldBe 100
        }
        
        // Check state
        val stateProbe = testKit.createTestProbe<ThrottleState>()
        throttleActor.tell(GetThrottleState(stateProbe.ref()))
        val state = stateProbe.expectMessageClass(ThrottleState::class.java)
        
        state.currentTokensUsed shouldBe 5000
        state.capacityPercent shouldBe 50.0
        state.failedRequestsCount shouldBe 0
    }
    
    @Test
    fun `should apply 100ms delay when capacity is between 70% and 80%`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val tokenCalculator = FixedTokenCalculator(100)
        val throttleActor = testKit.spawn(LLMThrottleActor.create(tokenCalculator, 10_000))
        
        // Use 75% capacity (75 * 100 = 7500 tokens)
        repeat(75) { i ->
            throttleActor.tell(ProcessLLMRequest("request-$i", "Test", probe.ref()))
            probe.expectMessageClass(LLMThrottleResponse::class.java)
        }
        
        // Next request should be throttled
        val requestId = generateRequestId()
        throttleActor.tell(ProcessLLMRequest(requestId, "Test throttled", probe.ref()))
        
        val response = probe.expectMessageClass(LLMThrottleResponse::class.java)
        response.shouldBeInstanceOf<LLMThrottled>()
        response.requestId shouldBe requestId
        response.delayMs shouldBe 100L
        response.currentCapacityPercent shouldBeGreaterThanOrEqual 75.0
        response.currentCapacityPercent shouldBeLessThan 80.0
    }
    
    @Test
    fun `should apply 300ms delay when capacity is between 80% and 90%`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val tokenCalculator = FixedTokenCalculator(100)
        val throttleActor = testKit.spawn(LLMThrottleActor.create(tokenCalculator, 10_000))
        
        // Use 85% capacity (85 * 100 = 8500 tokens)
        repeat(85) { i ->
            throttleActor.tell(ProcessLLMRequest("request-$i", "Test", probe.ref()))
            probe.expectMessageClass(LLMThrottleResponse::class.java)
        }
        
        // Next request should be throttled with higher delay
        val requestId = generateRequestId()
        throttleActor.tell(ProcessLLMRequest(requestId, "Test throttled", probe.ref()))
        
        val response = probe.expectMessageClass(LLMThrottleResponse::class.java)
        response.shouldBeInstanceOf<LLMThrottled>()
        response.delayMs shouldBe 300L
    }
    
    @Test
    fun `should apply 1000ms delay when capacity is between 90% and 95%`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val tokenCalculator = FixedTokenCalculator(100)
        val throttleActor = testKit.spawn(LLMThrottleActor.create(tokenCalculator, 10_000))
        
        // Use 92% capacity (92 * 100 = 9200 tokens)
        repeat(92) { i ->
            throttleActor.tell(ProcessLLMRequest("request-$i", "Test", probe.ref()))
            probe.expectMessageClass(LLMThrottleResponse::class.java)
        }
        
        // Next request should be throttled with even higher delay
        val requestId = generateRequestId()
        throttleActor.tell(ProcessLLMRequest(requestId, "Test throttled", probe.ref()))
        
        val response = probe.expectMessageClass(LLMThrottleResponse::class.java)
        response.shouldBeInstanceOf<LLMThrottled>()
        response.delayMs shouldBe 1000L
    }
    
    @Test
    fun `should apply 2000ms delay when capacity is above 95%`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val tokenCalculator = FixedTokenCalculator(100)
        val throttleActor = testKit.spawn(LLMThrottleActor.create(tokenCalculator, 10_000))
        
        // Use 96% capacity (96 * 100 = 9600 tokens)
        repeat(96) { i ->
            throttleActor.tell(ProcessLLMRequest("request-$i", "Test", probe.ref()))
            probe.expectMessageClass(LLMThrottleResponse::class.java)
        }
        
        // Next request should be throttled with maximum delay
        val requestId = generateRequestId()
        throttleActor.tell(ProcessLLMRequest(requestId, "Test throttled", probe.ref()))
        
        val response = probe.expectMessageClass(LLMThrottleResponse::class.java)
        response.shouldBeInstanceOf<LLMThrottled>()
        response.delayMs shouldBe 2000L
    }
    
    @Test
    fun `should add request to failed list when over capacity`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val tokenCalculator = FixedTokenCalculator(1000) // Large tokens per request
        val throttleActor = testKit.spawn(LLMThrottleActor.create(tokenCalculator, 10_000))
        
        // Use up most capacity (9 * 1000 = 9000 tokens)
        repeat(9) { i ->
            throttleActor.tell(ProcessLLMRequest("request-$i", "Test", probe.ref()))
            probe.expectMessageClass(LLMThrottleResponse::class.java)
        }
        
        // Next request would exceed capacity (would be 11000 tokens total)
        val requestId = generateRequestId()
        throttleActor.tell(ProcessLLMRequest(requestId, "Test overflow", probe.ref()))
        
        val response = probe.expectMessageClass(LLMThrottleResponse::class.java)
        response.shouldBeInstanceOf<LLMFailed>()
        response.requestId shouldBe requestId
        response.canRetry shouldBe true
        
        // Check failed requests
        val failedProbe = testKit.createTestProbe<FailedRequestsList>()
        throttleActor.tell(GetFailedRequests(failedProbe.ref()))
        val failedList = failedProbe.expectMessageClass(FailedRequestsList::class.java)
        
        failedList.requests.size shouldBe 1
        failedList.requests[0].requestId shouldBe requestId
    }
    
    @Test
    fun `should process stashed requests after delay`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val tokenCalculator = FixedTokenCalculator(100)
        val throttleActor = testKit.spawn(LLMThrottleActor.create(tokenCalculator, 10_000))
        
        // Use 75% capacity to trigger throttling
        repeat(75) { i ->
            throttleActor.tell(ProcessLLMRequest("request-$i", "Test", probe.ref()))
            probe.expectMessageClass(LLMThrottleResponse::class.java)
        }
        
        // Send throttled request
        val requestId = generateRequestId()
        throttleActor.tell(ProcessLLMRequest(requestId, "Test throttled", probe.ref()))
        
        // Should receive throttled response first
        val throttledResponse = probe.expectMessageClass(LLMThrottleResponse::class.java)
        throttledResponse.shouldBeInstanceOf<LLMThrottled>()
        
        // Advance time to trigger stashed processing
        manualTime.timePasses(Duration.ofMillis(150))
        
        // Should eventually receive actual response
        val actualResponse = probe.expectMessageClass(LLMThrottleResponse::class.java, Duration.ofSeconds(3))
        actualResponse.shouldBeInstanceOf<LLMResponse>()
        actualResponse.requestId shouldBe requestId
    }
    
    @Test
    fun `should handle sliding window token expiration`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val stateProbe = testKit.createTestProbe<ThrottleState>()
        val tokenCalculator = FixedTokenCalculator(1000)
        val throttleActor = testKit.spawn(LLMThrottleActor.create(tokenCalculator, 10_000))
        
        // Use 50% capacity at time 0
        repeat(5) { i ->
            throttleActor.tell(ProcessLLMRequest("request-$i", "Test", probe.ref()))
            probe.expectMessageClass(LLMThrottleResponse::class.java)
        }
        
        // Check initial state
        throttleActor.tell(GetThrottleState(stateProbe.ref()))
        var state = stateProbe.expectMessageClass(ThrottleState::class.java)
        state.currentTokensUsed shouldBe 5000
        
        // Advance time by 61 seconds - past the window
        manualTime.timePasses(Duration.ofSeconds(61))
        
        // Add more requests in new window
        repeat(3) { i ->
            throttleActor.tell(ProcessLLMRequest("request-new-$i", "Test", probe.ref()))
            probe.expectMessageClass(LLMThrottleResponse::class.java)
        }
        
        // Check state - should only show new tokens as old ones are outside window
        throttleActor.tell(GetThrottleState(stateProbe.ref()))
        state = stateProbe.expectMessageClass(ThrottleState::class.java)
        state.currentTokensUsed shouldBe 3000 // Only new tokens in current window
    }
    
    @Test
    fun `should calculate proportional token usage in sliding window`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val stateProbe = testKit.createTestProbe<ThrottleState>()
        val tokenCalculator = FixedTokenCalculator(6000) // Large chunk
        val throttleActor = testKit.spawn(LLMThrottleActor.create(tokenCalculator, 10_000))
        
        // Send first request
        throttleActor.tell(ProcessLLMRequest("request-1", "Test", probe.ref()))
        probe.expectMessageClass(LLMThrottleResponse::class.java)
        
        // Check initial state
        throttleActor.tell(GetThrottleState(stateProbe.ref()))
        var state = stateProbe.expectMessageClass(ThrottleState::class.java)
        state.currentTokensUsed shouldBe 6000
        
        // Advance time by 30 seconds (half window) - tokens still count fully
        manualTime.timePasses(Duration.ofSeconds(30))
        
        // Check state - tokens are still within window
        throttleActor.tell(GetThrottleState(stateProbe.ref()))
        state = stateProbe.expectMessageClass(ThrottleState::class.java)
        state.currentTokensUsed shouldBe 6000 // Still counts fully until window expires
    }
    
    @Test
    fun `should handle multiple concurrent throttled requests`() = runTest {
        val probe = testKit.createTestProbe<LLMThrottleResponse>()
        val tokenCalculator = FixedTokenCalculator(100)
        val throttleActor = testKit.spawn(LLMThrottleActor.create(tokenCalculator, 10_000))
        
        // Fill to 75% capacity
        repeat(75) { i ->
            throttleActor.tell(ProcessLLMRequest("request-$i", "Test", probe.ref()))
            probe.expectMessageClass(LLMThrottleResponse::class.java)
        }
        
        // Send multiple requests that will be throttled
        val throttledIds = (1..5).map { "throttled-$it" }
        throttledIds.forEach { id ->
            throttleActor.tell(ProcessLLMRequest(id, "Test throttled", probe.ref()))
        }
        
        // Should receive throttled responses
        repeat(5) {
            val response = probe.expectMessageClass(LLMThrottleResponse::class.java)
            response.shouldBeInstanceOf<LLMThrottled>()
        }
        
        // Advance time and expect stashed processing
        manualTime.timePasses(Duration.ofMillis(200))
        
        // Should process at least one stashed request
        val processedResponse = probe.expectMessageClass(LLMThrottleResponse::class.java, Duration.ofSeconds(3))
        processedResponse.shouldBeInstanceOf<LLMResponse>()
    }
}