package org.example.kotlinbootreactivelabs.ws.actor.chat

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SupervisorChannelActorTest {

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
    fun testCreateCounselorManager() {
        val probe = testKit.createTestProbe<SupervisorChannelResponse>()
        val brandSupervisorActor = testKit.spawn(SupervisorChannelActor.create())

        brandSupervisorActor.tell(CreateCounselorManager("channel1", probe.ref))
        probe.expectMessage(CounselorManagerCreated("channel1"))
    }

    @Test
    fun testGetCounselorManager() {
        val probe = testKit.createTestProbe<SupervisorChannelResponse>()
        val supervisorChannelActor = testKit.spawn(SupervisorChannelActor.create())

        supervisorChannelActor.tell(CreateCounselorManager("channel1", probe.ref))
        probe.expectMessage(CounselorManagerCreated("channel1"))

        supervisorChannelActor.tell(GetCounselorManager("channel1", probe.ref))
        probe.expectMessageClass(CounselorManagerFound::class.java)

    }

    @Test
    fun testCreateCounselorManagerAlreadyExists() {
        val probe = testKit.createTestProbe<SupervisorChannelResponse>()
        val supervisorChannelActor = testKit.spawn(SupervisorChannelActor.create())

        supervisorChannelActor.tell(CreateCounselorManager("channel1", probe.ref))
        probe.expectMessage(CounselorManagerCreated("channel1"))

        supervisorChannelActor.tell(CreateCounselorManager("channel1", probe.ref))
        probe.expectMessage(SupervisorErrorStringResponse("CounselorManager for channel channel1 already exists."))
    }

    @Test
    fun testGetCounselorManagerNotFound() {
        val probe = testKit.createTestProbe<SupervisorChannelResponse>()
        val supervisorChannelActor = testKit.spawn(SupervisorChannelActor.create())

        supervisorChannelActor.tell(GetCounselorManager("NonExistentBrand", probe.ref))
        probe.expectMessage(SupervisorErrorStringResponse("CounselorManager for channel NonExistentBrand not found."))
    }

    @Test
    fun testRemoveCounselorManager() {
        val probe = testKit.createTestProbe<SupervisorChannelResponse>()
        val supervisorChannelActor = testKit.spawn(SupervisorChannelActor.create())

        supervisorChannelActor.tell(CreateCounselorManager("channel1", probe.ref))
        probe.expectMessage(CounselorManagerCreated("channel1"))

        supervisorChannelActor.tell(RemoveCounselorManager("channel1", probe.ref))
        probe.expectMessage(CounselorManagerRemoved("channel1"))
    }

}