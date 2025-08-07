package com.example.connectorkafka.actor

import com.example.connectorkafka.model.*
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventConsumerActorTest {
    
    private lateinit var testKit: ActorTestKit
    
    @BeforeEach
    fun setup() {
        val config = ConfigFactory.parseString("""
            pekko {
                actor {
                    provider = local
                    allow-java-serialization = on
                }
                loglevel = "DEBUG"
            }
        """)
        
        testKit = ActorTestKit.create("ConsumerActorTestSystem", config)
    }
    
    @AfterEach
    fun teardown() {
        testKit.shutdownTestKit()
    }
    
    @Test
    fun `should process event and update state`() {
        val actor = testKit.spawn(EventConsumerActor.create())
        val probe = testKit.createTestProbe<EventResponse>()
        
        val event = KafkaEvent(
            eventType = "TEST",
            eventId = UUID.randomUUID().toString(),
            eventString = "Test message"
        )
        
        actor.tell(ProcessEvent(event))
        
        actor.tell(GetLastEvent(probe.ref))
        val response = probe.expectMessageClass(LastEventResponse::class.java)
        
        assertNotNull(response.event)
        assertEquals(event.eventId, response.event?.eventId)
        assertEquals(event.eventString, response.event?.eventString)
    }
    
    @Test
    fun `should count processed events correctly`() {
        val actor = testKit.spawn(EventConsumerActor.create())
        val probe = testKit.createTestProbe<EventResponse>()
        
        val events = (1..5).map { i ->
            KafkaEvent(
                eventType = "COUNT_TEST",
                eventId = "event-$i",
                eventString = "Message $i"
            )
        }
        
        events.forEach { event ->
            actor.tell(ProcessEvent(event))
        }
        
        actor.tell(GetEventCount(probe.ref))
        val response = probe.expectMessageClass(EventCountResponse::class.java) as EventCountResponse
        
        assertEquals(5, response.count)
    }
    
    @Test
    fun `should return null when no events processed`() {
        val actor = testKit.spawn(EventConsumerActor.create())
        val probe = testKit.createTestProbe<EventResponse>()
        
        actor.tell(GetLastEvent(probe.ref))
        val response = probe.expectMessageClass(LastEventResponse::class.java)
        
        assertNull(response.event)
    }
    
    @Test
    fun `should clear state when requested`() {
        val actor = testKit.spawn(EventConsumerActor.create())
        val eventProbe = testKit.createTestProbe<EventResponse>()
        val countProbe = testKit.createTestProbe<EventResponse>()
        
        val event = KafkaEvent(
            eventType = "CLEAR_TEST",
            eventId = UUID.randomUUID().toString(),
            eventString = "Clear test"
        )
        
        actor.tell(ProcessEvent(event))
        actor.tell(ClearEvents)
        
        actor.tell(GetLastEvent(eventProbe.ref))
        val eventResponse = eventProbe.expectMessageClass(LastEventResponse::class.java)
        assertNull(eventResponse.event)
        
        actor.tell(GetEventCount(countProbe.ref))
        val countResponse = countProbe.expectMessageClass(EventCountResponse::class.java) as EventCountResponse
        assertEquals(0, countResponse.count)
    }
    
    @Test
    fun `should handle multiple events and maintain last event`() {
        val actor = testKit.spawn(EventConsumerActor.create())
        val probe = testKit.createTestProbe<EventResponse>()
        
        val events = (1..10).map { i ->
            KafkaEvent(
                eventType = "SEQUENCE_TEST",
                eventId = "seq-$i",
                eventString = "Sequence $i"
            )
        }
        
        events.forEach { event ->
            actor.tell(ProcessEvent(event))
        }
        
        actor.tell(GetLastEvent(probe.ref))
        val response = probe.expectMessageClass(LastEventResponse::class.java, Duration.ofSeconds(5)) as LastEventResponse
        
        assertNotNull(response.event)
        assertEquals("seq-10", response.event?.eventId)
        assertEquals("Sequence 10", response.event?.eventString)
    }
    
    @Test
    fun `should handle concurrent event processing`() {
        val actor = testKit.spawn(EventConsumerActor.create())
        val countProbe = testKit.createTestProbe<EventResponse>()
        
        val eventCount = 100
        val events = (1..eventCount).map { i ->
            KafkaEvent(
                eventType = "CONCURRENT_TEST",
                eventId = "concurrent-$i",
                eventString = "Concurrent event $i"
            )
        }
        
        events.parallelStream().forEach { event ->
            actor.tell(ProcessEvent(event))
        }
        
        Thread.sleep(1000)
        
        actor.tell(GetEventCount(countProbe.ref))
        val response = countProbe.expectMessageClass(EventCountResponse::class.java, Duration.ofSeconds(5)) as EventCountResponse
        
        assertEquals(eventCount, response.count)
    }
}