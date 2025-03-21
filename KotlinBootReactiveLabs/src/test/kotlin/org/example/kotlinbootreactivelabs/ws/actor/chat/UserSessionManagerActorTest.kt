package org.example.kotlinbootreactivelabs.ws.actor.chat

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import labs.common.model.EventTextMessage
import labs.common.model.MessageFrom
import labs.common.model.MessageType
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono


class UserSessionManagerActorTest {

    companion object {
        private lateinit var testKit: ActorTestKit

        @BeforeAll
        @JvmStatic
        fun setup() {
            testKit = ActorTestKit.create()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun testAddAndRemoveSession() {
        val session = Mockito.mock(WebSocketSession::class.java)
        Mockito.`when`(session.id).thenReturn("session1")

        val actor = testKit.spawn(UserSessionManagerActor.create())

        actor.tell(AddSession(session))
        // Verify session added (you can add more detailed checks if needed)

        actor.tell(RemoveSession(session))
        // Verify session removed (you can add more detailed checks if needed)
    }

    @Test
    fun testSubscribeAndUnsubscribeToTopic() {
        val session = Mockito.mock(WebSocketSession::class.java)
        Mockito.`when`(session.id).thenReturn("session1")

        val actor = testKit.spawn(UserSessionManagerActor.create())

        actor.tell(AddSession(session))
        actor.tell(SubscribeToTopic("session1", "topic1"))
        // Verify subscription (you can add more detailed checks if needed)

        actor.tell(UnsubscribeFromTopic("session1", "topic1"))
        // Verify unsubscription (you can add more detailed checks if needed)
    }

    @Test
    fun testSendMessageToSession() {
        val probe = testKit.createTestProbe<UserSessionResponse>()
        val session = Mockito.mock(WebSocketSession::class.java)
        val textMessage = Mockito.mock(WebSocketMessage::class.java)

        Mockito.`when`(session.id).thenReturn("session1")
        Mockito.`when`(session.textMessage(Mockito.anyString())).thenReturn(textMessage)
        Mockito.`when`(session.send(Mockito.any())).thenReturn(Mono.empty())

        val actor = testKit.spawn(UserSessionManagerActor.create())

        actor.tell(AddSession(session))
        actor.tell(SendMessageToSession("session1", "Hello"))

        // Test for DelayedMessage
        actor.tell(Ping(probe.ref()))
        probe.expectMessageClass(Pong::class.java)

        // Create an actual instance of EventTextMessage
        val expectedMessage = EventTextMessage(
            type = MessageType.SESSIONID,
            message = "Connected",
            from = MessageFrom.SYSTEM,
            id = "session1",
            jsondata = null
        )

        // Verify that textMessage was called with the correct JSON value
        val objectMapper = jacksonObjectMapper()
        val expectedJson = objectMapper.writeValueAsString(expectedMessage)
        Mockito.verify(session).textMessage(Mockito.eq(expectedJson))
    }

    @Test
    fun testSendMessageToTopic() {
        val probe = testKit.createTestProbe<UserSessionResponse>()

        val session1 = Mockito.mock(WebSocketSession::class.java)
        val session2 = Mockito.mock(WebSocketSession::class.java)
        val textMessage = Mockito.mock(WebSocketMessage::class.java)

        Mockito.`when`(session1.id).thenReturn("session1")
        Mockito.`when`(session2.id).thenReturn("session2")

        Mockito.`when`(session1.textMessage(Mockito.anyString())).thenReturn(textMessage)
        Mockito.`when`(session2.textMessage(Mockito.anyString())).thenReturn(textMessage)

        Mockito.`when`(session1.send(Mockito.any())).thenReturn(Mono.empty())
        Mockito.`when`(session2.send(Mockito.any())).thenReturn(Mono.empty())

        val actor = testKit.spawn(UserSessionManagerActor.create())

        actor.tell(AddSession(session1))
        actor.tell(AddSession(session2))
        actor.tell(SubscribeToTopic("session1", "topic1"))
        actor.tell(SubscribeToTopic("session2", "topic1"))
        actor.tell(SendMessageToTopic("topic1", "Hello Topic"))

        actor.tell(Ping(probe.ref()))
        probe.expectMessageClass(Pong::class.java)

        // Create an actual instance of EventTextMessage
        val expectedMessage = EventTextMessage(
            type = MessageType.PUSH,
            message = "Hello Topic",
            from = MessageFrom.SYSTEM,
            id = null,
            jsondata = null
        )

        // Verify that textMessage was called with the correct JSON value
        val objectMapper = jacksonObjectMapper()
        val expectedJson = objectMapper.writeValueAsString(expectedMessage)
        Mockito.verify(session2).textMessage(Mockito.eq(expectedJson))

    }

    @Test
    fun testGetSessions() {
        val session1 = Mockito.mock(WebSocketSession::class.java)
        val session2 = Mockito.mock(WebSocketSession::class.java)
        val textMessage = Mockito.mock(WebSocketMessage::class.java)

        Mockito.`when`(session1.id).thenReturn("session1")
        Mockito.`when`(session2.id).thenReturn("session2")

        Mockito.`when`(session1.textMessage(Mockito.anyString())).thenReturn(textMessage)
        Mockito.`when`(session2.textMessage(Mockito.anyString())).thenReturn(textMessage)

        Mockito.`when`(session1.send(Mockito.any())).thenReturn(Mono.empty())
        Mockito.`when`(session2.send(Mockito.any())).thenReturn(Mono.empty())

        val actor = testKit.spawn(UserSessionManagerActor.create())
        val probe = testKit.createTestProbe<UserSessionResponse>()

        actor.tell(AddSession(session1))
        actor.tell(AddSession(session2))
        actor.tell(GetSessions(probe.ref()))

        val response = probe.expectMessageClass(SessionsResponse::class.java)
        assertEquals(2, response.sessions.size)
        assertTrue(response.sessions.containsKey("session1"))
        assertTrue(response.sessions.containsKey("session2"))
    }

    @Test
    fun testPingPong() {
        val actor = testKit.spawn(UserSessionManagerActor.create())
        val probe = testKit.createTestProbe<UserSessionResponse>()

        actor.tell(Ping(probe.ref()))

        val response = probe.expectMessageClass(Pong::class.java)
        assertEquals("Pong", response.message)
    }
}