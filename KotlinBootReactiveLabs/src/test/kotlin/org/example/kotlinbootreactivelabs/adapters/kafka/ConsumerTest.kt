package org.example.kotlinbootreactivelabs.adapters.kafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.kafka.CommitterSettings
import org.apache.pekko.kafka.ConsumerSettings
import org.apache.pekko.kafka.ProducerSettings
import org.apache.pekko.kafka.Subscriptions
import org.apache.pekko.kafka.javadsl.Committer
import org.apache.pekko.kafka.javadsl.Consumer
import org.apache.pekko.kafka.javadsl.Producer
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.javadsl.Source
import org.example.kotlinbootreactivelabs.actor.guide.HelloWorld
import org.example.kotlinbootreactivelabs.actor.guide.SayHello
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CompletionStage

// https://pekko.apache.org/docs/pekko-connectors-kafka/current/consumer.html#choosing-a-consumer
class ConsumerTest {
    private lateinit var akkaSystem: ActorTestKit

    private lateinit var conssumerSettings: ConsumerSettings<String, String>

    private lateinit var producerSettings: ProducerSettings<String, String>

    @BeforeEach
    fun setup(){
        val clusterConfigA = ConfigFactory.load("kafka.conf")
        akkaSystem = ActorTestKit.create("KafkaSystem",clusterConfigA)

        val producerConfig: Config = akkaSystem.system().settings().config().getConfig("pekko.kafka.producer")
        producerSettings = ProducerSettings.create(producerConfig, StringSerializer(), StringSerializer())
            .withBootstrapServers("localhost:9092,localhost:9093,localhost:9094")

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

    fun producerAsSink(teCount: Int){
        val materializer = Materializer.createMaterializer(akkaSystem.system())
        val topic = "test_topic1"
        val done: CompletionStage<Done> =
            Source.range(1, 100)
                .map { number -> number.toString() }
                .map { value -> ProducerRecord<String, String>(topic, value) }
                .runWith(Producer.plainSink(producerSettings), materializer)

        done.toCompletableFuture().get()

        done.thenAccept { result -> println("Done: $result") }
    }

    @Test
    fun testConsumerWithCommit(){
        // Given : Generate 100(testCount) messages
        val testCount = 100
        producerAsSink(testCount)

        var probe = akkaSystem.createTestProbe<String>()
        val actor = akkaSystem.spawn(HelloWorld.create())

        val topic = "test_topic1"
        val materializer = Materializer.createMaterializer(akkaSystem.system())
        val config: Config = akkaSystem.system().settings().config().getConfig("pekko.kafka.committer")
        val committerSettings = CommitterSettings.create(config)

        val control: Consumer.DrainingControl<Done> =
            Consumer.committableSource(conssumerSettings, Subscriptions.topics(topic))
                .map {  record ->
                    println("Key: ${record.record().key()}, Value: ${record.record().value()}")
                    actor.tell(SayHello("Hello", probe.ref))
                    record.committableOffset()
                }
                .toMat(Committer.sink(committerSettings), Consumer::createDrainingControl)
                .run(materializer)

        // When : testCount messages are consumed
        for (i in 1..testCount) {
            probe.expectMessage(Duration.ofSeconds(10),"Hello World")
        }
    }
}