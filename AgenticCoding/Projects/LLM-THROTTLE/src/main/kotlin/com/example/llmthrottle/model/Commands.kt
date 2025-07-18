package com.example.llmthrottle.model

import org.apache.pekko.actor.typed.ActorRef

/**
 * Base interface for all commands
 */
sealed interface LLMThrottleCommand

/**
 * Commands for LLMThrottleActor
 */
data class ProcessLLMRequest(
    val requestId: String,
    val content: String,
    val replyTo: ActorRef<LLMThrottleResponse>
) : LLMThrottleCommand

/**
 * Internal command for cleaning up expired token windows
 */
internal object CleanupExpiredWindows : LLMThrottleCommand

/**
 * Internal command for processing stashed requests
 */
internal object ProcessStashedRequests : LLMThrottleCommand

/**
 * Command to get current throttle state
 */
data class GetThrottleState(
    val replyTo: ActorRef<ThrottleState>
) : LLMThrottleCommand

/**
 * Command to get failed requests
 */
data class GetFailedRequests(
    val replyTo: ActorRef<FailedRequestsList>
) : LLMThrottleCommand

/**
 * Command to retry failed requests
 */
object RetryFailedRequests : LLMThrottleCommand

/**
 * Responses from LLMThrottleActor
 */
sealed interface LLMThrottleResponse

/**
 * Successful response with processed content
 */
data class LLMResponse(
    val requestId: String,
    val processedContent: String,
    val tokensUsed: Int,
    val processingTimeMs: Long
) : LLMThrottleResponse

/**
 * Response when request is throttled but will be processed with delay
 */
data class LLMThrottled(
    val requestId: String,
    val delayMs: Long,
    val currentCapacityPercent: Double,
    val message: String
) : LLMThrottleResponse

/**
 * Response when request failed and was added to failed list
 */
data class LLMFailed(
    val requestId: String,
    val reason: String,
    val canRetry: Boolean
) : LLMThrottleResponse

/**
 * Current throttle state
 */
data class ThrottleState(
    val currentTokensUsed: Int,
    val tokenLimit: Int,
    val capacityPercent: Double,
    val windowSizeMinutes: Int,
    val failedRequestsCount: Int
)

/**
 * Failed requests list
 */
data class FailedRequestsList(
    val requests: List<FailedRequest>
)

/**
 * Failed request data
 */
data class FailedRequest(
    val requestId: String,
    val content: String,
    val failedAt: Long,
    val reason: String
)