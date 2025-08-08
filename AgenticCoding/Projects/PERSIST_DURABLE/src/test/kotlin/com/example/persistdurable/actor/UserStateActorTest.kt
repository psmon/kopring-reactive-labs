package com.example.persistdurable.actor

import com.example.persistdurable.model.*
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserStateActorTest {

    private lateinit var testKit: ActorTestKit

    @BeforeAll
    fun setup() {
        val config = com.typesafe.config.ConfigFactory.load("application-test.conf")
        testKit = ActorTestKit.create(config)
    }

    @AfterAll
    fun teardown() {
        testKit.shutdownTestKit()
    }

    @Test
    fun `test user login updates last login time`() {
        val probe = testKit.createTestProbe<UserResponse>()
        val stateProbe = testKit.createTestProbe<UserStateResponse>()
        
        val mallId = "mall201"
        val userId = "user201"
        val actor = testKit.spawn(UserStateActor.create(mallId, userId))
        
        // Initial state check
        actor.tell(GetUserState(stateProbe.ref()))
        val initialState = stateProbe.expectMessageClass(UserStateResponse::class.java).state
        val initialLoginTime = initialState.lastLogin
        
        // Perform login
        actor.tell(UserLogin(probe.ref()))
        val response = probe.expectMessageClass(ActionCompleted::class.java)
        assertEquals("login", response.action)
        
        // Verify state after login
        actor.tell(GetUserState(stateProbe.ref()))
        val updatedState = stateProbe.expectMessageClass(UserStateResponse::class.java).state
        assertNotNull(updatedState.lastLogin)
        
        // If there was an initial login time, verify it was updated
        if (initialLoginTime != null) {
            assertTrue(updatedState.lastLogin!!.isAfter(initialLoginTime))
        } else {
            // If no initial login time, verify it was set
            assertTrue(updatedState.lastLogin!!.isAfter(LocalDateTime.now().minusMinutes(1)))
        }
    }

    @Test
    fun `test cart usage updates last cart used time`() {
        val probe = testKit.createTestProbe<UserResponse>()
        val stateProbe = testKit.createTestProbe<UserStateResponse>()
        
        val mallId = "mall001"
        val userId = "user002"
        val actor = testKit.spawn(UserStateActor.create(mallId, userId))
        
        // Use cart
        actor.tell(UseCart(probe.ref()))
        val response = probe.expectMessageClass(ActionCompleted::class.java)
        assertEquals("cart_used", response.action)
        
        // Verify state
        actor.tell(GetUserState(stateProbe.ref()))
        val state = stateProbe.expectMessageClass(UserStateResponse::class.java).state
        assertNotNull(state.lastCartUsedTime)
    }

    @Test
    fun `test viewing products updates recent products list`() {
        val probe = testKit.createTestProbe<UserResponse>()
        val stateProbe = testKit.createTestProbe<UserStateResponse>()
        
        val mallId = "mall001"
        val userId = "user003"
        val actor = testKit.spawn(UserStateActor.create(mallId, userId))
        
        // View multiple products
        val products = listOf("product1", "product2", "product3", "product4")
        products.forEach { productId ->
            actor.tell(ViewProduct(productId, probe.ref()))
            probe.expectMessageClass(ActionCompleted::class.java)
        }
        
        // Verify state - should keep only last 3 products
        actor.tell(GetUserState(stateProbe.ref()))
        val state = stateProbe.expectMessageClass(UserStateResponse::class.java).state
        assertEquals(3, state.recentProducts.size)
        assertTrue(state.recentProducts.contains("product4"))
        assertTrue(state.recentProducts.contains("product3"))
        assertTrue(state.recentProducts.contains("product2"))
        assertFalse(state.recentProducts.contains("product1"))
    }

    @Test
    fun `test marketing opt-in setting`() {
        val probe = testKit.createTestProbe<UserResponse>()
        val stateProbe = testKit.createTestProbe<UserStateResponse>()
        
        val mallId = "mall101"
        val userId = "user104"
        val actor = testKit.spawn(UserStateActor.create(mallId, userId))
        
        // Wait a bit for actor to initialize
        Thread.sleep(100)
        
        // Initial state - opt-in should be false
        actor.tell(GetUserState(stateProbe.ref()))
        val initialState = stateProbe.expectMessageClass(UserStateResponse::class.java, Duration.ofSeconds(5)).state
        assertFalse(initialState.marketingOptIn)
        
        // Set opt-in to true
        actor.tell(SetMarketingOptIn(true, probe.ref()))
        probe.expectMessageClass(ActionCompleted::class.java)
        
        // Verify state
        actor.tell(GetUserState(stateProbe.ref()))
        val updatedState = stateProbe.expectMessageClass(UserStateResponse::class.java).state
        assertTrue(updatedState.marketingOptIn)
        
        // Set opt-in to false
        actor.tell(SetMarketingOptIn(false, probe.ref()))
        probe.expectMessageClass(ActionCompleted::class.java)
        
        // Verify state again
        actor.tell(GetUserState(stateProbe.ref()))
        val finalState = stateProbe.expectMessageClass(UserStateResponse::class.java).state
        assertFalse(finalState.marketingOptIn)
    }

    @Test
    fun `test actor persistence across restarts`() {
        val probe = testKit.createTestProbe<UserResponse>()
        val stateProbe = testKit.createTestProbe<UserStateResponse>()
        
        val mallId = "mall002"
        val userId = "user005"
        
        // Create first actor instance
        val actor1 = testKit.spawn(UserStateActor.create(mallId, userId))
        
        // Perform some actions
        actor1.tell(UserLogin(probe.ref()))
        probe.expectMessageClass(ActionCompleted::class.java)
        
        actor1.tell(ViewProduct("product1", probe.ref()))
        probe.expectMessageClass(ActionCompleted::class.java)
        
        actor1.tell(SetMarketingOptIn(true, probe.ref()))
        probe.expectMessageClass(ActionCompleted::class.java)
        
        // Get state before stopping
        actor1.tell(GetUserState(stateProbe.ref()))
        val stateBefore = stateProbe.expectMessageClass(UserStateResponse::class.java).state
        
        // Stop the actor
        testKit.stop(actor1)
        
        // Create new actor instance with same persistence id
        val actor2 = testKit.spawn(UserStateActor.create(mallId, userId))
        
        // Get state after restart
        actor2.tell(GetUserState(stateProbe.ref()))
        val stateAfter = stateProbe.expectMessageClass(UserStateResponse::class.java).state
        
        // Verify state was persisted
        assertEquals(stateBefore.lastLogin, stateAfter.lastLogin)
        assertEquals(stateBefore.recentProducts, stateAfter.recentProducts)
        assertEquals(stateBefore.marketingOptIn, stateAfter.marketingOptIn)
    }

    // @Test - Commented out as it requires ManualTime which is not compatible with persistence
    // fun `test actor auto-shutdown after 30 minutes of inactivity`() {
    //     // This test would require ManualTime which conflicts with persistence testing
    //     // In production, the actor will shut down after 30 minutes of inactivity
    // }

    @Test
    fun `test actor stays alive with regular activity`() {
        val probe = testKit.createTestProbe<UserResponse>()
        val stateProbe = testKit.createTestProbe<UserStateResponse>()
        
        val mallId = "mall004"
        val userId = "user007"
        val actor = testKit.spawn(UserStateActor.create(mallId, userId))
        
        // Simulate multiple activities
        repeat(6) { i ->
            actor.tell(ViewProduct("product$i", probe.ref()))
            probe.expectMessageClass(ActionCompleted::class.java)
        }
        
        // Actor should still be alive
        actor.tell(GetUserState(stateProbe.ref()))
        val state = stateProbe.expectMessageClass(UserStateResponse::class.java).state
        assertNotNull(state)
    }

    @Test
    fun `test concurrent user operations`() {
        val probes = (1..10).map { testKit.createTestProbe<UserResponse>() }
        val stateProbe = testKit.createTestProbe<UserStateResponse>()
        
        val mallId = "mall305"
        val actors = (1..10).map { i ->
            testKit.spawn(UserStateActor.create(mallId, "user${300 + i}"))
        }
        
        // Each actor performs multiple operations
        actors.forEachIndexed { index, actor ->
            val probe = probes[index]
            
            // Login
            actor.tell(UserLogin(probe.ref()))
            
            // View products
            (1..3).forEach { productNum ->
                actor.tell(ViewProduct("product$productNum", probe.ref()))
            }
            
            // Use cart
            actor.tell(UseCart(probe.ref()))
            
            // Set marketing opt-in
            actor.tell(SetMarketingOptIn((index + 1) % 2 == 0, probe.ref()))
        }
        
        // Verify all responses
        probes.forEach { probe ->
            // 1 login + 3 products + 1 cart + 1 marketing = 6 responses
            repeat(6) {
                probe.expectMessageClass(ActionCompleted::class.java)
            }
        }
        
        // Verify each actor maintains its own state
        actors.forEachIndexed { index, actor ->
            actor.tell(GetUserState(stateProbe.ref()))
            val state = stateProbe.expectMessageClass(UserStateResponse::class.java).state
            
            assertEquals("mall305", state.mallId)
            assertEquals("user${300 + index + 1}", state.userId)
            assertNotNull(state.lastLogin)
            assertNotNull(state.lastCartUsedTime)
            assertEquals(3, state.recentProducts.size)
            assertEquals((index + 1) % 2 == 0, state.marketingOptIn)
        }
    }

    @Test
    fun `test unique product tracking in recent products`() {
        val probe = testKit.createTestProbe<UserResponse>()
        val stateProbe = testKit.createTestProbe<UserStateResponse>()
        
        val mallId = "mall006"
        val userId = "user008"
        val actor = testKit.spawn(UserStateActor.create(mallId, userId))
        
        // View same product multiple times
        repeat(3) {
            actor.tell(ViewProduct("product1", probe.ref()))
            probe.expectMessageClass(ActionCompleted::class.java)
        }
        
        // View different products
        actor.tell(ViewProduct("product2", probe.ref()))
        probe.expectMessageClass(ActionCompleted::class.java)
        
        actor.tell(ViewProduct("product3", probe.ref()))
        probe.expectMessageClass(ActionCompleted::class.java)
        
        // Check state
        actor.tell(GetUserState(stateProbe.ref()))
        val state = stateProbe.expectMessageClass(UserStateResponse::class.java).state
        
        // Should have 3 unique products
        assertEquals(3, state.recentProducts.size)
        assertEquals(state.recentProducts.toSet().size, state.recentProducts.size) // All unique
    }

    @Test
    fun `test state consistency after multiple updates`() {
        val probe = testKit.createTestProbe<UserResponse>()
        val stateProbe = testKit.createTestProbe<UserStateResponse>()
        
        val mallId = "mall107"
        val userId = "user109"
        val actor = testKit.spawn(UserStateActor.create(mallId, userId))
        
        // Wait for actor to initialize and establish DB connection
        Thread.sleep(500)
        
        // Perform multiple operations
        val operations = listOf(
            UserLogin(probe.ref()),
            ViewProduct("product1", probe.ref()),
            UseCart(probe.ref()),
            ViewProduct("product2", probe.ref()),
            SetMarketingOptIn(true, probe.ref()),
            ViewProduct("product3", probe.ref()),
            UseCart(probe.ref()),
            ViewProduct("product4", probe.ref())
        )
        
        operations.forEach { operation ->
            actor.tell(operation)
            probe.expectMessageClass(ActionCompleted::class.java, Duration.ofSeconds(5))
        }
        
        // Verify final state
        actor.tell(GetUserState(stateProbe.ref()))
        val state = stateProbe.expectMessageClass(UserStateResponse::class.java, Duration.ofSeconds(5)).state
        
        assertNotNull(state.lastLogin)
        assertNotNull(state.lastCartUsedTime)
        assertTrue(state.marketingOptIn)
        assertEquals(3, state.recentProducts.size)
        assertTrue(state.recentProducts.contains("product4"))
        assertTrue(state.recentProducts.contains("product3"))
        assertTrue(state.recentProducts.contains("product2"))
    }
}