package org.example.kotlinbootreactivelabs.ws.actor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.junit.jupiter.api.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.text.contains

class SocketActorHandlerTest {
    companion object {
        private lateinit var client: OkHttpClient
        private lateinit var request: Request
        private val receivedMessages = mutableListOf<String>()

        @BeforeAll
        @JvmStatic
        fun setup() {
            client = OkHttpClient()
            request = Request.Builder().url("ws://localhost:8080/ws-actor").build()
        }
    }

    fun assertContainsText(text: String) {
        val objectMapper = jacksonObjectMapper()
        val messages = receivedMessages.map { objectMapper.readValue<Map<String, Any>>(it)["message"] as String }
        if (messages.any { it.contains(text) }) {
            return
        }
        throw AssertionError("The list does not contain the text '$text'")
    }

    @Test
    fun testWebSocketConnection() {
        val latch = CountDownLatch(1) // 1초동안 수신대기목적
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("hello")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                println("Received message: $text")
                receivedMessages.add(text)
                latch.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
                latch.countDown()
            }
        }

        client.newWebSocket(request, listener)
        latch.await(10, TimeUnit.SECONDS)

        assertContainsText("You are connected")
    }

    @Test
    fun testSubscribeToTopic() {
        val latch = CountDownLatch(1)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("subscribe:test-topic")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                println("Received message: $text")
                receivedMessages.add(text)
                latch.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
                latch.countDown()
            }
        }

        client.newWebSocket(request, listener)
        latch.await(10, TimeUnit.SECONDS)

        assertContainsText("You are subscribed to topic test-topic")
    }

    @Test
    fun testSendMessageToSubscribedUsers() {
        val latch = CountDownLatch(1)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("subscribe:test-topic")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                println("Received message: $text")
                receivedMessages.add(text)
                latch.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
                latch.countDown()
            }
        }

        client.newWebSocket(request, listener)

        // Wait for subscription to complete
        Thread.sleep(1000)

        // Send message to topic using REST endpoint
        val url = "http://localhost:8080/api/pubsub/publish-to-topic?topic=test-topic"
        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), "Hello Subscribers")
        val restRequest = Request.Builder().url(url).post(requestBody).build()
        client.newCall(restRequest).execute()

        latch.await(10, TimeUnit.SECONDS)

        // Step1: Subscribe to topic
        assertContainsText("You are subscribed to topic test-topic")

        // Step2: Receive message from topic by REST
        assertContainsText("Hello Subscribers")
    }

    @Test
    fun testUnsubscribeFromTopic() {
        val latch = CountDownLatch(1)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("subscribe:test-topic")
                webSocket.send("unsubscribe:test-topic")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                println("Received message: $text")
                receivedMessages.add(text)
                latch.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
                latch.countDown()
            }
        }

        client.newWebSocket(request, listener)
        latch.await(10, TimeUnit.SECONDS)

        assertContainsText("You are unsubscribed to topic test-topic")
    }
}