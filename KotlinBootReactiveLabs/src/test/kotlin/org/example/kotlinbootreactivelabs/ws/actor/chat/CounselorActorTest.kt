package org.example.kotlinbootreactivelabs.ws.actor.chat

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.reactive.socket.WebSocketSession

class CounselorActorTest {

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
    fun testGoOnline() {
        val probe = testKit.createTestProbe<CounselorResponse>()
        val counselorActor = testKit.spawn(CounselorActor.create("Counselor1"))

        counselorActor.tell(GoOnline(probe.ref))
        probe.expectMessage(StatusChanged(CounselorStatus.ONLINE))
    }

    @Test
    fun testGoOffline() {
        val probe = testKit.createTestProbe<CounselorResponse>()
        val counselorActor = testKit.spawn(CounselorActor.create("Counselor1"))

        counselorActor.tell(GoOffline(AwayStatus.BREAK, probe.ref))
        probe.expectMessage(StatusChanged(CounselorStatus.OFFLINE, AwayStatus.BREAK))
    }

    @Test
    fun testAssignTask() {
        val probe = testKit.createTestProbe<CounselorResponse>()
        val counselorActor = testKit.spawn(CounselorActor.create("Counselor1"))

        counselorActor.tell(AssignTask("Task1", probe.ref))
        probe.expectMessage(TaskAssigned("Task1"))
    }

    @Test
    fun testAssignRoom() {
        val probe = testKit.createTestProbe<CounselorResponse>()
        val counselorActor = testKit.spawn(CounselorActor.create("Counselor1"))
        val customerProbe = testKit.createTestProbe<PersonalRoomCommand>()
        val roomProbe = testKit.createTestProbe<CounselorRoomCommand>()

        counselorActor.tell(SetCounselorTestProbe(probe.ref))
        counselorActor.tell(AssignRoom("Room1", customerProbe.ref, roomProbe.ref))
        probe.expectMessage(TaskAssigned("Room assigned to counselor Counselor1: Room1"))
    }

    @Test
    fun testSetCounselorSocketSession() {
        val probe = testKit.createTestProbe<CounselorResponse>()
        val counselorActor = testKit.spawn(CounselorActor.create("Counselor1"))
        val session = Mockito.mock(WebSocketSession::class.java)

        counselorActor.tell(SetCounselorSocketSession(session))
        // 추가적인 검증 로직이 필요할 수 있습니다.
    }

    @Test
    fun testSendToCounselorHandlerTextMessage() {
        val probe = testKit.createTestProbe<CounselorResponse>()
        val counselorActor = testKit.spawn(CounselorActor.create("Counselor1"))
        val session = Mockito.mock(WebSocketSession::class.java)

        counselorActor.tell(SetCounselorSocketSession(session))
        counselorActor.tell(SendToCounselorHandlerTextMessage("Test Message"))
        // 추가적인 검증 로직이 필요할 수 있습니다.
    }

}