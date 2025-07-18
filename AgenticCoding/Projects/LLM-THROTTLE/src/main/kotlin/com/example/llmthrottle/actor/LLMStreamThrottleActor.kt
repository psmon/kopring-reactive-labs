package com.example.llmthrottle.actor

import com.example.llmthrottle.model.*
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.PostStop
import org.apache.pekko.actor.typed.javadsl.*
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.javadsl.*
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * LLMStreamThrottleActor - Pekko Streams Throttle을 활용한 향상된 버전
 * 
 * 주요 특징:
 * - Pekko Streams의 내장 throttle 사용
 * - 자동 속도 조절 (토큰 사용량에 따라 스트림 속도 동적 조절)
 * - 백프레셔 메커니즘 최적화
 * - 비동기 스트림 처리
 */
class LLMStreamThrottleActor private constructor(
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
                LLMStreamThrottleActor(context, tokenCalculator, tokenLimitPerMinute)
            }
        }
    }
    
    // Stream infrastructure
    private val materializer = Materializer.createMaterializer(context.system)
    private val streamSetup = Source.queue<QueuedRequest>(100, OverflowStrategy.dropNew())
        .toMat(createProcessingFlow(), Keep.both())
        .run(materializer)
    private val queue = streamSetup.first()
    private val completionFuture = streamSetup.second()
    
    // Token tracking
    private val tokenWindows = ConcurrentHashMap<Long, TokenWindow>()
    private val failedRequests = ConcurrentLinkedQueue<FailedRequest>()
    private val currentTokenCount = AtomicInteger(0)
    private val lastWindowCleanup = AtomicLong(System.currentTimeMillis())
    
    // Window configuration
    private val windowSizeMs = 60_000L
    private val cleanupIntervalMs = 5_000L
    
    // Dynamic throttling parameters
    private val baseThrottleRate = 10 // requests per second at 0% capacity
    private val minThrottleRate = 1   // requests per second at 100% capacity
    
    data class QueuedRequest(
        val request: ProcessLLMRequest,
        val queuedAt: Long,
        val tokens: Int
    )
    
    override fun createReceive(): Receive<LLMThrottleCommand> {
        return newReceiveBuilder()
            .onMessage(ProcessLLMRequest::class.java, this::onProcessLLMRequest)
            .onMessage(GetThrottleState::class.java, this::onGetThrottleState)
            .onMessage(GetFailedRequests::class.java, this::onGetFailedRequests)
            .onMessageEquals(RetryFailedRequests) { onRetryFailedRequests() }
            .onSignal(PostStop::class.java, this::onPostStop)
            .build()
    }
    
    private fun onProcessLLMRequest(request: ProcessLLMRequest): Behavior<LLMThrottleCommand> {
        val currentTime = System.currentTimeMillis()
        val tokens = tokenCalculator.calculateTokens(request.content)
        
        // Cleanup expired windows
        cleanupExpiredWindows(currentTime)
        
        // Calculate current usage
        val currentUsage = getCurrentTokenUsage(currentTime)
        val capacityPercent = currentUsage.toDouble() / tokenLimitPerMinute
        
        context.log.info("Processing request ${request.requestId}: $tokens tokens, current usage: $currentUsage/$tokenLimitPerMinute (${String.format("%.2f", capacityPercent * 100)}%)")
        
        // Check if over capacity
        if (currentUsage + tokens > tokenLimitPerMinute) {
            failedRequests.offer(FailedRequest(
                requestId = request.requestId,
                content = request.content,
                failedAt = currentTime,
                reason = "Token limit exceeded. Current: $currentUsage, Required: $tokens, Limit: $tokenLimitPerMinute"
            ))
            
            request.replyTo.tell(LLMFailed(
                requestId = request.requestId,
                reason = "Token limit exceeded. Request added to retry queue.",
                canRetry = true
            ))
            
            return this
        }
        
        // Queue request for stream processing
        val queuedRequest = QueuedRequest(request, currentTime, tokens)
        
        // Simple offer without complex result handling
        queue.offer(queuedRequest)
        
        // If high capacity, notify about throttling
        if (capacityPercent >= 0.70) {
            val delay = calculateDelayForCapacity(capacityPercent)
            request.replyTo.tell(LLMThrottled(
                requestId = request.requestId,
                delayMs = delay,
                currentCapacityPercent = capacityPercent * 100,
                message = "Request queued for stream processing with automatic throttling."
            ))
        }
        
        return this
    }
    
    private fun createProcessingFlow(): Sink<QueuedRequest, NotUsed> {
        return Flow.of(QueuedRequest::class.java)
            .buffer(50, OverflowStrategy.backpressure())
            .map { queuedRequest ->
                // Dynamic throttling based on current capacity
                val currentUsage = getCurrentTokenUsage(System.currentTimeMillis())
                val capacityPercent = currentUsage.toDouble() / tokenLimitPerMinute
                val dynamicRate = calculateDynamicRate(capacityPercent)
                
                ProcessingUnit(queuedRequest, dynamicRate)
            }
            .groupBy(10) { it.rate.toInt() } // Group by throttle rate
            .map { processingUnit ->
                // Apply dynamic throttling per group
                Source.single(processingUnit.queuedRequest)
                    .throttle(processingUnit.rate.toInt(), Duration.ofSeconds(1))
                    .runWith(Sink.foreach { this.processRequest(it) }, materializer)
                processingUnit
            }
            .mergeSubstreams()
            .to(Sink.ignore())
    }
    
    data class ProcessingUnit(
        val queuedRequest: QueuedRequest,
        val rate: Double
    )
    
    private fun processRequest(queuedRequest: QueuedRequest) {
        val currentTime = System.currentTimeMillis()
        val request = queuedRequest.request
        val tokens = queuedRequest.tokens
        
        try {
            // Add token usage to window
            val windowStart = (currentTime / windowSizeMs) * windowSizeMs
            val windowEnd = windowStart + windowSizeMs
            
            val window = tokenWindows.computeIfAbsent(windowStart) { 
                TokenWindow(windowStart, windowEnd, 0)
            }
            
            // Update window atomically
            tokenWindows[windowStart] = window.copy(tokensUsed = window.tokensUsed + tokens)
            
            // Simulate processing
            val processedContent = "Processed: ${request.content}"
            val processingTime = currentTime - queuedRequest.queuedAt
            
            // Send successful response
            request.replyTo.tell(LLMResponse(
                requestId = request.requestId,
                processedContent = processedContent,
                tokensUsed = tokens,
                processingTimeMs = processingTime
            ))
            
        } catch (e: Exception) {
            context.log.error("Error processing request ${request.requestId}", e)
            request.replyTo.tell(LLMFailed(
                requestId = request.requestId,
                reason = "Processing error: ${e.message}",
                canRetry = true
            ))
        }
    }
    
    private fun calculateDynamicRate(capacityPercent: Double): Double {
        // Calculate throttle rate based on capacity
        // Higher capacity = lower rate (more throttling)
        val rate = when {
            capacityPercent < 0.70 -> baseThrottleRate.toDouble()
            capacityPercent < 0.80 -> baseThrottleRate * 0.8
            capacityPercent < 0.90 -> baseThrottleRate * 0.5
            capacityPercent < 0.95 -> baseThrottleRate * 0.3
            else -> minThrottleRate.toDouble()
        }
        
        return maxOf(rate, minThrottleRate.toDouble())
    }
    
    private fun calculateDelayForCapacity(capacityPercent: Double): Long {
        return when {
            capacityPercent < 0.80 -> 100L
            capacityPercent < 0.90 -> 300L
            capacityPercent < 0.95 -> 1000L
            else -> 2000L
        }
    }
    
    private fun getCurrentTokenUsage(currentTime: Long): Int {
        val windowStart = currentTime - windowSizeMs
        return tokenWindows.values
            .filter { window -> window.startTime >= windowStart }
            .sumOf { it.tokensUsed }
    }
    
    private fun cleanupExpiredWindows(currentTime: Long) {
        val lastCleanup = lastWindowCleanup.get()
        if (currentTime - lastCleanup > cleanupIntervalMs) {
            if (lastWindowCleanup.compareAndSet(lastCleanup, currentTime)) {
                val windowStart = currentTime - windowSizeMs
                val toRemove = tokenWindows.keys.filter { it < windowStart }
                toRemove.forEach { tokenWindows.remove(it) }
                
                if (toRemove.isNotEmpty()) {
                    context.log.debug("Cleaned up ${toRemove.size} expired token windows")
                }
            }
        }
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
        
        val requestsToRetry = mutableListOf<FailedRequest>()
        while (failedRequests.isNotEmpty()) {
            failedRequests.poll()?.let { requestsToRetry.add(it) }
        }
        
        // Re-queue failed requests
        requestsToRetry.forEach { failedRequest ->
            val retryRequest = ProcessLLMRequest(
                requestId = failedRequest.requestId,
                content = failedRequest.content,
                replyTo = context.system.ignoreRef()
            )
            
            context.self.tell(retryRequest)
            context.log.info("Re-queued failed request ${failedRequest.requestId}")
        }
        
        return this
    }
    
    private fun onPostStop(signal: PostStop): Behavior<LLMThrottleCommand> {
        // Clean shutdown of stream
        queue.complete()
        
        context.log.info("LLMStreamThrottleActor stopped. Had ${failedRequests.size} failed requests.")
        return this
    }
}