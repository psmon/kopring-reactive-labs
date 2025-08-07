package com.example.connectorkafka.producer

import com.example.connectorkafka.model.KafkaEvent
import com.typesafe.config.ConfigFactory
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
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
class EventProducerTest {
    
    companion object {
        @Container
        @JvmStatic
        val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withEmbeddedZookeeper()
    }
    
    private lateinit var testKit: ActorTestKit
    private lateinit var producer: EventProducer
    
    @BeforeAll
    fun setupAll() {
        kafkaContainer.start()
    }
    
    @BeforeEach
    fun setup() {
        val config = ConfigFactory.parseString("""
            pekko {
                actor {
                    provider = local
                    allow-java-serialization = on
                }
                loglevel = "INFO"
                
                kafka {
                    producer {
                        kafka-clients {
                            client.id = "test-producer"
                            acks = "all"
                            retries = 3
                        }
                    }
                }
            }
            
            kafka {
                bootstrap.servers = "${kafkaContainer.bootstrapServers}"
            }
        """).withFallback(ConfigFactory.load("application-test.conf"))
        
        testKit = ActorTestKit.create("ProducerTestSystem", config)
        producer = EventProducer(testKit.system(), "test-topic1")
        producer.resetCount()
    }
    
    @AfterEach
    fun teardown() {
        testKit.shutdownTestKit()
    }
    
    @AfterAll
    fun teardownAll() {
        kafkaContainer.stop()
    }
    
    @Test
    fun `should send single event successfully`() {
        val event = KafkaEvent(
            eventType = "TEST_TYPE",
            eventId = UUID.randomUUID().toString(),
            eventString = "Test event message"
        )
        
        val future = producer.sendEvent(event).toCompletableFuture()
        val result = future.get(10, TimeUnit.SECONDS)
        
        assertEquals(Done.done(), result)
        assertEquals(1, producer.getProducedCount())
    }
    
    @Test
    fun `should send multiple events in batch`() {
        val events = (1..10).map { i ->
            KafkaEvent(
                eventType = "BATCH_TYPE",
                eventId = "batch-$i",
                eventString = "Batch event $i"
            )
        }
        
        val future = producer.sendEvents(events).toCompletableFuture()
        val result = future.get(10, TimeUnit.SECONDS)
        
        assertEquals(Done.done(), result)
        assertEquals(10, producer.getProducedCount())
    }
    
    @Test
    fun `should send event with metadata and get partition info`() {
        val event = KafkaEvent(
            eventType = "METADATA_TYPE",
            eventId = UUID.randomUUID().toString(),
            eventString = "Event with metadata"
        )
        
        val future = producer.sendEventWithMetadata(event).toCompletableFuture()
        val result = future.get(10, TimeUnit.SECONDS)
        
        assertNotNull(result)
        assertNotNull(result.metadata())
        val metadata = result.metadata()
        assertEquals("test-topic1", metadata.topic())
        assertTrue(metadata.offset() >= 0)
        assertTrue(metadata.partition() >= 0)
        assertEquals(1, producer.getProducedCount())
    }
    
    @Test
    fun `should handle high volume of events`() {
        val eventCount = 100
        val events = (1..eventCount).map { i ->
            KafkaEvent(
                eventType = "LOAD_TEST",
                eventId = "load-$i",
                eventString = "Load test event $i"
            )
        }
        
        val startTime = System.currentTimeMillis()
        val future = producer.sendEvents(events).toCompletableFuture()
        val result = future.get(30, TimeUnit.SECONDS)
        val endTime = System.currentTimeMillis()
        
        assertEquals(Done.done(), result)
        assertEquals(eventCount, producer.getProducedCount())
        
        val duration = endTime - startTime
        val tps = (eventCount * 1000.0) / duration
        
        println("Sent $eventCount events in ${duration}ms (TPS: %.2f)".format(tps))
        assertTrue(tps > 10, "TPS should be greater than 10")
    }
    
    @Test
    fun `should reset count correctly`() {
        val event = KafkaEvent(
            eventType = "RESET_TEST",
            eventId = UUID.randomUUID().toString(),
            eventString = "Reset test"
        )
        
        producer.sendEvent(event).toCompletableFuture().get(10, TimeUnit.SECONDS)
        assertEquals(1, producer.getProducedCount())
        
        producer.resetCount()
        assertEquals(0, producer.getProducedCount())
    }
}