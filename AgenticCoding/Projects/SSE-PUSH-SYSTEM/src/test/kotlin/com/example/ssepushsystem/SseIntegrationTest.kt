package com.example.ssepushsystem

import com.example.ssepushsystem.controller.PushController
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Flux
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class SseIntegrationTest {
    
    @Autowired
    private lateinit var webTestClient: WebTestClient
    
    @Test
    fun `test API - SSE streaming and event publishing`() {
        // Create a test event
        val testEvent = PushController.PushEventRequest(
            topic = "test-topic",
            data = "Test message from API"
        )
        
        // Publish event via API
        webTestClient.post()
            .uri("/api/push/event")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(testEvent))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.status").isEqualTo("success")
            .jsonPath("$.event.topic").isEqualTo("test-topic")
            .jsonPath("$.event.data").isEqualTo("Test message from API")
        
        // Subscribe to SSE stream
        val sseFlux: Flux<ServerSentEvent<String>> = webTestClient.get()
            .uri("/api/sse/stream?userId=testUser&topics=test-topic")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .returnResult<ServerSentEvent<String>>()
            .responseBody
        
        // Collect first event
        val firstEvent = sseFlux.blockFirst(Duration.ofSeconds(5))
        firstEvent shouldNotBe null
    }
}