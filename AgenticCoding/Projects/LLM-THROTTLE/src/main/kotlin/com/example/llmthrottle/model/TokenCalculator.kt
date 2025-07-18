package com.example.llmthrottle.model

import kotlin.random.Random

/**
 * Interface for calculating tokens from text content
 */
interface TokenCalculator {
    fun calculateTokens(text: String): Int
}

/**
 * Mock implementation of TokenCalculator
 * Uses character count + random value (0-500) to simulate token calculation
 */
class MockTokenCalculator : TokenCalculator {
    override fun calculateTokens(text: String): Int {
        val baseTokens = text.length
        val randomTokens = Random.nextInt(0, 501) // 0 to 500
        return baseTokens + randomTokens
    }
}

/**
 * Token window for tracking usage over time
 */
data class TokenWindow(
    val startTime: Long,
    val endTime: Long,
    val tokensUsed: Int
) {
    fun isExpired(currentTime: Long): Boolean = currentTime >= endTime
    
    fun containsTime(time: Long): Boolean = time in startTime until endTime
}