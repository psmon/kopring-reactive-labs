package org.example.kotlinbootreactivelabs.ws.actor

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.example.kotlinbootreactivelabs.ws.actor.basic.SimpleSessionManagerActor
import org.example.kotlinbootreactivelabs.ws.actor.basic.SimpleSessionCommand
import org.example.kotlinbootreactivelabs.ws.actor.basic.SimpleUserSessionCommandResponse
import org.example.kotlinbootreactivelabs.ws.actor.basic.SimpleUserSessionCommandResponse.SimpleInformation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono

class SessionManagerActorTest {

    companion object {
        private val testKit = ActorTestKit.create()

        @BeforeAll
        @JvmStatic
        fun setup() {
            // Setup code if needed
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun testAddSession() {
        val sessionManagerActor: ActorRef<SimpleSessionCommand> = testKit.spawn(SimpleSessionManagerActor.Companion.create(), "session-manager-actor")
        val probe: TestProbe<SimpleUserSessionCommandResponse> = testKit.createTestProbe()

        val session = Mockito.mock(WebSocketSession::class.java)
        Mockito.`when`(session.id).thenReturn("session1")
        val message = Mockito.mock(WebSocketMessage::class.java)
        Mockito.`when`(session.textMessage(Mockito.anyString())).thenReturn(message)
        Mockito.`when`(session.send(Mockito.any())).thenReturn(Mono.empty())

        sessionManagerActor.tell(SimpleSessionCommand.SimpleAddSession(session, probe.ref))

        probe.expectMessage(SimpleInformation("Session added session1"))

    }

    @Test
    fun testRemoveSession() {
        val sessionManagerActor: ActorRef<SimpleSessionCommand> = testKit.spawn(SimpleSessionManagerActor.Companion.create(), "session-manager-actor")
        val probe: TestProbe<SimpleUserSessionCommandResponse> = testKit.createTestProbe()

        val session = Mockito.mock(WebSocketSession::class.java)
        Mockito.`when`(session.id).thenReturn("session1")
        val message = Mockito.mock(WebSocketMessage::class.java)
        Mockito.`when`(session.textMessage(Mockito.anyString())).thenReturn(message)
        Mockito.`when`(session.send(Mockito.any())).thenReturn(Mono.empty())

        sessionManagerActor.tell(SimpleSessionCommand.SimpleAddSession(session, probe.ref))
        sessionManagerActor.tell(SimpleSessionCommand.SimpleRemoveSession(session, probe.ref))

        probe.expectMessage(SimpleInformation("Session added session1"))
        probe.expectMessage(SimpleInformation("Session removed session1"))
    }

    @Test
    fun testSubscribeToTopic() {
        val simpleSessionManagerActor: ActorRef<SimpleSessionCommand> = testKit.spawn(SimpleSessionManagerActor.Companion.create(), "session-manager-actor")
        val probe: TestProbe<SimpleUserSessionCommandResponse> = testKit.createTestProbe()

        val session = Mockito.mock(WebSocketSession::class.java)
        Mockito.`when`(session.id).thenReturn("session1")
        val message = Mockito.mock(WebSocketMessage::class.java)
        Mockito.`when`(session.textMessage(Mockito.anyString())).thenReturn(message)
        Mockito.`when`(session.send(Mockito.any())).thenReturn(Mono.empty())

        simpleSessionManagerActor.tell(SimpleSessionCommand.SimpleAddSession(session, probe.ref))
        simpleSessionManagerActor.tell(SimpleSessionCommand.SimpleSubscribeToTopic("session1", "topic1", probe.ref))

        probe.expectMessage(SimpleInformation("Session added session1"))
        probe.expectMessage(SimpleInformation("Subscribed to topic topic1"))

    }

    @Test
    fun testUnsubscribeFromTopic() {
        val simpleSessionManagerActor: ActorRef<SimpleSessionCommand> = testKit.spawn(SimpleSessionManagerActor.Companion.create(), "session-manager-actor")
        val probe: TestProbe<SimpleUserSessionCommandResponse> = testKit.createTestProbe()

        val session = Mockito.mock(WebSocketSession::class.java)
        Mockito.`when`(session.id).thenReturn("session1")
        val message = Mockito.mock(WebSocketMessage::class.java)
        Mockito.`when`(session.textMessage(Mockito.anyString())).thenReturn(message)
        Mockito.`when`(session.send(Mockito.any())).thenReturn(Mono.empty())

        simpleSessionManagerActor.tell(SimpleSessionCommand.SimpleAddSession(session, probe.ref))
        simpleSessionManagerActor.tell(SimpleSessionCommand.SimpleSubscribeToTopic("session1", "topic1", probe.ref))
        simpleSessionManagerActor.tell(SimpleSessionCommand.SimpleUnsubscribeFromTopic("session1", "topic1", probe.ref))

        probe.expectMessage(SimpleInformation("Session added session1"))
        probe.expectMessage(SimpleInformation("Subscribed to topic topic1"))
        probe.expectMessage(SimpleInformation("Unsubscribed from topic topic1"))
    }
}