package org.example.kotlinbootreactivelabs.adapters.kafka


import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.kafka.ProducerSettings
import org.apache.pekko.kafka.javadsl.Producer
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.javadsl.Source
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletionStage

// https://pekko.apache.org/docs/pekko-connectors-kafka/current/producer.html
class ProducerTest {

    private lateinit var akkaSystem: ActorTestKit

    private lateinit var producerSettings: ProducerSettings<String, String>

    @BeforeEach
    fun setup(){
        val clusterConfigA = ConfigFactory.load("kafka.conf")
        akkaSystem = ActorTestKit.create("KafkaSystem",clusterConfigA)

        val config: Config = akkaSystem.system().settings().config().getConfig("pekko.kafka.producer")
        producerSettings = ProducerSettings.create(config, StringSerializer(), StringSerializer())
            .withBootstrapServers("localhost:9092,localhost:9093,localhost:9094")

    }

    @AfterEach
    fun teardown() {
        akkaSystem.shutdownTestKit()
    }

    fun waitForSomeCompleted(seconds: Number) {
        Thread.sleep(seconds.toLong() * 1000)
    }

    @Test
    fun testProducerAsSink(){
        val materializer = Materializer.createMaterializer(akkaSystem.system())
        val topic = "test_topic1"
        val done: CompletionStage<Done> =
            Source.range(1, 100)
                .map { number -> number.toString() }
                .map { value -> ProducerRecord<String, String>(topic, value) }
                .runWith(Producer.plainSink(producerSettings), materializer)

        waitForSomeCompleted(5)
    }

    @Test
    fun testProducerSingleMessage(){
        val materializer = Materializer.createMaterializer(akkaSystem.system())

        val topic = "test_topic1"
        val key = "singleKey"
        val value = "singleValue"
        val message: ProducerRecord<String, String> = ProducerRecord(topic, key, value)
        val done: CompletionStage<Done> =
            Source.single(message)
                .runWith(Producer.plainSink(producerSettings), materializer)

        waitForSomeCompleted(3)
    }

}