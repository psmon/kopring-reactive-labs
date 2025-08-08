package com.example.pekkohttp.actor

import com.example.pekkohttp.model.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.longs.shouldBeGreaterThan
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventStreamActorTest {
    
    private lateinit var testKit: ActorTestKit
    
    @BeforeAll
    fun setup() {
        testKit = ActorTestKit.create()
    }
    
    @AfterAll
    fun teardown() {
        testKit.shutdownTestKit()
    }
    
    @Test
    fun `should process single event`() = runTest {
        val actor = testKit.spawn(EventStreamActor.create())
        
        val event = UserEvent(
            userId = "user123",
            eventType = "click",
            action = "button_click",
            metadata = mapOf("button" to "submit")
        )
        
        actor.tell(ProcessEvent(event))
        
        // Small delay to allow actor to process the message
        //delay(100)
        
        val probe = testKit.createTestProbe<EventStats>()
        actor.tell(GetEventStats(probe.ref))
        
        val stats = probe.expectMessageClass(EventStats::class.java)
        stats.totalEvents shouldBe 1
        stats.eventsPerType["click"] shouldBe 1
        stats.eventsPerUser["user123"] shouldBe 1
        stats.lastEventTime shouldNotBe null
    }
    
    @Test
    fun `should process multiple events from different users`() = runTest {
        val actor = testKit.spawn(EventStreamActor.create())
        
        val events = listOf(
            UserEvent("user1", "click", "button_click"),
            UserEvent("user2", "view", "page_view"),
            UserEvent("user1", "click", "link_click"),
            UserEvent("user3", "submit", "form_submit")
        )
        
        events.forEach { event ->
            actor.tell(ProcessEvent(event))
        }
        
        // Small delay to allow actor to process the messages
        //delay(200)
        
        val probe = testKit.createTestProbe<EventStats>()
        actor.tell(GetEventStats(probe.ref))
        
        val stats = probe.expectMessageClass(EventStats::class.java)
        stats.totalEvents shouldBe 4
        stats.eventsPerType["click"] shouldBe 2
        stats.eventsPerType["view"] shouldBe 1
        stats.eventsPerType["submit"] shouldBe 1
        stats.eventsPerUser["user1"] shouldBe 2
        stats.eventsPerUser["user2"] shouldBe 1
        stats.eventsPerUser["user3"] shouldBe 1
    }
    
    @Test
    fun `should handle stream completion`() = runTest {
        val actor = testKit.spawn(EventStreamActor.create())
        
        actor.tell(StreamComplete(5))
        
        // Should not throw any exceptions
        delay(100)
    }
    
    @Test
    fun `should track event timestamps`() = runTest {
        val actor = testKit.spawn(EventStreamActor.create())
        
        val beforeTime = Instant.now()
        
        val event = UserEvent(
            userId = "user456",
            eventType = "test",
            action = "test_action"
        )
        
        actor.tell(ProcessEvent(event))
        
        //delay(100)
        
        val probe = testKit.createTestProbe<EventStats>()
        actor.tell(GetEventStats(probe.ref))
        
        val stats = probe.expectMessageClass(EventStats::class.java)
        stats.lastEventTime shouldNotBe null
        stats.lastEventTime!!.isAfter(beforeTime.minusSeconds(1)) shouldBe true
        stats.lastEventTime!!.isBefore(Instant.now().plusSeconds(1)) shouldBe true
    }
    
    @Test
    fun `should accumulate statistics correctly`() = runTest {
        val actor = testKit.spawn(EventStreamActor.create())
        
        // Send events of same type from same user
        repeat(5) {
            actor.tell(ProcessEvent(
                UserEvent("userA", "typeX", "actionY")
            ))
            //delay(50) // Small delay between events
        }
        
        //delay(300)
        
        val probe = testKit.createTestProbe<EventStats>()
        actor.tell(GetEventStats(probe.ref))
        
        val stats = probe.expectMessageClass(EventStats::class.java)
        stats.totalEvents shouldBe 5
        stats.eventsPerType["typeX"] shouldBe 5
        stats.eventsPerUser["userA"] shouldBe 5
    }
}