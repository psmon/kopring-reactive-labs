package com.example.actorthrottle.actor

import com.example.actorthrottle.model.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan as longShouldBeGreaterThan
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import kotlin.time.Duration.Companion.seconds

class ThrottleManagerActorTest : StringSpec({
    
    val testKit = ActorTestKit.create()
    
    afterSpec {
        testKit.shutdownTestKit()
    }
    
    "ThrottleManagerActor should create separate actors for different malls" {
        val managerActor = testKit.spawn(ThrottleManagerActor.create())
        val mall1Probe = testKit.createTestProbe<WorkResult>()
        val mall2Probe = testKit.createTestProbe<WorkResult>()

        managerActor.tell(ProcessMallWork("mall1", "work1", mall1Probe.ref))
        managerActor.tell(ProcessMallWork("mall2", "work2", mall2Probe.ref))

        val mall1Result = mall1Probe.receiveMessage()
        val mall2Result = mall2Probe.receiveMessage()

        mall1Result.mallId shouldBe "mall1"
        mall1Result.workId shouldBe "work1"
        mall2Result.mallId shouldBe "mall2"
        mall2Result.workId shouldBe "work2"
    }
    
    "ThrottleManagerActor should enforce TPS limit per mall independently" {
        val managerActor = testKit.spawn(ThrottleManagerActor.create())
        val resultProbe = testKit.createTestProbe<WorkResult>()
        
        val startTime = System.currentTimeMillis()
        
        repeat(3) { i ->
            managerActor.tell(ProcessMallWork("mallA", "workA-$i", resultProbe.ref))
        }
        
        repeat(3) { i ->
            managerActor.tell(ProcessMallWork("mallB", "workB-$i", resultProbe.ref))
        }
        
        val results = mutableListOf<WorkResult>()
        repeat(6) {
            results.add(resultProbe.receiveMessage())
        }
        
        val endTime = System.currentTimeMillis()
        val elapsedSeconds = (endTime - startTime) / 1000.0
        
        elapsedSeconds shouldBeGreaterThan 2.0
        
        val mallAResults = results.filter { it.mallId == "mallA" }
        val mallBResults = results.filter { it.mallId == "mallB" }
        
        mallAResults.size shouldBe 3
        mallBResults.size shouldBe 3
    }
    
    "ThrottleManagerActor should return stats for specific mall" {
        val managerActor = testKit.spawn(ThrottleManagerActor.create())
        val resultProbe = testKit.createTestProbe<WorkResult>()
        val statsProbe = testKit.createTestProbe<ThrottleStats>()
        
        repeat(5) { i ->
            managerActor.tell(ProcessMallWork("mall1", "work-$i", resultProbe.ref))
        }
        
        Thread.sleep(1000)
        
        managerActor.tell(GetMallStats("mall1", statsProbe.ref))
        val stats = statsProbe.receiveMessage()
        
        stats.mallId shouldBe "mall1"
        stats.processedCount longShouldBeGreaterThan 0
    }
    
    "ThrottleManagerActor should return empty stats for non-existent mall" {
        val managerActor = testKit.spawn(ThrottleManagerActor.create())
        val statsProbe = testKit.createTestProbe<ThrottleStats>()
        
        managerActor.tell(GetMallStats("non-existent", statsProbe.ref))
        val stats = statsProbe.receiveMessage()
        
        stats.mallId shouldBe "non-existent"
        stats.processedCount shouldBe 0
        stats.queuedCount shouldBe 0
        stats.tps shouldBe 0.0
    }
    
    "ThrottleManagerActor should return stats for all malls" {
        val managerActor = testKit.spawn(ThrottleManagerActor.create())
        val resultProbe = testKit.createTestProbe<WorkResult>()
        val allStatsProbe = testKit.createTestProbe<Map<String, ThrottleStats>>()
        
        val malls = listOf("mall1", "mall2", "mall3")
        
        malls.forEach { mallId ->
            repeat(3) { i ->
                managerActor.tell(ProcessMallWork(mallId, "$mallId-work-$i", resultProbe.ref))
            }
        }
        
        Thread.sleep(1000)
        
        managerActor.tell(GetAllStats(allStatsProbe.ref))
        val allStats = allStatsProbe.receiveMessage(java.time.Duration.ofSeconds(5))
        
        allStats shouldHaveSize 3
        malls.forEach { mallId ->
            allStats shouldContainKey mallId
            allStats[mallId]?.processedCount?.let { it longShouldBeGreaterThan 0 }
        }
    }
    
    "ThrottleManagerActor should handle empty state for GetAllStats" {
        val managerActor = testKit.spawn(ThrottleManagerActor.create())
        val allStatsProbe = testKit.createTestProbe<Map<String, ThrottleStats>>()
        
        managerActor.tell(GetAllStats(allStatsProbe.ref))
        val allStats = allStatsProbe.receiveMessage()
        
        allStats shouldHaveSize 0
    }
})