package com.example.connectorkafka.producer

import com.example.connectorkafka.model.KafkaEvent
import com.example.connectorkafka.model.KafkaEventSerializer
import com.typesafe.config.Config
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.kafka.ProducerMessage
import org.apache.pekko.kafka.ProducerSettings
import org.apache.pekko.kafka.javadsl.Producer
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.javadsl.Source
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

class EventProducer(
    private val system: ActorSystem<*>,
    private val topicName: String = "test-topic1"
) {
    private val logger = LoggerFactory.getLogger(EventProducer::class.java)
    private val materializer: Materializer = Materializer.createMaterializer(system)
    private val producerSettings: ProducerSettings<String, KafkaEvent>
    private val producedCount = AtomicInteger(0)
    
    init {
        val config: Config = system.settings().config().getConfig("pekko.kafka.producer")
        producerSettings = ProducerSettings.create(config, StringSerializer(), KafkaEventSerializer())
            .withBootstrapServers(system.settings().config().getString("kafka.bootstrap.servers"))
    }
    
    fun sendEvent(event: KafkaEvent): CompletionStage<Done> {
        val record = ProducerRecord<String, KafkaEvent>(topicName, event.eventId, event)
        
        return Source.single(record)
            .runWith(Producer.plainSink(producerSettings), materializer)
            .thenApply { done ->
                producedCount.incrementAndGet()
                logger.info("Event sent: ${event.eventId} - Total produced: ${producedCount.get()}")
                done
            }
    }
    
    fun sendEvents(events: List<KafkaEvent>): CompletionStage<Done> {
        val records = events.map { event ->
            ProducerRecord<String, KafkaEvent>(topicName, event.eventId, event)
        }
        
        return Source.from(records)
            .runWith(Producer.plainSink(producerSettings), materializer)
            .thenApply { done ->
                producedCount.addAndGet(events.size)
                logger.info("Batch of ${events.size} events sent - Total produced: ${producedCount.get()}")
                done
            }
    }
    
    fun sendEventWithMetadata(event: KafkaEvent): CompletionStage<ProducerMessage.Result<String, KafkaEvent, KafkaEvent>> {
        val message = ProducerMessage.single(
            ProducerRecord<String, KafkaEvent>(topicName, event.eventId, event),
            event
        )
        
        return Source.single(message)
            .via(Producer.flexiFlow(producerSettings))
            .map { result ->
                when (result) {
                    is ProducerMessage.Result<*, *, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        result as ProducerMessage.Result<String, KafkaEvent, KafkaEvent>
                    }
                    else -> throw IllegalStateException("Unexpected result type")
                }
            }
            .runWith(Sink.head(), materializer)
            .thenApply { result ->
                producedCount.incrementAndGet()
                val metadata = result.metadata()
                logger.info("Event sent with metadata - Topic: ${metadata.topic()}, Partition: ${metadata.partition()}, Offset: ${metadata.offset()}")
                result
            }
    }
    
    fun getProducedCount(): Int = producedCount.get()
    
    fun resetCount() {
        producedCount.set(0)
    }
}