package com.example.persistdurablecluster.actor

import com.example.persistdurablecluster.model.*
import com.example.persistdurablecluster.sharding.UserShardingManager
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.Join
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClusterUserStateActorTest {
    
    private lateinit var nodeA: ActorTestKit
    private lateinit var nodeB: ActorTestKit
    private lateinit var shardingManagerA: UserShardingManager
    private lateinit var shardingManagerB: UserShardingManager
    
    @BeforeAll
    fun setup() {
        // Start PostgreSQL container before tests
        println("Please ensure PostgreSQL is running via docker-compose up")
        
        // Setup cluster nodes
        val clusterConfigA = ConfigFactory.load("cluster1.conf")
        val clusterConfigB = ConfigFactory.load("cluster2.conf")
        
        nodeA = ActorTestKit.create("ClusterSystem", clusterConfigA)
        nodeB = ActorTestKit.create("ClusterSystem", clusterConfigB)
        
        // Initialize clusters
        val clusterA = Cluster.get(nodeA.system())
        val clusterB = Cluster.get(nodeB.system())
        
        // Join cluster
        clusterA.manager().tell(Join.create(clusterA.selfMember().address()))
        //Thread.sleep(2000)
        clusterB.manager().tell(Join.create(clusterA.selfMember().address()))
        
        // Wait for cluster to form
        //Thread.sleep(5000)
        
        // Initialize sharding managers
        shardingManagerA = UserShardingManager(nodeA.system())
        shardingManagerB = UserShardingManager(nodeB.system())
        
        // Wait for sharding to initialize
        //Thread.sleep(3000)
    }
    
    @AfterAll
    fun teardown() {
        nodeB.shutdownTestKit()
        nodeA.shutdownTestKit()
    }
    
    @Test
    fun `test user state distribution across cluster nodes`() {
        val probeA = nodeA.createTestProbe<UserResponse>()
        val probeB = nodeB.createTestProbe<UserResponse>()
        val stateProbeA = nodeA.createTestProbe<UserStateResponse>()
        val stateProbeB = nodeB.createTestProbe<UserStateResponse>()
        
        // Create multiple users across different malls
        val testData = listOf(
            Triple("mall001", "user001", probeA),
            Triple("mall001", "user002", probeB),
            Triple("mall002", "user001", probeA),
            Triple("mall002", "user002", probeB)
        )
        
        // Send commands from different nodes
        testData.forEach { (mallId, userId, probe) ->
            val manager = if (probe == probeA) shardingManagerA else shardingManagerB
            
            // User login
            manager.sendCommand(mallId, userId, UserLogin(probe.ref()))
            val response = probe.expectMessageClass(ActionCompleted::class.java, Duration.ofSeconds(10))
            assertEquals("login", response.action)
        }
        
        // Verify state persistence across nodes
        testData.forEach { (mallId, userId, _) ->
            // Query from node A
            shardingManagerA.sendCommand(mallId, userId, GetUserState(stateProbeA.ref()))
            val stateFromA = stateProbeA.expectMessageClass(UserStateResponse::class.java, Duration.ofSeconds(5))
            assertNotNull(stateFromA.state.lastLogin)
            assertEquals(mallId, stateFromA.state.mallId)
            assertEquals(userId, stateFromA.state.userId)
            
            // Query same entity from node B
            shardingManagerB.sendCommand(mallId, userId, GetUserState(stateProbeB.ref()))
            val stateFromB = stateProbeB.expectMessageClass(UserStateResponse::class.java, Duration.ofSeconds(5))
            
            // Both nodes should return the same state
            assertEquals(stateFromA.state.lastLogin, stateFromB.state.lastLogin)
            assertEquals(stateFromA.state.mallId, stateFromB.state.mallId)
            assertEquals(stateFromA.state.userId, stateFromB.state.userId)
        }
    }
    
    @Test
    fun `test concurrent operations on same entity from different nodes`() {
        val stateProbe = nodeA.createTestProbe<UserStateResponse>()
        
        val mallId = "mall003"
        val userId = "user003"
        
        // Send concurrent commands from both nodes without expecting responses
        val products = listOf("product1", "product2", "product3", "product4", "product5")
        
        products.forEachIndexed { index, productId ->
            val manager = if (index % 2 == 0) shardingManagerA else shardingManagerB
            manager.sendCommand(mallId, userId, ViewProduct(productId, null))
        }
        
        // Wait for commands to be processed
        //Thread.sleep(2000)
        
        // Verify final state
        shardingManagerA.sendCommand(mallId, userId, GetUserState(stateProbe.ref()))
        val finalState = stateProbe.expectMessageClass(UserStateResponse::class.java, Duration.ofSeconds(5))
        
        // Should have 3 unique products
        assertEquals(3, finalState.state.recentProducts.size)
        // Verify that we have 3 products from our list
        val allProducts = setOf("product1", "product2", "product3", "product4", "product5")
        assertTrue(finalState.state.recentProducts.all { it in allProducts })
        // Most recent products should include product5 (last one sent)
        assertTrue(finalState.state.recentProducts.contains("product5"))
    }
    
    @Test
    fun `test entity persistence and recovery`() {
        val stateProbeA = nodeA.createTestProbe<UserStateResponse>()
        val stateProbeB = nodeB.createTestProbe<UserStateResponse>()
        
        val mallId = "mall004"
        val userId = "user004"
        
        // Create and modify entity without expecting responses
        shardingManagerA.sendCommand(mallId, userId, UserLogin(null))
        //Thread.sleep(500)
        
        shardingManagerA.sendCommand(mallId, userId, ViewProduct("product1", null))
        //Thread.sleep(500)
        
        shardingManagerA.sendCommand(mallId, userId, SetMarketingOptIn(true, null))
        //Thread.sleep(500)
        
        // Get state before passivation
        shardingManagerA.sendCommand(mallId, userId, GetUserState(stateProbeA.ref()))
        val stateBefore = stateProbeA.expectMessageClass(UserStateResponse::class.java, Duration.ofSeconds(5))
        
        // Force entity passivation by waiting (simulated)
        //Thread.sleep(2000)
        
        // Access entity again from different node - should recover from persistence
        shardingManagerB.sendCommand(mallId, userId, GetUserState(stateProbeB.ref()))
        val stateAfter = stateProbeB.expectMessageClass(UserStateResponse::class.java, Duration.ofSeconds(5))
        
        // Verify state was persisted and recovered
        assertEquals(stateBefore.state.lastLogin, stateAfter.state.lastLogin)
        assertEquals(stateBefore.state.recentProducts, stateAfter.state.recentProducts)
        assertEquals(stateBefore.state.marketingOptIn, stateAfter.state.marketingOptIn)
        assertTrue(stateAfter.state.marketingOptIn)
    }
    
    @Test
    fun `test sharding distribution with multiple entities`() {
        val stateProbeA = nodeA.createTestProbe<UserStateResponse>()
        val stateProbeB = nodeB.createTestProbe<UserStateResponse>()
        
        // Create many entities to test distribution
        val entities = (1..20).flatMap { mallNum ->
            (1..5).map { userNum ->
                Pair("mall${String.format("%03d", mallNum)}", "user${String.format("%03d", userNum)}")
            }
        }
        
        // Send commands to all entities without expecting responses
        entities.forEach { (mallId, userId) ->
            // Alternate between nodes for sending commands
            val manager = if ((mallId.hashCode() + userId.hashCode()) % 2 == 0) {
                shardingManagerA
            } else {
                shardingManagerB
            }
            
            manager.sendCommand(mallId, userId, UserLogin(null))
        }
        
        // Wait for all commands to be processed
        //Thread.sleep(5000)
        
        // Sample check some entities from both nodes
        val sampleEntities = entities.shuffled().take(5)
        
        sampleEntities.forEach { (mallId, userId) ->
            // Use the appropriate probe based on which node we're querying from
            val useNodeB = (mallId.hashCode() + userId.hashCode()) % 2 == 0
            if (useNodeB) {
                shardingManagerB.sendCommand(mallId, userId, GetUserState(stateProbeB.ref()))
                val state = stateProbeB.expectMessageClass(UserStateResponse::class.java, Duration.ofSeconds(5))
                
                assertNotNull(state.state.lastLogin)
                assertEquals(mallId, state.state.mallId)
                assertEquals(userId, state.state.userId)
            } else {
                shardingManagerA.sendCommand(mallId, userId, GetUserState(stateProbeA.ref()))
                val state = stateProbeA.expectMessageClass(UserStateResponse::class.java, Duration.ofSeconds(5))
                
                assertNotNull(state.state.lastLogin)
                assertEquals(mallId, state.state.mallId)
                assertEquals(userId, state.state.userId)
            }
        }
    }
    
    @Test
    fun `test cluster status reporting`() {
        val statusA = shardingManagerA.getClusterStatus()
        val statusB = shardingManagerB.getClusterStatus()
        
        // Both nodes should see 2 members
        assertEquals(2, statusA.numberOfMembers)
        assertEquals(2, statusB.numberOfMembers)
        
        // Check roles
        assertTrue(statusA.selfRoles.contains("shard"))
        assertTrue(statusB.selfRoles.contains("shard"))
        
        // Node A should have seed role
        assertTrue(statusA.selfRoles.contains("seed"))
        assertFalse(statusB.selfRoles.contains("seed"))
    }
    
    @Test
    fun `test marketing opt-in synchronization across nodes`() {
        val stateProbeA = nodeA.createTestProbe<UserStateResponse>()
        val stateProbeB = nodeB.createTestProbe<UserStateResponse>()
        
        val mallId = "mall005"
        val userId = "user005"
        
        // First create the entity by logging in
        shardingManagerA.sendCommand(mallId, userId, UserLogin(null))
        //Thread.sleep(1000)
        
        // Set opt-in from node A without expecting response
        shardingManagerA.sendCommand(mallId, userId, SetMarketingOptIn(true, null))
        //Thread.sleep(1000)
        
        // Verify from node B
        shardingManagerB.sendCommand(mallId, userId, GetUserState(stateProbeB.ref()))
        val stateFromB = stateProbeB.expectMessageClass(UserStateResponse::class.java, Duration.ofSeconds(5))
        assertTrue(stateFromB.state.marketingOptIn)
        
        // Change opt-in from node B without expecting response
        shardingManagerB.sendCommand(mallId, userId, SetMarketingOptIn(false, null))
        Thread.sleep(1000)
        
        // Verify from node A
        shardingManagerA.sendCommand(mallId, userId, GetUserState(stateProbeA.ref()))
        val stateFromA = stateProbeA.expectMessageClass(UserStateResponse::class.java, Duration.ofSeconds(5))
        assertFalse(stateFromA.state.marketingOptIn)
    }
    
    @Test
    fun `test cart usage tracking across cluster`() {
        val stateProbe = nodeB.createTestProbe<UserStateResponse>()
        
        val mallId = "mall006"
        val userId = "user006"
        
        // Use cart from different nodes without expecting responses
        shardingManagerA.sendCommand(mallId, userId, UseCart(null))
        //Thread.sleep(1000)
        
        shardingManagerB.sendCommand(mallId, userId, UseCart(null))
        //Thread.sleep(1000)
        
        // Verify state
        shardingManagerB.sendCommand(mallId, userId, GetUserState(stateProbe.ref()))
        val state = stateProbe.expectMessageClass(UserStateResponse::class.java, Duration.ofSeconds(5))
        
        assertNotNull(state.state.lastCartUsedTime)
    }
}