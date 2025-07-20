package com.example.actorstream.processor

import com.example.actorstream.model.StreamResult
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors

class JavaStreamProcessor {
    
    fun processText(text: String): CompletableFuture<StreamResult> {
        return CompletableFuture.supplyAsync {
            val parts = text.split("\\s+".toRegex())
            
            // Split into words and numbers using Java Streams
            val words = parts.stream()
                .filter { it.matches("[^\\d]+".toRegex()) && it.isNotBlank() }
                .collect(Collectors.toList())
            
            val numberSum = parts.stream()
                .filter { it.matches("\\d+".toRegex()) }
                .mapToInt { it.toInt() }
                .sum()
            
            StreamResult(
                originalText = text,
                words = words,
                wordCount = words.size,
                numberSum = numberSum
            )
        }
    }
    
    fun processTextWithBuffering(texts: List<String>, bufferSize: Int = 10): List<StreamResult> {
        return texts.stream()
            .parallel()
            .map { text ->
                val parts = text.split("\\s+".toRegex())
                
                val words = parts.stream()
                    .filter { it.matches("[^\\d]+".toRegex()) && it.isNotBlank() }
                    .collect(Collectors.toList())
                
                val numberSum = parts.stream()
                    .filter { it.matches("\\d+".toRegex()) }
                    .mapToInt { it.toInt() }
                    .sum()
                
                StreamResult(
                    originalText = text,
                    words = words,
                    wordCount = words.size,
                    numberSum = numberSum
                )
            }
            .collect(Collectors.toList())
    }
}