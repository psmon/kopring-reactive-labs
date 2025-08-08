package com.example.pekkohttp.actor

import com.example.pekkohttp.model.*
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HelloActorTest {
    
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
    fun `should respond with Pekko message for hello`() = runTest {
        val probe = testKit.createTestProbe<HelloResponse>()
        val actor = testKit.spawn(HelloActor.create())
        
        actor.tell(GetHello("hello", probe.ref))
        
        val response = probe.expectMessageClass(HelloResponse::class.java)
        response.message shouldBe "Pekko responds with warm greetings!"
    }
    
    @Test
    fun `should respond with world message`() = runTest {
        val probe = testKit.createTestProbe<HelloResponse>()
        val actor = testKit.spawn(HelloActor.create())
        
        actor.tell(GetHello("world", probe.ref))
        
        val response = probe.expectMessageClass(HelloResponse::class.java)
        response.message shouldBe "Pekko says hello to the World!"
    }
    
    @Test
    fun `should respond with meta message for pekko`() = runTest {
        val probe = testKit.createTestProbe<HelloResponse>()
        val actor = testKit.spawn(HelloActor.create())
        
        actor.tell(GetHello("pekko", probe.ref))
        
        val response = probe.expectMessageClass(HelloResponse::class.java)
        response.message shouldBe "Pekko talking to itself? How meta!"
    }
    
    @Test
    fun `should respond with evolution message for akka`() = runTest {
        val probe = testKit.createTestProbe<HelloResponse>()
        val actor = testKit.spawn(HelloActor.create())
        
        actor.tell(GetHello("akka", probe.ref))
        
        val response = probe.expectMessageClass(HelloResponse::class.java)
        response.message shouldBe "Pekko is the new evolution of Akka!"
    }
    
    @Test
    fun `should respond with custom name message`() = runTest {
        val probe = testKit.createTestProbe<HelloResponse>()
        val actor = testKit.spawn(HelloActor.create())
        
        actor.tell(GetHello("Alice", probe.ref))
        
        val response = probe.expectMessageClass(HelloResponse::class.java)
        response.message shouldBe "Pekko says hello to Alice!"
    }
    
    @Test
    fun `should handle multiple requests`() = runTest {
        val probe = testKit.createTestProbe<HelloResponse>()
        val actor = testKit.spawn(HelloActor.create())
        
        val names = listOf("Bob", "Charlie", "Diana")
        
        names.forEach { name ->
            actor.tell(GetHello(name, probe.ref))
            val response = probe.expectMessageClass(HelloResponse::class.java)
            response.message shouldBe "Pekko says hello to $name!"
        }
    }
}