package com.example.actorstream.processor

import com.example.actorstream.model.StreamResult
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

class WebFluxProcessor {
    
    fun processText(text: String): Mono<StreamResult> {
        return Mono.just(text)
            .map { it.split("\\s+".toRegex()) }
            .flatMap { parts ->
                val wordsFlux = Flux.fromIterable(parts)
                    .filter { it.matches("[^\\d]+".toRegex()) && it.isNotBlank() }
                    .collectList()
                
                val numberSumMono = Flux.fromIterable(parts)
                    .filter { it.matches("\\d+".toRegex()) }
                    .map { it.toInt() }
                    .reduce(0) { acc, num -> acc + num }
                
                Mono.zip(wordsFlux, numberSumMono)
                    .map { tuple ->
                        StreamResult(
                            originalText = text,
                            words = tuple.t1,
                            wordCount = tuple.t1.size,
                            numberSum = tuple.t2
                        )
                    }
            }
            .subscribeOn(Schedulers.parallel())
    }
    
    fun processTextWithBackpressure(texts: Flux<String>): Flux<StreamResult> {
        return texts
            .onBackpressureBuffer(100)
            .delayElements(Duration.ofMillis(10)) // Rate limiting
            .parallel()
            .runOn(Schedulers.parallel())
            .flatMap { text ->
                val parts = text.split("\\s+".toRegex())
                
                val wordsFlux = Flux.fromIterable(parts)
                    .filter { it.matches("[^\\d]+".toRegex()) && it.isNotBlank() }
                    .collectList()
                
                val numberSumMono = Flux.fromIterable(parts)
                    .filter { it.matches("\\d+".toRegex()) }
                    .map { it.toInt() }
                    .reduce(0) { acc, num -> acc + num }
                
                Mono.zip(wordsFlux, numberSumMono)
                    .map { tuple ->
                        StreamResult(
                            originalText = text,
                            words = tuple.t1,
                            wordCount = tuple.t1.size,
                            numberSum = tuple.t2
                        )
                    }
            }
            .sequential()
    }
}