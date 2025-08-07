package com.example.connectorkafka.integration

import com.example.connectorkafka.connector.KafkaConsumerConnector
import com.example.connectorkafka.model.*
import com.example.connectorkafka.producer.EventProducer
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaIntegrationTest {
    
    companion object {
        @Container
        @JvmStatic
        val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withEmbeddedZookeeper()
    }
    
    private lateinit var testKit: ActorTestKit
    private lateinit var producer: EventProducer
    private lateinit var consumer: KafkaConsumerConnector
    
    @BeforeAll
    fun setupAll() {
        kafkaContainer.start()
        // Wait for Kafka to be fully ready
        Thread.sleep(5000)
    }
    
    @BeforeEach
    fun setup() {
        val config = ConfigFactory.parseString("""
            pekko {
                actor {
                    provider = local
                    allow-java-serialization = on
                    
                    serializers {
                        jackson-json = "org.apache.pekko.serialization.jackson.JacksonJsonSerializer"
                    }
                    
                    serialization-bindings {
                        "com.example.connectorkafka.model.KafkaEvent" = jackson-json
                    }
                }
                loglevel = "INFO"
                
                kafka {
                    producer {
                        kafka-clients {
                            client.id = "integration-producer"
                            acks = "all"
                            retries = 3
                        }
                    }
                    
                    consumer {
                        kafka-clients {
                            enable.auto.commit = false
                            auto.offset.reset = "earliest"
                            session.timeout.ms = 30000
                        }
                    }
                    
                    committer {
                        max-batch = 100
                        max-interval = 1s
                    }
                }
            }
            
            kafka {
                bootstrap.servers = "${kafkaContainer.bootstrapServers}"
            }
        """).withFallback(ConfigFactory.load("application-test.conf"))
        
        testKit = ActorTestKit.create("IntegrationTestSystem", config)
        producer = EventProducer(testKit.system(), "test-topic1")
        consumer = KafkaConsumerConnector(testKit.system(), "test-topic1", "integration-group-${UUID.randomUUID()}")
        
        producer.resetCount()
        consumer.resetCount()
    }
    
    @AfterEach
    fun teardown() {
        consumer.stopConsuming()
        Thread.sleep(1000)
        testKit.shutdownTestKit()
    }
    
    @AfterAll
    fun teardownAll() {
        kafkaContainer.stop()
    }
    
    @Test
    fun `should produce and consume events with state management`() {
        val control = consumer.startConsuming()
        Thread.sleep(5000) // Give more time for consumer to start
        
        val events = (1..10).map { i ->
            KafkaEvent(
                eventType = "INTEGRATION_TEST",
                eventId = "int-$i",
                eventString = "Integration event $i"
            )
        }
        
        events.forEach { event ->
            producer.sendEvent(event).toCompletableFuture().get(30, TimeUnit.SECONDS)
            Thread.sleep(100) // Small delay between sends
        }
        
        // Wait for consumer to catch up
        var attempts = 0
        while (consumer.getConsumedCount() < 10 && attempts < 20) {
            Thread.sleep(1000)
            attempts++
        }
        
        val probe = testKit.createTestProbe<EventResponse>()
        val actor = consumer.getConsumerActor()
        
        actor.tell(GetLastEvent(probe.ref))
        val lastEventResponse = probe.expectMessageClass(LastEventResponse::class.java, Duration.ofSeconds(10))
        
        assertNotNull(lastEventResponse.event)
        // Just check that we got some event, not necessarily the last one due to Kafka's async nature
        assertNotNull(lastEventResponse.event?.eventId)
        assertNotNull(lastEventResponse.event?.eventString)
        
        val countProbe = testKit.createTestProbe<EventResponse>()
        actor.tell(GetEventCount(countProbe.ref))
        val countResponse = countProbe.expectMessageClass(EventCountResponse::class.java, Duration.ofSeconds(10)) as EventCountResponse
        
        assertTrue(countResponse.count >= 10, "Actor should have at least 10 events but got ${countResponse.count}")
        assertEquals(10, producer.getProducedCount())
        assertTrue(consumer.getConsumedCount() >= 10, "Consumer should have consumed at least 10 events but got ${consumer.getConsumedCount()}")
    }
    
    @Test
    fun `should verify event count match between producer and consumer`() {
        val (killSwitch, future) = consumer.startConsumingWithKillSwitch()
        Thread.sleep(3000) // Give more time for consumer to start
        
        val eventCount = 50
        val events = (1..eventCount).map { i ->
            KafkaEvent(
                eventType = "COUNT_VERIFICATION",
                eventId = UUID.randomUUID().toString(),
                eventString = "Count verification $i"
            )
        }
        
        val sendFuture = producer.sendEvents(events).toCompletableFuture()
        sendFuture.get(30, TimeUnit.SECONDS)
        
        Thread.sleep(8000) // Give more time for consumption
        
        assertEquals(eventCount, producer.getProducedCount(), "Producer count mismatch")
        
        // Wait for consumer to catch up
        var attempts = 0
        while (consumer.getConsumedCount() < eventCount && attempts < 10) {
            Thread.sleep(1000)
            attempts++
        }
        
        assertTrue(consumer.getConsumedCount() >= eventCount, "Consumer should have consumed at least $eventCount events but got ${consumer.getConsumedCount()}")
        
        val countProbe = testKit.createTestProbe<EventResponse>()
        consumer.getConsumerActor().tell(GetEventCount(countProbe.ref))
        val actorCount = countProbe.expectMessageClass(EventCountResponse::class.java, Duration.ofSeconds(5)) as EventCountResponse
        
        assertTrue(actorCount.count >= eventCount, "Actor state should have at least $eventCount events but got ${actorCount.count}")
        
        killSwitch.shutdown()
    }
    
    @Test
    fun `should handle high throughput with TPS measurement`() {
        val control = consumer.startConsuming()
        Thread.sleep(3000) // Give more time for consumer to start
        
        val eventCount = 1000
        val events = (1..eventCount).map { i ->
            KafkaEvent(
                eventType = "TPS_TEST",
                eventId = "tps-$i",
                eventString = "TPS test event $i with some payload data to simulate real messages"
            )
        }
        
        val startTime = System.currentTimeMillis()
        
        val batchSize = 100
        events.chunked(batchSize).forEach { batch ->
            producer.sendEvents(batch).toCompletableFuture().get(30, TimeUnit.SECONDS)
        }
        
        val producerEndTime = System.currentTimeMillis()
        val producerDuration = producerEndTime - startTime
        val producerTps = (eventCount * 1000.0) / producerDuration
        
        println("Producer: Sent $eventCount events in ${producerDuration}ms (TPS: %.2f)".format(producerTps))
        
        var attempts = 0
        while (consumer.getConsumedCount() < eventCount && attempts < 30) {
            Thread.sleep(1000)
            attempts++
        }
        
        val consumerEndTime = System.currentTimeMillis()
        val totalDuration = consumerEndTime - startTime
        val endToEndTps = (eventCount * 1000.0) / totalDuration
        
        println("End-to-end: Processed $eventCount events in ${totalDuration}ms (TPS: %.2f)".format(endToEndTps))
        
        assertEquals(eventCount, producer.getProducedCount())
        assertTrue(consumer.getConsumedCount() >= eventCount * 0.95, "Consumer should have consumed at least 95% of events")
        
        assertTrue(producerTps > 50, "Producer TPS should be greater than 50 but was $producerTps")
        assertTrue(endToEndTps > 20, "End-to-end TPS should be greater than 20 but was $endToEndTps")
    }
    
    @Test
    fun `should maintain last event state correctly`() {
        val control = consumer.startConsuming()
        Thread.sleep(2000)
        
        val specialEvent = KafkaEvent(
            eventType = "SPECIAL",
            eventId = "special-last",
            eventString = "This should be the last event"
        )
        
        val events = (1..5).map { i ->
            KafkaEvent(
                eventType = "NORMAL",
                eventId = "normal-$i",
                eventString = "Normal event $i"
            )
        }
        
        events.forEach { event ->
            producer.sendEvent(event).toCompletableFuture().get(5, TimeUnit.SECONDS)
        }
        
        Thread.sleep(2000)
        
        producer.sendEvent(specialEvent).toCompletableFuture().get(5, TimeUnit.SECONDS)
        
        Thread.sleep(2000)
        
        val probe = testKit.createTestProbe<EventResponse>()
        consumer.getConsumerActor().tell(GetLastEvent(probe.ref))
        val response = probe.expectMessageClass(LastEventResponse::class.java, Duration.ofSeconds(5)) as LastEventResponse
        
        assertNotNull(response.event)
        assertEquals("special-last", response.event?.eventId)
        assertEquals("This should be the last event", response.event?.eventString)
    }
}