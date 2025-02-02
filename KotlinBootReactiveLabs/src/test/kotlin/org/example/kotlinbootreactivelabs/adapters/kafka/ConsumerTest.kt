package org.example.kotlinbootreactivelabs.adapters.kafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.kafka.CommitterSettings
import org.apache.pekko.kafka.ConsumerSettings
import org.apache.pekko.kafka.Subscriptions
import org.apache.pekko.kafka.javadsl.Committer
import org.apache.pekko.kafka.javadsl.Consumer
import org.apache.pekko.stream.Materializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// https://pekko.apache.org/docs/pekko-connectors-kafka/current/consumer.html#choosing-a-consumer
class ConsumerTest {
    private lateinit var akkaSystem: ActorTestKit

    private lateinit var conssumerSettings: ConsumerSettings<String, String>

    @BeforeEach
    fun setup(){
        val clusterConfigA = ConfigFactory.load("kafka.conf")
        akkaSystem = ActorTestKit.create("KafkaSystem",clusterConfigA)

        val config: Config = akkaSystem.system().settings().config().getConfig("pekko.kafka.consumer")

        conssumerSettings = ConsumerSettings.create(config, StringDeserializer(), StringDeserializer())
            .withBootstrapServers("localhost:9092,localhost:9093,localhost:9094")
            .withGroupId("test_group1")
            .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
            .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000")

    }

    @AfterEach
    fun teardown() {
        akkaSystem.shutdownTestKit()
    }

    fun waitForSomeCompleted(seconds: Number) {
        Thread.sleep(seconds.toLong() * 1000)
    }

    @Test
    fun testConsumerWithCommit(){
        val topic = "test_topic1"
        val materializer = Materializer.createMaterializer(akkaSystem.system())
        val config: Config = akkaSystem.system().settings().config().getConfig("pekko.kafka.committer")
        val committerSettings = CommitterSettings.create(config)

        val control: Consumer.DrainingControl<Done> =
            Consumer.committableSource(conssumerSettings, Subscriptions.topics(topic))
                .map {  record ->
                    println("Key: ${record.record().key()}, Value: ${record.record().value()}")
                    record.committableOffset()
                }
                .toMat(Committer.sink(committerSettings), Consumer::createDrainingControl)
                .run(materializer)

        waitForSomeCompleted(5)
    }
}