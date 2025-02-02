package org.example.kotlinbootreactivelabs.adapters.kafka


import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.kafka.ProducerMessage
import org.apache.pekko.kafka.ProducerSettings
import org.apache.pekko.kafka.javadsl.Producer
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.javadsl.Source
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletionStage

data class PassThrough(val info: String)

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

        done.toCompletableFuture().get()

        done.thenAccept { result -> println("Done: $result") }
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

        done.toCompletableFuture().get()

        done.thenAccept { result -> println("Done: $result") }
    }

    @Test
    fun testProducerSingleMessageWithPassThrough() {
        val materializer = Materializer.createMaterializer(akkaSystem.system())
        val topic = "test_topic1"
        val key = "singleKey"
        val value = "singleValue"
        val passThrough = PassThrough("passThroughInfo")

        val single: ProducerMessage.Envelope<String, String, PassThrough> =
            ProducerMessage.single(
                ProducerRecord(topic, key, value),
                passThrough
            )

        val done: CompletionStage<Done> = Source.single(single)
            .via(Producer.flexiFlow(producerSettings))
            .runWith(Sink.foreach { result ->
                when (result) {
                    is ProducerMessage.Result<*, *, *> -> {
                        val res = result as ProducerMessage.Result<String, String, PassThrough>
                        val record = res.message().record()
                        val meta = res.metadata()
                        println("${meta.topic()}/${meta.partition()} ${meta.offset()}: ${record.value()} with passThrough: ${res.message().passThrough().info}")
                    }
                    else -> println("unknown result")
                }
            }, materializer)

        done.toCompletableFuture().get()

        done.thenAccept { result -> println("Done: $result") }
    }

    @Test
    fun testProducerMultiMessageWithPassThrough(){
        val materializer = Materializer.createMaterializer(akkaSystem.system())
        val passThrough = PassThrough("passThroughInfo")

        val multiMessage: ProducerMessage.Envelope<String, String, PassThrough> =
            ProducerMessage.multi(
                listOf(
                    ProducerRecord("test_topic1", "multiKey1","multiValue1"),
                    ProducerRecord("test_topic2", "multiKey2","multiValue2")
                ),
                passThrough
            )

        val done: CompletionStage<Done> = Source.single(multiMessage)
            .via(Producer.flexiFlow(producerSettings))
            .runWith(Sink.foreach { result ->
                when (result) {
                    is ProducerMessage.Result<*, *, *> -> {
                        val res = result as ProducerMessage.Result<String, String, PassThrough>
                        val record = res.message().record()
                        val meta = res.metadata()
                        println("${meta.topic()}/${meta.partition()} ${meta.offset()}: ${record.value()} with passThrough: ${res.message().passThrough().info}")
                    }
                    else -> println("unknown result")
                }
            }, materializer)

        done.toCompletableFuture().get()

        done.thenAccept { result -> println("Done: $result") }
    }

}