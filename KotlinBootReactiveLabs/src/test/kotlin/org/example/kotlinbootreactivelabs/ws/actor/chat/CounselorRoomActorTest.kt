package org.example.kotlinbootreactivelabs.ws.actor.chat

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class CounselorRoomActorTest {

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
    fun testInvitePersnalRoomActor() {
        val probe = testKit.createTestProbe<CounselorRoomResponse>()
        val counselorRoomActor = testKit.spawn(CounselorRoomActor.create("Room1"))

        val persnalRoomActor = testKit.createTestProbe<PersonalRoomCommand>().ref
        counselorRoomActor.tell(InvitePersonalRoomActor(persnalRoomActor, probe.ref))
        probe.expectMessage(InvitationCompleted)
    }

    @Test
    fun testChangeStatus() {
        val probe = testKit.createTestProbe<CounselorRoomResponse>()
        val counselorRoomActor = testKit.spawn(CounselorRoomActor.create("Room1"))

        counselorRoomActor.tell(ChangeStatus(CounselorRoomStatus.IN_PROGRESS, probe.ref))
        probe.expectMessage(StatusChangeCompleted(CounselorRoomStatus.IN_PROGRESS))

        counselorRoomActor.tell(ChangeStatus(CounselorRoomStatus.COMPLETED, probe.ref))
        probe.expectMessage(StatusChangeCompleted(CounselorRoomStatus.COMPLETED))
    }

    @Test
    fun testSendMessageToPersonalRoom() {
        val probe = testKit.createTestProbe<CounselorRoomResponse>()
        val counselorRoomActor = testKit.spawn(CounselorRoomActor.create("Room1"))

        val personalRoomActor = testKit.createTestProbe<PersonalRoomCommand>().ref
        counselorRoomActor.tell(InvitePersonalRoomActor(personalRoomActor, probe.ref))
        probe.expectMessage(InvitationCompleted)

        counselorRoomActor.tell(SendMessageToPersonalRoom("Hello Personal Room"))
        // Add verification logic if needed
    }
}