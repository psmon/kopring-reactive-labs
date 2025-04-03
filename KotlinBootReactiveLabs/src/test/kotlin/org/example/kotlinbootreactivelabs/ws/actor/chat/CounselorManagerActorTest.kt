package org.example.kotlinbootreactivelabs.ws.actor.chat

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.ManualTime
import org.example.kotlinbootreactivelabs.ws.actor.chat.router.CounselingGroup
import org.example.kotlinbootreactivelabs.ws.actor.chat.router.CounselingRequestInfo
import org.example.kotlinbootreactivelabs.ws.actor.chat.router.CounselingRouter
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Random

class CounselorManagerActorTest {

    companion object {
        private lateinit var testKit: ActorTestKit
        private lateinit var manualTime: ManualTime

        @BeforeAll
        @JvmStatic
        fun setup() {
            val config = ManualTime.config().withFallback(ConfigFactory.defaultApplication())
            testKit = ActorTestKit.create(config)
            manualTime = ManualTime.get(testKit.system())
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun testCreateCounselor() {
        val probe = testKit.createTestProbe<CounselorManagerResponse>()
        val counselorManager = testKit.spawn(CounselorManagerActor.create())

        counselorManager.tell(CreateCounselor("counselor1", probe.ref))
        val response = probe.receiveMessage()
        assertEquals(CounselorCreated("counselor1"), response)
    }

    @Test
    fun testRequestCounseling() {
        val probe = testKit.createTestProbe<CounselorManagerResponse>()
        val personalRoomProbe = testKit.createTestProbe<PersonalRoomCommand>()
        val counselorManager = testKit.spawn(CounselorManagerActor.create())

        counselorManager.tell(CreateCounselor("counselor1", probe.ref))
        probe.receiveMessage()

        val skillInfo = CounselingRequestInfo(1, 0, 0)
        counselorManager.tell(RequestCounseling("room1", skillInfo, personalRoomProbe.ref, probe.ref))
        val response = probe.receiveMessage()
        assert(response is CounselorRoomFound)
    }

    @Test
    fun testAddObserverCounselor() {
        val probe = testKit.createTestProbe<CounselorManagerResponse>()
        val counselorManager = testKit.spawn(CounselorManagerActor.create())

        // Create a counselor and a room
        counselorManager.tell(CreateCounselor("observer1", probe.ref))
        probe.receiveMessage()
        counselorManager.tell(CreateRoom("room1", probe.ref))
        probe.receiveMessage()

        // Add observer counselor to the room
        counselorManager.tell(AddObserverCounselor("room1", "observer1", probe.ref))
        val response = probe.receiveMessage()
        assertEquals(CounselorManagerSystemResponse("Observer counselor observer1 added to room: room1"), response)
    }

    @Test
    fun testRoundRobinCounselorAssignment() {
        val probe = testKit.createTestProbe<CounselorManagerResponse>()
        val counselorManager = testKit.spawn(CounselorManagerActor.create())

        counselorManager.tell(CreateCounselor("counselor1", probe.ref))
        probe.receiveMessage()
        counselorManager.tell(CreateCounselor("counselor2", probe.ref))
        probe.receiveMessage()
        counselorManager.tell(CreateCounselor("counselor3", probe.ref))
        probe.receiveMessage()

    }

    @Test
    fun testUpdateRoutingRule() {
        val probe = testKit.createTestProbe<CounselorManagerResponse>()
        val counselorManager = testKit.spawn(CounselorManagerActor.create())

        val newRoutingRule = CounselingRouter(
            counselingGroups = listOf(
                CounselingGroup(
                    hashCodes = arrayOf("skill-4-0-0", "skill-4-0-1"),
                    availableCounselors = mutableListOf(),
                    lastAssignmentTime = System.currentTimeMillis(),
                    availableSlots = 5
                )
            )
        )

        counselorManager.tell(UpdateRoutingRule(newRoutingRule, probe.ref))
        val response = probe.receiveMessage()
        assertEquals(CounselorManagerSystemResponse("Routing rule updated successfully."), response)
    }

    @Test
    fun testGetCounselor() {
        val probe = testKit.createTestProbe<CounselorManagerResponse>()
        val counselorManager = testKit.spawn(CounselorManagerActor.create())

        counselorManager.tell(CreateCounselor("counselor1", probe.ref))
        probe.receiveMessage()

        counselorManager.tell(GetCounselor("counselor1", probe.ref))
        val response = probe.receiveMessage()
        assert(response is CounselorFound)
    }

    @Test
    fun testGetCounselorRoom() {
        val probe = testKit.createTestProbe<CounselorManagerResponse>()
        val counselorManager = testKit.spawn(CounselorManagerActor.create())

        counselorManager.tell(CreateRoom("room1", probe.ref))
        val response = probe.receiveMessage()
        assertEquals(CounselorCreated("room1"), response)

        counselorManager.tell(GetCounselorRoom("room1", probe.ref))
        val response2 = probe.receiveMessage()
        assert(response2 is CounselorRoomFound)
    }

    //// Evaluate routing rule
    @Test()
    fun testUpdateRoutingRuleAndRequestCounseling() {
        val probe = testKit.createTestProbe<CounselorManagerResponse>()
        val personalRoomProbe = testKit.createTestProbe<PersonalRoomCommand>()
        val counselorManager = testKit.spawn(CounselorManagerActor.create())

        val numCounselors = 10
        val numRequests = 20

        // Update routing rule
        val newRoutingRule = CounselingRouter(
            counselingGroups = listOf(
                CounselingGroup(
                    hashCodes = arrayOf("skill-1-0-0", "skill-1-0-1", "skill-1-0-2", "skill-1-0-3", "skill-1-0-4"),
                    availableCounselors = mutableListOf(),
                    lastAssignmentTime = System.currentTimeMillis(),
                    availableSlots = 100
                ),
                CounselingGroup(
                    hashCodes = arrayOf("skill-2-0-0", "skill-2-0-1", "skill-2-0-2", "skill-2-0-3", "skill-2-0-4"),
                    availableCounselors = mutableListOf(),
                    lastAssignmentTime = System.currentTimeMillis(),
                    availableSlots = 100
                )
            )
        )

        counselorManager.tell(UpdateRoutingRule(newRoutingRule, probe.ref))
        val updateResponse = probe.receiveMessage()
        assertEquals(CounselorManagerSystemResponse("Routing rule updated successfully."), updateResponse)

        // Create counselors
        for (i in 1..numCounselors) {
            counselorManager.tell(CreateCounselor("counselor$i", probe.ref))
            probe.receiveMessage()
        }

        // Request counseling
        val random = Random()
        val availableSkills = newRoutingRule.counselingGroups.flatMap { it.hashCodes.toList() }.distinct()

        for (i in 1..numRequests) {
            manualTime.timePasses(Duration.ofSeconds(3))
            val randomSkill = availableSkills[random.nextInt(availableSkills.size)]
            val skillParts = randomSkill.split("-").drop(1).map { it.toInt() }
            val skillInfo = CounselingRequestInfo(skillParts[0], skillParts[1], skillParts[2])
            counselorManager.tell(RequestCounseling("room$i", skillInfo, personalRoomProbe.ref, probe.ref))
            val response = probe.receiveMessage()
            assert(response is CounselorRoomFound)
        }

        // Verify distribution
        counselorManager.tell(EvaluateRoutingRule(probe.ref))
        val response2 = probe.receiveMessage() as CounselorManagerSystemResponse

        println(response2.message)
    }

}