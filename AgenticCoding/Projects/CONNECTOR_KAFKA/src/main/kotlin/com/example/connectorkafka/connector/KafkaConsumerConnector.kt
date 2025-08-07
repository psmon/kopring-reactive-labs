package com.example.connectorkafka.connector

import com.example.connectorkafka.actor.EventConsumerActor
import com.example.connectorkafka.model.*
import com.typesafe.config.Config
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.kafka.CommitterSettings
import org.apache.pekko.kafka.ConsumerSettings
import org.apache.pekko.kafka.Subscriptions
import org.apache.pekko.kafka.javadsl.Committer
import org.apache.pekko.kafka.javadsl.Consumer
import org.apache.pekko.stream.KillSwitches
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.UniqueKillSwitch
import org.apache.pekko.stream.javadsl.Keep
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

class KafkaConsumerConnector(
    private val system: ActorSystem<*>,
    private val topicName: String = "test-topic1",
    private val groupId: String = "test_group1"
) {
    private val logger = LoggerFactory.getLogger(KafkaConsumerConnector::class.java)
    private val materializer: Materializer = Materializer.createMaterializer(system)
    private val consumerSettings: ConsumerSettings<String, KafkaEvent>
    private val committerSettings: CommitterSettings
    private val consumedCount = AtomicInteger(0)
    private lateinit var consumerActor: ActorRef<EventCommand>
    private var killSwitch: UniqueKillSwitch? = null
    
    init {
        val config: Config = system.settings().config().getConfig("pekko.kafka.consumer")
        consumerSettings = ConsumerSettings.create(config, StringDeserializer(), KafkaEventDeserializer())
            .withBootstrapServers(system.settings().config().getString("kafka.bootstrap.servers"))
            .withGroupId(groupId)
            .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            
        val committerConfig: Config = system.settings().config().getConfig("pekko.kafka.committer")
        committerSettings = CommitterSettings.create(committerConfig)
        
        consumerActor = system.systemActorOf(EventConsumerActor.create(), "event-consumer-actor", org.apache.pekko.actor.typed.Props.empty())
    }
    
    fun startConsuming(): Consumer.DrainingControl<Done> {
        logger.info("Starting Kafka consumer for topic: $topicName, group: $groupId")
        
        val control = Consumer.committableSource(consumerSettings, Subscriptions.topics(topicName))
            .map { record ->
                val event = record.record().value()
                logger.debug("Received event: ${event.eventId} - Type: ${event.eventType}")
                
                consumerActor.tell(ProcessEvent(event))
                consumedCount.incrementAndGet()
                
                record.committableOffset()
            }
            .toMat(Committer.sink(committerSettings), Consumer::createDrainingControl)
            .run(materializer)
            
        logger.info("Kafka consumer started successfully")
        return control
    }
    
    fun startConsumingWithKillSwitch(): Pair<UniqueKillSwitch, CompletionStage<Done>> {
        logger.info("Starting Kafka consumer with kill switch for topic: $topicName, group: $groupId")
        
        val result = Consumer.committableSource(consumerSettings, Subscriptions.topics(topicName))
            .viaMat(KillSwitches.single(), Keep.right())
            .map { record ->
                val event = record.record().value()
                logger.debug("Received event: ${event.eventId} - Type: ${event.eventType}")
                
                consumerActor.tell(ProcessEvent(event))
                consumedCount.incrementAndGet()
                
                record.committableOffset()
            }
            .toMat(Committer.sink(committerSettings), Keep.both())
            .run(materializer)
            
        killSwitch = result.first()
        logger.info("Kafka consumer with kill switch started successfully")
        
        return Pair(result.first(), result.second())
    }
    
    fun stopConsuming() {
        killSwitch?.shutdown()
        logger.info("Kafka consumer stopped")
    }
    
    fun getConsumedCount(): Int = consumedCount.get()
    
    fun getConsumerActor(): ActorRef<EventCommand> = consumerActor
    
    fun resetCount() {
        consumedCount.set(0)
        consumerActor.tell(ClearEvents)
    }
}