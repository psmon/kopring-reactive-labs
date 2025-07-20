package com.example.actorrouter.router

import com.example.actorrouter.model.*
import kotlinx.coroutines.test.runTest
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BroadcastRouterActorTest {

    private lateinit var testKit: ActorTestKit

    @BeforeAll
    fun beforeAll() {
        testKit = ActorTestKit.create()
    }

    @AfterAll
    fun afterAll() {
        testKit.shutdownTestKit()
    }

    @Test
    fun `test broadcast sends task to all workers`() = runTest {
        // Create broadcast router with 3 workers
        val router = testKit.spawn(BroadcastRouterActor.create(3))
        val probe = testKit.createTestProbe<TaskResult>()

        // Send a task
        router.tell(ProcessTask(
            taskId = "broadcast-task-1",
            payload = "Broadcast test",
            priority = TaskPriority.NORMAL,
            replyTo = probe.ref
        ))

        // Should receive aggregated result
        val result = probe.receiveMessage(Duration.ofSeconds(5))
        
        assertNotNull(result)
        assertTrue(result.success)
        assertTrue(result.result?.contains("3 workers") == true)
    }

    @Test
    fun `test broadcast aggregates results from all workers`() = runTest {
        val router = testKit.spawn(BroadcastRouterActor.create(5))
        val probe = testKit.createTestProbe<TaskResult>()

        // Send multiple broadcast tasks with delay to ensure workers are free
        repeat(3) { i ->
            router.tell(ProcessTask(
                taskId = "broadcast-aggregate-$i",
                payload = "Aggregate test $i",
                priority = TaskPriority.HIGH,
                replyTo = probe.ref
            ))
            
            // Wait for this broadcast to complete before sending next
            if (i < 2) {
                val result = probe.receiveMessage(Duration.ofSeconds(5))
                assertNotNull(result)
            }
        }

        // Verify the last result
        val lastResult = probe.receiveMessage(Duration.ofSeconds(5))
        assertNotNull(lastResult)
        assertEquals("broadcast-aggregate-2", lastResult.taskId)
        // The aggregated result should mention all workers
        assertTrue(lastResult.result?.contains("5 workers") == true)
    }

    @Test
    fun `test broadcast handles partial failures`() = runTest {
        val router = testKit.spawn(BroadcastRouterActor.create(10))
        val probe = testKit.createTestProbe<TaskResult>()

        // Send tasks that might have some failures
        router.tell(ProcessTask(
            taskId = "partial-failure-test",
            payload = "May have failures",
            priority = TaskPriority.NORMAL,
            replyTo = probe.ref
        ))

        val result = probe.receiveMessage(Duration.ofSeconds(10))
        
        assertNotNull(result)
        // Result should indicate how many succeeded
        assertTrue(result.result?.contains("workers succeeded") == true)
    }

    @Test
    fun `test broadcast with different priorities`() = runTest {
        val router = testKit.spawn(BroadcastRouterActor.create(4))
        val probe = testKit.createTestProbe<TaskResult>()

        val priorities = listOf(
            TaskPriority.CRITICAL,
            TaskPriority.HIGH,
            TaskPriority.NORMAL,
            TaskPriority.LOW
        )

        val results = mutableListOf<TaskResult>()
        
        priorities.forEach { priority ->
            router.tell(ProcessTask(
                taskId = "broadcast-priority-${priority.name}",
                payload = "Priority broadcast",
                priority = priority,
                replyTo = probe.ref
            ))
            
            // Wait for each broadcast to complete
            val result = probe.receiveMessage(Duration.ofSeconds(10))
            results.add(result)
        }

        // Higher priority tasks should complete faster on average
        val criticalResult = results.find { it.taskId.contains("CRITICAL") }
        val lowResult = results.find { it.taskId.contains("LOW") }
        
        assertNotNull(criticalResult)
        assertNotNull(lowResult)
        assertTrue(criticalResult!!.processingTimeMs < lowResult!!.processingTimeMs)
    }

    @Test
    fun `test broadcast router metrics`() = runTest {
        val router = testKit.spawn(BroadcastRouterActor.create(3))
        val taskProbe = testKit.createTestProbe<TaskResult>()
        val metricsProbe = testKit.createTestProbe<RouterMetrics>()

        // Process some broadcast tasks
        repeat(2) { i ->
            router.tell(ProcessTask(
                taskId = "metrics-broadcast-$i",
                payload = "Metrics test",
                priority = TaskPriority.NORMAL,
                replyTo = taskProbe.ref
            ))
        }

        // Wait for completion
        repeat(2) {
            taskProbe.receiveMessage(Duration.ofSeconds(5))
        }

        // Get metrics
        router.tell(GetRouterMetrics(metricsProbe.ref))
        val metrics = metricsProbe.receiveMessage()

        assertEquals(3, metrics.workerCount)
        assertEquals("BROADCAST", metrics.routingStrategy)
    }

    @Test
    fun `test broadcast router shutdown`() = runTest {
        val router = testKit.spawn(BroadcastRouterActor.create(2))
        
        // Send shutdown
        router.tell(ShutdownRouter)
        
        // Give time for shutdown
        Thread.sleep(500)
        
        // Router should not respond to new requests
        val probe = testKit.createTestProbe<TaskResult>()
        router.tell(ProcessTask(
            taskId = "after-shutdown",
            payload = "Should not process",
            priority = TaskPriority.NORMAL,
            replyTo = probe.ref
        ))
        
        // Should not receive response
        probe.expectNoMessage(Duration.ofSeconds(1))
    }
}