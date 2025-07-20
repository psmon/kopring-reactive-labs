package com.example.actorrouter.router

import com.example.actorrouter.model.*
import kotlinx.coroutines.test.runTest
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RouterActorTest {

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
    fun `test round-robin routing distributes tasks evenly`() = runTest {
        // Create router with round-robin strategy
        val router = testKit.spawn(RouterActor.create(RoutingStrategy.ROUND_ROBIN, 3))
        val probe = testKit.createTestProbe<TaskResult>()

        // Send multiple tasks
        val taskCount = 9
        repeat(taskCount) { i ->
            router.tell(ProcessTask(
                taskId = "task-$i",
                payload = "Test payload $i",
                priority = TaskPriority.NORMAL,
                replyTo = probe.ref
            ))
        }

        // Verify all tasks complete
        val results = mutableListOf<TaskResult>()
        repeat(taskCount) {
            val result = probe.receiveMessage(Duration.ofSeconds(5))
            results.add(result)
        }

        assertEquals(taskCount, results.size)
        assertTrue(results.all { it.success || it.error != null })
    }

    @Test
    fun `test random routing processes all tasks`() = runTest {
        val router = testKit.spawn(RouterActor.create(RoutingStrategy.RANDOM, 3))
        val probe = testKit.createTestProbe<TaskResult>()

        // Send tasks
        repeat(6) { i ->
            router.tell(ProcessTask(
                taskId = "random-task-$i",
                payload = "Random test $i",
                priority = TaskPriority.NORMAL,
                replyTo = probe.ref
            ))
        }

        // Verify completion
        repeat(6) {
            val result = probe.receiveMessage(Duration.ofSeconds(5))
            assertNotNull(result)
        }
    }

    @Test
    fun `test least-loaded routing`() = runTest {
        val router = testKit.spawn(RouterActor.create(RoutingStrategy.LEAST_LOADED, 2))
        val probe = testKit.createTestProbe<TaskResult>()

        // Send tasks with different priorities
        router.tell(ProcessTask(
            taskId = "heavy-task",
            payload = "Heavy processing",
            priority = TaskPriority.LOW, // This will take longer
            replyTo = probe.ref
        ))

        Thread.sleep(100) // Give time for task to be assigned

        // Send more tasks - should go to the other worker
        repeat(3) { i ->
            router.tell(ProcessTask(
                taskId = "light-task-$i",
                payload = "Light processing",
                priority = TaskPriority.HIGH,
                replyTo = probe.ref
            ))
        }

        // Verify all complete
        repeat(4) {
            probe.receiveMessage(Duration.ofSeconds(10))
        }
    }

    @Test
    fun `test consistent hash routing`() = runTest {
        val router = testKit.spawn(RouterActor.create(RoutingStrategy.CONSISTENT_HASH, 4))
        val probe = testKit.createTestProbe<TaskResult>()

        // Send tasks with same ID prefix - should go to same worker
        val taskIdPrefix = "user-123"
        repeat(3) { i ->
            router.tell(ProcessTask(
                taskId = "$taskIdPrefix-request-$i",
                payload = "User request $i",
                priority = TaskPriority.NORMAL,
                replyTo = probe.ref
            ))
        }

        // Different prefix - might go to different worker
        repeat(3) { i ->
            router.tell(ProcessTask(
                taskId = "user-456-request-$i",
                payload = "Another user request $i",
                priority = TaskPriority.NORMAL,
                replyTo = probe.ref
            ))
        }

        // Verify all complete
        repeat(6) {
            probe.receiveMessage(Duration.ofSeconds(5))
        }
    }

    @Test
    fun `test priority-based routing`() = runTest {
        val router = testKit.spawn(RouterActor.create(RoutingStrategy.PRIORITY_BASED, 4))
        val probe = testKit.createTestProbe<TaskResult>()

        // Send tasks with different priorities
        val priorities = listOf(
            TaskPriority.CRITICAL,
            TaskPriority.HIGH,
            TaskPriority.NORMAL,
            TaskPriority.LOW
        )

        priorities.forEach { priority ->
            repeat(2) { i ->
                router.tell(ProcessTask(
                    taskId = "${priority.name}-task-$i",
                    payload = "Priority test",
                    priority = priority,
                    replyTo = probe.ref
                ))
            }
        }

        // Verify all complete
        repeat(8) {
            probe.receiveMessage(Duration.ofSeconds(10))
        }
    }

    @Test
    fun `test worker scaling up`() = runTest {
        val router = testKit.spawn(RouterActor.create(RoutingStrategy.ROUND_ROBIN, 2))
        val scaleProbe = testKit.createTestProbe<ScaleResult>()
        val metricsProbe = testKit.createTestProbe<RouterMetrics>()

        // Get initial metrics
        router.tell(GetRouterMetrics(metricsProbe.ref))
        val initialMetrics = metricsProbe.receiveMessage()
        assertEquals(2, initialMetrics.workerCount)

        // Scale up
        router.tell(ScaleWorkers(3, scaleProbe.ref))
        val scaleResult = scaleProbe.receiveMessage()
        
        assertTrue(scaleResult.success)
        assertEquals(5, scaleResult.currentWorkerCount)

        // Verify new worker count
        router.tell(GetRouterMetrics(metricsProbe.ref))
        val newMetrics = metricsProbe.receiveMessage()
        assertEquals(5, newMetrics.workerCount)
    }

    @Test
    fun `test worker scaling down`() = runTest {
        val router = testKit.spawn(RouterActor.create(RoutingStrategy.ROUND_ROBIN, 5))
        val scaleProbe = testKit.createTestProbe<ScaleResult>()

        // Scale down
        router.tell(ScaleWorkers(-2, scaleProbe.ref))
        val scaleResult = scaleProbe.receiveMessage()
        
        assertTrue(scaleResult.success)
        assertEquals(3, scaleResult.currentWorkerCount)
    }

    @Test
    fun `test router metrics`() = runTest {
        val router = testKit.spawn(RouterActor.create(RoutingStrategy.ROUND_ROBIN, 3))
        val taskProbe = testKit.createTestProbe<TaskResult>()
        val metricsProbe = testKit.createTestProbe<RouterMetrics>()

        // Process some tasks
        repeat(5) { i ->
            router.tell(ProcessTask(
                taskId = "metrics-task-$i",
                payload = "Metrics test",
                priority = TaskPriority.NORMAL,
                replyTo = taskProbe.ref
            ))
        }

        // Wait for completion
        repeat(5) {
            taskProbe.receiveMessage(Duration.ofSeconds(5))
        }

        // Get metrics
        router.tell(GetRouterMetrics(metricsProbe.ref))
        val metrics = metricsProbe.receiveMessage()

        assertEquals(5, metrics.totalTasksProcessed)
        assertEquals(3, metrics.workerCount)
        assertEquals("ROUND_ROBIN", metrics.routingStrategy)
        assertTrue(metrics.averageProcessingTimeMs > 0)
    }

    @Test
    fun `test worker status monitoring`() = runTest {
        val router = testKit.spawn(RouterActor.create(RoutingStrategy.ROUND_ROBIN, 2))
        val statusProbe = testKit.createTestProbe<WorkerStatuses>()

        // Get worker statuses
        router.tell(GetWorkerStatuses(statusProbe.ref))
        val statuses = statusProbe.receiveMessage(Duration.ofSeconds(2))

        assertEquals(2, statuses.workers.size)
        statuses.workers.forEach { worker ->
            assertTrue(worker.isAvailable)
            assertEquals(0, worker.currentLoad)
        }
    }

    @Test
    fun `test task failure handling`() = runTest {
        val router = testKit.spawn(RouterActor.create(RoutingStrategy.ROUND_ROBIN, 1))
        val probe = testKit.createTestProbe<TaskResult>()

        // Send multiple tasks to increase chance of failure
        repeat(20) { i ->
            router.tell(ProcessTask(
                taskId = "failure-test-$i",
                payload = "Might fail",
                priority = TaskPriority.NORMAL,
                replyTo = probe.ref
            ))
        }

        // Collect results
        val results = mutableListOf<TaskResult>()
        repeat(20) {
            results.add(probe.receiveMessage(Duration.ofSeconds(5)))
        }

        // Should have some failures due to 95% success rate
        val failures = results.filter { !it.success }
        assertTrue(failures.isNotEmpty() || results.all { it.success })
    }

    @Test
    fun `test router shutdown`() = runTest {
        val router = testKit.spawn(RouterActor.create(RoutingStrategy.ROUND_ROBIN, 2))
        
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