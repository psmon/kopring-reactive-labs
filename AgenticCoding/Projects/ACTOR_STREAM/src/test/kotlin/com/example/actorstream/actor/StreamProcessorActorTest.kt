package com.example.actorstream.actor

import com.example.actorstream.model.*
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StreamProcessorActorTest {
    
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
    fun `should process text with words and numbers correctly`() = runTest {
        val probe = testKit.createTestProbe<StreamCommand>()
        val actor = testKit.spawn(StreamProcessorActor.create(probe.ref))
        
        val testText = "abc 345 def sdf"
        actor.tell(ProcessText(testText))
        
        val result = probe.expectMessageClass(StreamResult::class.java)
        
        result.originalText shouldBe testText
        result.words shouldBe listOf("abc", "def", "sdf")
        result.wordCount shouldBe 3
        result.numberSum shouldBe 345
        
        probe.expectMessage(StreamCompleted)
    }
    
    @Test
    fun `should handle multiple numbers in text`() = runTest {
        val probe = testKit.createTestProbe<StreamCommand>()
        val actor = testKit.spawn(StreamProcessorActor.create(probe.ref))
        
        val testText = "hello 100 world 200 test 50"
        actor.tell(ProcessText(testText))
        
        val result = probe.expectMessageClass(StreamResult::class.java)
        
        result.originalText shouldBe testText
        result.words shouldBe listOf("hello", "world", "test")
        result.wordCount shouldBe 3
        result.numberSum shouldBe 350
        
        probe.expectMessage(StreamCompleted)
    }
    
    @Test
    fun `should handle text with no numbers`() = runTest {
        val probe = testKit.createTestProbe<StreamCommand>()
        val actor = testKit.spawn(StreamProcessorActor.create(probe.ref))
        
        val testText = "hello world test"
        actor.tell(ProcessText(testText))
        
        val result = probe.expectMessageClass(StreamResult::class.java)
        
        result.originalText shouldBe testText
        result.words shouldBe listOf("hello", "world", "test")
        result.wordCount shouldBe 3
        result.numberSum shouldBe 0
        
        probe.expectMessage(StreamCompleted)
    }
    
    @Test
    fun `should handle text with only numbers`() = runTest {
        val probe = testKit.createTestProbe<StreamCommand>()
        val actor = testKit.spawn(StreamProcessorActor.create(probe.ref))
        
        val testText = "100 200 300"
        actor.tell(ProcessText(testText))
        
        val result = probe.expectMessageClass(StreamResult::class.java)
        
        result.originalText shouldBe testText
        result.words shouldBe emptyList()
        result.wordCount shouldBe 0
        result.numberSum shouldBe 600
        
        probe.expectMessage(StreamCompleted)
    }
    
    @Test
    fun `should process multiple texts concurrently`() = runTest {
        val probe = testKit.createTestProbe<StreamCommand>()
        val actor = testKit.spawn(StreamProcessorActor.create(probe.ref))
        
        val texts = listOf(
            "first 10 test",
            "second 20 example",
            "third 30 sample"
        )
        
        texts.forEach { text ->
            actor.tell(ProcessText(text))
        }
        
        val results = mutableListOf<StreamResult>()
        repeat(texts.size) {
            results.add(probe.expectMessageClass(StreamResult::class.java))
            probe.expectMessage(StreamCompleted)
        }
        
        results.size shouldBe 3
        results.map { it.wordCount } shouldBe listOf(2, 2, 2)
        results.map { it.numberSum } shouldBe listOf(10, 20, 30)
    }
}