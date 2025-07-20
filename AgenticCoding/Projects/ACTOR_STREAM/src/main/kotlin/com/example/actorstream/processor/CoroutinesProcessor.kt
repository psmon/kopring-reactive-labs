package com.example.actorstream.processor

import com.example.actorstream.model.StreamResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.*

class CoroutinesProcessor {
    
    suspend fun processText(text: String): StreamResult = coroutineScope {
        val parts = text.split("\\s+".toRegex())
        
        // Process words and numbers in parallel
        val wordsDeferred = async {
            parts.filter { it.matches("[^\\d]+".toRegex()) && it.isNotBlank() }
        }
        
        val numberSumDeferred = async {
            parts.filter { it.matches("\\d+".toRegex()) }
                .map { it.toInt() }
                .sum()
        }
        
        val words = wordsDeferred.await()
        val numberSum = numberSumDeferred.await()
        
        StreamResult(
            originalText = text,
            words = words,
            wordCount = words.size,
            numberSum = numberSum
        )
    }
    
    fun processTextAsFlow(text: String): Flow<StreamResult> = flow {
        val parts = text.split("\\s+".toRegex())
        
        val wordsFlow = parts.asFlow()
            .filter { it.matches("[^\\d]+".toRegex()) && it.isNotBlank() }
            .toList()
        
        val numberSum = parts.asFlow()
            .filter { it.matches("\\d+".toRegex()) }
            .map { it.toInt() }
            .fold(0) { acc, num -> acc + num }
        
        emit(StreamResult(
            originalText = text,
            words = wordsFlow,
            wordCount = wordsFlow.size,
            numberSum = numberSum
        ))
    }.flowOn(Dispatchers.Default)
    
    fun processTextsWithBackpressure(texts: Flow<String>): Flow<StreamResult> {
        return texts
            .buffer(capacity = 100) // Backpressure handling
            .map { text ->
                val parts = text.split("\\s+".toRegex())
                
                coroutineScope {
                    val wordsDeferred = async {
                        parts.filter { it.matches("[^\\d]+".toRegex()) && it.isNotBlank() }
                    }
                    
                    val numberSumDeferred = async {
                        parts.filter { it.matches("\\d+".toRegex()) }
                            .map { it.toInt() }
                            .sum()
                    }
                    
                    val words = wordsDeferred.await()
                    val numberSum = numberSumDeferred.await()
                    
                    StreamResult(
                        originalText = text,
                        words = words,
                        wordCount = words.size,
                        numberSum = numberSum
                    )
                }
            }
            .flowOn(Dispatchers.Default)
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun processWithChannels(text: String): StreamResult = coroutineScope {
        val parts = text.split("\\s+".toRegex())
        
        // Create channels for words and numbers
        val wordsChannel = produce {
            parts.forEach { part ->
                if (part.matches("[^\\d]+".toRegex()) && part.isNotBlank()) {
                    send(part)
                }
            }
        }
        
        val numbersChannel = produce {
            parts.forEach { part ->
                if (part.matches("\\d+".toRegex())) {
                    send(part.toInt())
                }
            }
        }
        
        // Process channels concurrently
        val words = mutableListOf<String>()
        val numberSum = async {
            var sum = 0
            for (num in numbersChannel) {
                sum += num
            }
            sum
        }
        
        for (word in wordsChannel) {
            words.add(word)
        }
        
        StreamResult(
            originalText = text,
            words = words,
            wordCount = words.size,
            numberSum = numberSum.await()
        )
    }
}