package com.example.pekkohttp.integration

import com.example.pekkohttp.actor.*
import com.example.pekkohttp.marshalling.JsonSupport
import com.example.pekkohttp.model.*
import com.example.pekkohttp.routes.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.longs.shouldBeGreaterThan
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.junit.jupiter.api.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PekkoHttpIntegrationTest {
    
    private lateinit var system: ActorSystem<Void>
    private val httpClient = HttpClient.newHttpClient()
    private val baseUrl = "http://localhost:8081" // Using different port to avoid conflicts
    
    @BeforeAll
    fun setup() {
        system = ActorSystem.create(Behaviors.empty(), "TestSystem")
    }
    
    @AfterAll
    fun teardown() {
        system.terminate()
    }
    
    @Test
    fun `test hello actor responds correctly`() = runTest {
        val helloActor = system.systemActorOf(HelloActor.create(), "testHelloActor", org.apache.pekko.actor.typed.Props.empty())
        val probe = org.apache.pekko.actor.testkit.typed.javadsl.TestProbe.create(HelloResponse::class.java, system)
        
        helloActor.tell(GetHello("Test", probe.ref))
        
        val response = probe.expectMessageClass(HelloResponse::class.java)
        response.message shouldBe "Pekko says hello to Test!"
    }
    
    @Test
    fun `test event actor processes events`() = runTest {
        val eventActor = system.systemActorOf(EventStreamActor.create(), "testEventActor", org.apache.pekko.actor.typed.Props.empty())
        
        val event = UserEvent(
            userId = "testUser",
            eventType = "test",
            action = "testAction"
        )
        
        eventActor.tell(ProcessEvent(event))
        
        //delay(100) // Small delay to allow actor to process the message
        
        val statsProbe = org.apache.pekko.actor.testkit.typed.javadsl.TestProbe.create(EventStats::class.java, system)
        eventActor.tell(GetEventStats(statsProbe.ref))
        
        val stats = statsProbe.expectMessageClass(EventStats::class.java)
        stats.totalEvents shouldBe 1
        stats.eventsPerType["test"] shouldBe 1
        stats.eventsPerUser["testUser"] shouldBe 1
    }
    
    @Test
    fun `test JSON marshalling and unmarshalling`() {
        val request = HelloRequest("World")
        val json = JsonSupport.objectMapper.writeValueAsString(request)
        json shouldContain "World"
        
        val deserialized = JsonSupport.objectMapper.readValue(json, HelloRequest::class.java)
        deserialized.name shouldBe "World"
    }
    
    @Test
    fun `test event request serialization`() {
        val event = EventRequest(
            userId = "user123",
            eventType = "click",
            action = "button_click",
            metadata = mapOf("page" to "home")
        )
        
        val json = JsonSupport.objectMapper.writeValueAsString(event)
        json shouldContain "user123"
        json shouldContain "click"
        json shouldContain "button_click"
        json shouldContain "home"
    }
    
    @Test
    fun `test WebSocket actor handles connections`() = runTest {
        val wsActor = system.systemActorOf(WebSocketActor.create(), "testWsActor", org.apache.pekko.actor.typed.Props.empty())
        val probe = org.apache.pekko.actor.testkit.typed.javadsl.TestProbe.create(WSMessage::class.java, system)
        
        wsActor.tell(WSConnect("conn1", probe.ref))
        
        val welcome = probe.expectMessageClass(WSMessage::class.java)
        welcome.message shouldContain "Welcome"
        welcome.message shouldContain "conn1"
    }
    
    @Test
    fun `test WebSocket broadcast`() = runTest {
        val wsActor = system.systemActorOf(WebSocketActor.create(), "testWsBroadcastActor", org.apache.pekko.actor.typed.Props.empty())
        
        // Connect two clients
        val probe1 = org.apache.pekko.actor.testkit.typed.javadsl.TestProbe.create(WSMessage::class.java, system)
        val probe2 = org.apache.pekko.actor.testkit.typed.javadsl.TestProbe.create(WSMessage::class.java, system)
        
        wsActor.tell(WSConnect("conn1", probe1.ref))
        
        // Clear welcome message for conn1
        probe1.expectMessageClass(WSMessage::class.java)
        
        wsActor.tell(WSConnect("conn2", probe2.ref))
        
        // Clear welcome message for conn2 and the join notification for conn1
        probe2.expectMessageClass(WSMessage::class.java)
        probe1.expectMessageClass(WSMessage::class.java) // "User conn2 has joined the chat"
        
        // Send broadcast
        wsActor.tell(WSBroadcast("Test broadcast"))
        
        // Both should receive the broadcast message
        val msg1 = probe1.expectMessageClass(WSMessage::class.java)
        val msg2 = probe2.expectMessageClass(WSMessage::class.java)
        
        msg1.message shouldBe "Test broadcast"
        msg2.message shouldBe "Test broadcast"
    }
    
    @Test
    fun `test route configuration`() = runTest {
        val helloActor = system.systemActorOf(HelloActor.create(), "routeTestHelloActor", org.apache.pekko.actor.typed.Props.empty())
        val eventActor = system.systemActorOf(EventStreamActor.create(), "routeTestEventActor", org.apache.pekko.actor.typed.Props.empty())
        val wsActor = system.systemActorOf(WebSocketActor.create(), "routeTestWsActor", org.apache.pekko.actor.typed.Props.empty())
        
        val helloRoute = HelloRoute(helloActor, system)
        val eventRoute = EventRoute(eventActor, system)
        val wsRoute = WebSocketRoute(wsActor, system)
        val swaggerRoute = SwaggerRoute(system)
        
        // Routes should be created without errors
        helloRoute.createRoute() shouldNotBe null
        eventRoute.createRoute() shouldNotBe null
        wsRoute.createRoute() shouldNotBe null
        swaggerRoute.createRoute() shouldNotBe null
    }
}