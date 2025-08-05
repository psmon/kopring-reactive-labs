package com.example.actorthrottle.actor

import com.example.actorthrottle.model.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual as longShouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual as longShouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual as intShouldBeGreaterThanOrEqual
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class ThrottleActorTest : StringSpec({
    
    val testKit = ActorTestKit.create()
    
    afterSpec {
        testKit.shutdownTestKit()
    }
    
    "ThrottleActor should enforce TPS=1 limit" {
        val probe = testKit.createTestProbe<WorkResult>()
        val statsProbe = testKit.createTestProbe<ThrottleStats>()
        val throttleActor = testKit.spawn(ThrottleActor.create("mall1", tpsLimit = 1))
        
        val workCount = 5
        val startTime = System.currentTimeMillis()
        
        repeat(workCount) { i ->
            throttleActor.tell(ProcessWork("mall1", "work-$i", probe.ref))
        }
        
        val results = mutableListOf<WorkResult>()
        repeat(workCount) {
            results.add(probe.receiveMessage())
        }
        
        val endTime = System.currentTimeMillis()
        val elapsedSeconds = (endTime - startTime) / 1000.0
        
        elapsedSeconds shouldBeGreaterThanOrEqual  4.0
        
        throttleActor.tell(GetStats(statsProbe.ref))
        val stats = statsProbe.receiveMessage()
        
        stats.mallId shouldBe "mall1"
        stats.processedCount shouldBe workCount.toLong()
        stats.tps shouldBeLessThanOrEqual 1.5
    }
    
    "ThrottleActor should handle multiple requests concurrently with throttling" {
        val probe = testKit.createTestProbe<WorkResult>()
        val throttleActor = testKit.spawn(ThrottleActor.create("mall2", tpsLimit = 2))
        
        val workCount = 10
        
        val elapsed = measureTime {
            repeat(workCount) { i ->
                throttleActor.tell(ProcessWork("mall2", "work-$i", probe.ref))
            }
            
            repeat(workCount) {
                probe.receiveMessage()
            }
        }
        
        elapsed.inWholeSeconds longShouldBeGreaterThanOrEqual 4L
        elapsed.inWholeSeconds longShouldBeLessThanOrEqual 6L
    }
    
    "ThrottleActor should track queued work correctly" {
        val probe = testKit.createTestProbe<WorkResult>()
        val statsProbe = testKit.createTestProbe<ThrottleStats>()
        val throttleActor = testKit.spawn(ThrottleActor.create("mall3", tpsLimit = 1))
        
        repeat(10) { i ->
            throttleActor.tell(ProcessWork("mall3", "work-$i", probe.ref))
        }
        
        Thread.sleep(500)
        
        throttleActor.tell(GetStats(statsProbe.ref))
        val stats = statsProbe.receiveMessage()
        
        stats.queuedCount intShouldBeGreaterThanOrEqual 5
        
        repeat(10) {
            probe.receiveMessage()
        }
    }
    
    "ThrottleActor should process work in FIFO order" {
        val probe = testKit.createTestProbe<WorkResult>()
        val throttleActor = testKit.spawn(ThrottleActor.create("mall4", tpsLimit = 1))
        
        val workIds = (1..5).map { "work-$it" }
        
        workIds.forEach { workId ->
            throttleActor.tell(ProcessWork("mall4", workId, probe.ref))
        }
        
        val results = mutableListOf<WorkResult>()
        repeat(5) {
            results.add(probe.receiveMessage())
        }
        
        results.map { it.workId } shouldBe workIds
    }
})