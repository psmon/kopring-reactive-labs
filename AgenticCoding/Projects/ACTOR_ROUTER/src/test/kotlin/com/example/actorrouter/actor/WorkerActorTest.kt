package com.example.actorrouter.actor

import com.example.actorrouter.model.*
import kotlinx.coroutines.test.runTest
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkerActorTest {

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
    fun `test worker processes task successfully`() = runTest {
        val routerProbe = testKit.createTestProbe<RouterCommand>()
        val worker = testKit.spawn(WorkerActor.create("test-worker-1", routerProbe.ref))

        // Send work
        worker.tell(DoWork(
            taskId = "task-1",
            payload = "Test payload",
            priority = TaskPriority.NORMAL
        ))

        // Should receive completion
        val completion = routerProbe.expectMessageClass(WorkCompleted::class.java)
        assertEquals("test-worker-1", completion.workerId)
        assertEquals("task-1", completion.taskId)
        assertNotNull(completion.result)
        assertTrue(completion.result.processingTimeMs > 0)
    }

    @Test
    fun `test worker rejects task when busy`() = runTest {
        val routerProbe = testKit.createTestProbe<RouterCommand>()
        val worker = testKit.spawn(WorkerActor.create("busy-worker", routerProbe.ref))

        // Send first task (long running)
        worker.tell(DoWork(
            taskId = "long-task",
            payload = "Long running",
            priority = TaskPriority.LOW // This takes longer
        ))

        // Immediately send second task
        Thread.sleep(50) // Give time for first task to start
        worker.tell(DoWork(
            taskId = "rejected-task",
            payload = "Should be rejected",
            priority = TaskPriority.HIGH
        ))

        // Should receive rejection for second task
        val messages = mutableListOf<WorkCompleted>()
        repeat(2) {
            messages.add(routerProbe.expectMessageClass(WorkCompleted::class.java, Duration.ofSeconds(5)))
        }

        val rejectedTask = messages.find { it.taskId == "rejected-task" }
        assertNotNull(rejectedTask)
        assertFalse(rejectedTask!!.result.success)
        assertEquals("Worker is busy", rejectedTask.result.error)
    }

    @Test
    fun `test worker metrics`() = runTest {
        val routerProbe = testKit.createTestProbe<RouterCommand>()
        val metricsProbe = testKit.createTestProbe<WorkerMetrics>()
        val worker = testKit.spawn(WorkerActor.create("metrics-worker", routerProbe.ref))

        // Get initial metrics
        worker.tell(GetWorkerMetrics(metricsProbe.ref))
        var metrics = metricsProbe.receiveMessage()
        
        assertEquals("metrics-worker", metrics.workerId)
        assertEquals(0, metrics.tasksProcessed)
        assertEquals(0, metrics.currentLoad)
        assertTrue(metrics.isAvailable)

        // Process a task
        worker.tell(DoWork(
            taskId = "metrics-task",
            payload = "For metrics",
            priority = TaskPriority.HIGH
        ))

        // Wait for completion
        routerProbe.expectMessageClass(WorkCompleted::class.java, Duration.ofSeconds(5))

        // Get updated metrics
        worker.tell(GetWorkerMetrics(metricsProbe.ref))
        metrics = metricsProbe.receiveMessage()
        
        assertEquals(1, metrics.tasksProcessed)
        assertEquals(0, metrics.currentLoad) // Should be available again
        assertTrue(metrics.averageProcessingTimeMs > 0)
        assertTrue(metrics.isAvailable)
    }

    @Test
    fun `test worker handles different priorities`() = runTest {
        val routerProbe = testKit.createTestProbe<RouterCommand>()
        val worker = testKit.spawn(WorkerActor.create("priority-worker", routerProbe.ref))

        val priorities = listOf(
            TaskPriority.CRITICAL,
            TaskPriority.HIGH,
            TaskPriority.NORMAL,
            TaskPriority.LOW
        )

        val results = mutableListOf<WorkCompleted>()

        priorities.forEach { priority ->
            worker.tell(DoWork(
                taskId = "priority-${priority.name}",
                payload = "Priority test",
                priority = priority
            ))
            
            results.add(routerProbe.expectMessageClass(WorkCompleted::class.java, Duration.ofSeconds(5)))
        }

        // Critical tasks should complete faster than low priority
        val criticalTime = results.find { it.taskId.contains("CRITICAL") }?.result?.processingTimeMs ?: 0
        val lowTime = results.find { it.taskId.contains("LOW") }?.result?.processingTimeMs ?: 0
        
        assertTrue(criticalTime < lowTime)
    }

    @Test
    fun `test worker shutdown`() = runTest {
        val routerProbe = testKit.createTestProbe<RouterCommand>()
        val metricsProbe = testKit.createTestProbe<WorkerMetrics>()
        val worker = testKit.spawn(WorkerActor.create("shutdown-worker", routerProbe.ref))

        // Process a task first
        worker.tell(DoWork(
            taskId = "before-shutdown",
            payload = "Process before shutdown",
            priority = TaskPriority.NORMAL
        ))
        
        routerProbe.expectMessageClass(WorkCompleted::class.java, Duration.ofSeconds(5))

        // Shutdown worker
        worker.tell(ShutdownWorker)

        // Give time for shutdown
        Thread.sleep(500)

        // Worker should not respond after shutdown
        worker.tell(GetWorkerMetrics(metricsProbe.ref))
        metricsProbe.expectNoMessage(Duration.ofSeconds(1))
    }

    @Test
    fun `test worker handles task failures`() = runTest {
        val routerProbe = testKit.createTestProbe<RouterCommand>()
        val worker = testKit.spawn(WorkerActor.create("failure-test-worker", routerProbe.ref))

        // Send multiple tasks to increase chance of random failure
        repeat(30) { i ->
            worker.tell(DoWork(
                taskId = "failure-test-$i",
                payload = "May fail randomly",
                priority = TaskPriority.NORMAL
            ))
            
            val completion = routerProbe.expectMessageClass(WorkCompleted::class.java, Duration.ofSeconds(5))
            
            // Either success or specific failure
            if (!completion.result.success) {
                assertEquals("Random processing failure", completion.result.error)
            }
        }
    }
}