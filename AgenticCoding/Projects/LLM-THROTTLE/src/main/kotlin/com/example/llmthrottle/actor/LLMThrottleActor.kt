package com.example.llmthrottle.actor

import com.example.llmthrottle.model.*
import org.apache.pekko.actor.Cancellable
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.PostStop
import org.apache.pekko.actor.typed.javadsl.*
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Actor that implements LLM request throttling with backpressure
 * 
 * Features:
 * - Token-based rate limiting (10,000 tokens per minute)
 * - Progressive backpressure based on capacity
 * - Failed request queue for retry
 * - Sliding window token tracking
 */
class LLMThrottleActor private constructor(
    context: ActorContext<LLMThrottleCommand>,
    private val tokenCalculator: TokenCalculator = MockTokenCalculator(),
    private val tokenLimitPerMinute: Int = 10_000
) : AbstractBehavior<LLMThrottleCommand>(context) {
    
    companion object {
        fun create(
            tokenCalculator: TokenCalculator = MockTokenCalculator(),
            tokenLimitPerMinute: Int = 10_000
        ): Behavior<LLMThrottleCommand> {
            return Behaviors.setup { context ->
                LLMThrottleActor(context, tokenCalculator, tokenLimitPerMinute)
            }
        }
    }
    
    // Token tracking with sliding windows
    private val tokenWindows = mutableListOf<TokenWindow>()
    private val failedRequests = ConcurrentLinkedQueue<FailedRequest>()
    private val stashedRequests = mutableListOf<ProcessLLMRequest>()
    
    // Window configuration
    private val windowSizeMs = 60_000L // 1 minute
    private val cleanupIntervalMs = 5_000L // cleanup every 5 seconds
    
    // Throttle thresholds
    private val throttleThresholds = mapOf(
        0.70 to 100L,    // 70% capacity: 100ms delay
        0.80 to 300L,    // 80% capacity: 300ms delay
        0.90 to 1000L,   // 90% capacity: 1s delay
        0.95 to 2000L    // 95% capacity: 2s delay
    )
    
    // Cleanup timer
    private var cleanupTimer: Cancellable? = null
    
    init {
        startCleanupTimer()
    }
    
    override fun createReceive(): Receive<LLMThrottleCommand> {
        return newReceiveBuilder()
            .onMessage(ProcessLLMRequest::class.java, this::onProcessLLMRequest)
            .onMessage(GetThrottleState::class.java, this::onGetThrottleState)
            .onMessage(GetFailedRequests::class.java, this::onGetFailedRequests)
            .onMessageEquals(RetryFailedRequests) { onRetryFailedRequests() }
            .onMessageEquals(CleanupExpiredWindows) { onCleanupExpiredWindows() }
            .onMessageEquals(ProcessStashedRequests) { onProcessStashedRequests() }
            .onSignal(PostStop::class.java, this::onPostStop)
            .build()
    }
    
    private fun onProcessLLMRequest(request: ProcessLLMRequest): Behavior<LLMThrottleCommand> {
        val currentTime = System.currentTimeMillis()
        val requestTokens = tokenCalculator.calculateTokens(request.content)
        val currentUsage = getCurrentTokenUsage(currentTime)
        val capacityPercent = currentUsage.toDouble() / tokenLimitPerMinute
        
        context.log.info("Processing request ${request.requestId}: $requestTokens tokens, current usage: $currentUsage/$tokenLimitPerMinute (${String.format("%.2f", capacityPercent * 100)}%)")
        
        when {
            // Over capacity - add to failed list
            currentUsage + requestTokens > tokenLimitPerMinute -> {
                failedRequests.offer(FailedRequest(
                    requestId = request.requestId,
                    content = request.content,
                    failedAt = currentTime,
                    reason = "Token limit exceeded. Current: $currentUsage, Required: $requestTokens, Limit: $tokenLimitPerMinute"
                ))
                
                request.replyTo.tell(LLMFailed(
                    requestId = request.requestId,
                    reason = "Token limit exceeded. Request added to retry queue.",
                    canRetry = true
                ))
            }
            
            // Apply throttling based on capacity
            capacityPercent >= 0.70 -> {
                val delay = getDelayForCapacity(capacityPercent)
                
                // Add to stashed requests to process after delay
                stashedRequests.add(request)
                
                request.replyTo.tell(LLMThrottled(
                    requestId = request.requestId,
                    delayMs = delay,
                    currentCapacityPercent = capacityPercent * 100,
                    message = "Request throttled due to high usage. Processing with ${delay}ms delay."
                ))
                
                // Schedule processing after delay
                context.scheduleOnce(
                    Duration.ofMillis(delay),
                    context.self,
                    ProcessStashedRequests
                )
            }
            
            // Process immediately
            else -> {
                processRequestImmediately(request, requestTokens, currentTime)
            }
        }
        
        return this
    }
    
    private fun processRequestImmediately(
        request: ProcessLLMRequest,
        requestTokens: Int,
        startTime: Long
    ) {
        // Add token usage to current window
        val windowStart = (startTime / windowSizeMs) * windowSizeMs
        val windowEnd = windowStart + windowSizeMs
        
        val existingWindow = tokenWindows.find { it.startTime == windowStart }
        if (existingWindow != null) {
            tokenWindows.remove(existingWindow)
            tokenWindows.add(existingWindow.copy(tokensUsed = existingWindow.tokensUsed + requestTokens))
        } else {
            tokenWindows.add(TokenWindow(windowStart, windowEnd, requestTokens))
        }
        
        // Simulate processing
        val processedContent = "Processed: ${request.content}"
        val processingTime = System.currentTimeMillis() - startTime
        
        request.replyTo.tell(LLMResponse(
            requestId = request.requestId,
            processedContent = processedContent,
            tokensUsed = requestTokens,
            processingTimeMs = processingTime
        ))
    }
    
    private fun onProcessStashedRequests(): Behavior<LLMThrottleCommand> {
        if (stashedRequests.isNotEmpty()) {
            val request = stashedRequests.removeAt(0)
            val currentTime = System.currentTimeMillis()
            val requestTokens = tokenCalculator.calculateTokens(request.content)
            
            // Re-check capacity before processing
            val currentUsage = getCurrentTokenUsage(currentTime)
            if (currentUsage + requestTokens <= tokenLimitPerMinute) {
                processRequestImmediately(request, requestTokens, currentTime)
            } else {
                // Still over capacity, add to failed list
                failedRequests.offer(FailedRequest(
                    requestId = request.requestId,
                    content = request.content,
                    failedAt = currentTime,
                    reason = "Still over capacity after throttling"
                ))
                
                request.replyTo.tell(LLMFailed(
                    requestId = request.requestId,
                    reason = "Token limit still exceeded after delay. Request added to retry queue.",
                    canRetry = true
                ))
            }
        }
        
        return this
    }
    
    private fun onGetThrottleState(command: GetThrottleState): Behavior<LLMThrottleCommand> {
        val currentTime = System.currentTimeMillis()
        val currentUsage = getCurrentTokenUsage(currentTime)
        val capacityPercent = currentUsage.toDouble() / tokenLimitPerMinute
        
        command.replyTo.tell(ThrottleState(
            currentTokensUsed = currentUsage,
            tokenLimit = tokenLimitPerMinute,
            capacityPercent = capacityPercent * 100,
            windowSizeMinutes = (windowSizeMs / 60_000).toInt(),
            failedRequestsCount = failedRequests.size
        ))
        
        return this
    }
    
    private fun onGetFailedRequests(command: GetFailedRequests): Behavior<LLMThrottleCommand> {
        command.replyTo.tell(FailedRequestsList(failedRequests.toList()))
        return this
    }
    
    private fun onRetryFailedRequests(): Behavior<LLMThrottleCommand> {
        context.log.info("Retrying ${failedRequests.size} failed requests")
        
        // Process failed requests in batch
        val requestsToRetry = mutableListOf<FailedRequest>()
        while (failedRequests.isNotEmpty()) {
            failedRequests.poll()?.let { requestsToRetry.add(it) }
        }
        
        // Note: In a real implementation, you would re-submit these through the normal flow
        // For this example, we just log
        requestsToRetry.forEach { failedRequest ->
            context.log.info("Would retry request ${failedRequest.requestId}")
        }
        
        return this
    }
    
    private fun onCleanupExpiredWindows(): Behavior<LLMThrottleCommand> {
        val currentTime = System.currentTimeMillis()
        val before = tokenWindows.size
        tokenWindows.removeIf { it.isExpired(currentTime) }
        val after = tokenWindows.size
        
        if (before != after) {
            context.log.debug("Cleaned up ${before - after} expired token windows")
        }
        
        return this
    }
    
    private fun onPostStop(signal: PostStop): Behavior<LLMThrottleCommand> {
        cleanupTimer?.cancel()
        context.log.info("LLMThrottleActor stopped. Had ${failedRequests.size} failed requests.")
        return this
    }
    
    private fun getCurrentTokenUsage(currentTime: Long): Int {
        val windowStart = currentTime - windowSizeMs
        // Sum all tokens from windows that are still within the sliding window
        return tokenWindows
            .filter { window -> window.startTime >= windowStart }
            .sumOf { it.tokensUsed }
    }
    
    private fun getDelayForCapacity(capacityPercent: Double): Long {
        return throttleThresholds.entries
            .sortedByDescending { it.key }
            .firstOrNull { capacityPercent >= it.key }
            ?.value ?: 0L
    }
    
    private fun startCleanupTimer() {
        cleanupTimer = context.system.scheduler().scheduleWithFixedDelay(
            Duration.ofMillis(cleanupIntervalMs),
            Duration.ofMillis(cleanupIntervalMs),
            { context.self.tell(CleanupExpiredWindows) },
            context.executionContext
        )
    }
}