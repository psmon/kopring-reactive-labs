package org.example.kotlinbootreactivelabs.ws.actor

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.example.kotlinbootreactivelabs.ws.actor.UserSessionCommandResponse.Information
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
        val sessionManagerActor: ActorRef<UserSessionCommand> = testKit.spawn(SessionManagerActor.create(), "session-manager-actor")
        val probe: TestProbe<UserSessionCommandResponse> = testKit.createTestProbe()

        val session = Mockito.mock(WebSocketSession::class.java)
        Mockito.`when`(session.id).thenReturn("session1")
        val message = Mockito.mock(WebSocketMessage::class.java)
        Mockito.`when`(session.textMessage(Mockito.anyString())).thenReturn(message)
        Mockito.`when`(session.send(Mockito.any())).thenReturn(Mono.empty())

        sessionManagerActor.tell(UserSessionCommand.AddSession(session, probe.ref))

        probe.expectMessage(Information("Session added session1"))

    }

    @Test
    fun testRemoveSession() {
        val sessionManagerActor: ActorRef<UserSessionCommand> = testKit.spawn(SessionManagerActor.create(), "session-manager-actor")
        val probe: TestProbe<UserSessionCommandResponse> = testKit.createTestProbe()

        val session = Mockito.mock(WebSocketSession::class.java)
        Mockito.`when`(session.id).thenReturn("session1")
        val message = Mockito.mock(WebSocketMessage::class.java)
        Mockito.`when`(session.textMessage(Mockito.anyString())).thenReturn(message)
        Mockito.`when`(session.send(Mockito.any())).thenReturn(Mono.empty())

        sessionManagerActor.tell(UserSessionCommand.AddSession(session, probe.ref))
        sessionManagerActor.tell(UserSessionCommand.RemoveSession(session, probe.ref))

        probe.expectMessage(Information("Session added session1"))
        probe.expectMessage(Information("Session removed session1"))
    }

    @Test
    fun testSubscribeToTopic() {
        val sessionManagerActor: ActorRef<UserSessionCommand> = testKit.spawn(SessionManagerActor.create(), "session-manager-actor")
        val probe: TestProbe<UserSessionCommandResponse> = testKit.createTestProbe()

        val session = Mockito.mock(WebSocketSession::class.java)
        Mockito.`when`(session.id).thenReturn("session1")
        val message = Mockito.mock(WebSocketMessage::class.java)
        Mockito.`when`(session.textMessage(Mockito.anyString())).thenReturn(message)
        Mockito.`when`(session.send(Mockito.any())).thenReturn(Mono.empty())

        sessionManagerActor.tell(UserSessionCommand.AddSession(session, probe.ref))
        sessionManagerActor.tell(UserSessionCommand.SubscribeToTopic("session1", "topic1", probe.ref))

        probe.expectMessage(Information("Session added session1"))
        probe.expectMessage(Information("Subscribed to topic topic1"))

    }

    @Test
    fun testUnsubscribeFromTopic() {
        val sessionManagerActor: ActorRef<UserSessionCommand> = testKit.spawn(SessionManagerActor.create(), "session-manager-actor")
        val probe: TestProbe<UserSessionCommandResponse> = testKit.createTestProbe()

        val session = Mockito.mock(WebSocketSession::class.java)
        Mockito.`when`(session.id).thenReturn("session1")
        val message = Mockito.mock(WebSocketMessage::class.java)
        Mockito.`when`(session.textMessage(Mockito.anyString())).thenReturn(message)
        Mockito.`when`(session.send(Mockito.any())).thenReturn(Mono.empty())

        sessionManagerActor.tell(UserSessionCommand.AddSession(session, probe.ref))
        sessionManagerActor.tell(UserSessionCommand.SubscribeToTopic("session1", "topic1", probe.ref))
        sessionManagerActor.tell(UserSessionCommand.UnsubscribeFromTopic("session1", "topic1", probe.ref))

        probe.expectMessage(Information("Session added session1"))
        probe.expectMessage(Information("Subscribed to topic topic1"))
        probe.expectMessage(Information("Unsubscribed from topic topic1"))
    }
}