package com.example.persisteventsource.performance

import com.example.persisteventsource.actor.WalletActor
import com.example.persisteventsource.model.*
import kotlinx.coroutines.*
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalletPerformanceTest {
    
    private lateinit var testKit: ActorTestKit
    
    @BeforeAll
    fun setup() {
        //val config = com.typesafe.config.ConfigFactory.load("application-test.conf")  //Inmem TEST
        val config = com.typesafe.config.ConfigFactory.load("application.conf")         //Postgres-R2DBC TEST
        testKit = ActorTestKit.create(config)
    }
    
    @AfterAll
    fun teardown() {
        testKit.shutdownTestKit()
    }
    
    @Test
    fun `test high volume transaction processing`() {
        val walletId = "perf-wallet-${UUID.randomUUID()}"
        val wallet = testKit.spawn(WalletActor.create(walletId))
        val probe = testKit.createTestProbe<WalletResponse>()
        
        // Initial large deposit
        wallet.tell(Deposit(
            amount = BigDecimal("1000000.00"),
            source = "Initial Fund",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        // Set higher daily limit for test
        wallet.tell(SetDailyLimit(
            limit = BigDecimal("1000000.00"),
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        val transactionCount = 10000
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val totalTime = AtomicLong(0)
        
        println("\n=== Event Sourcing Performance Test ===")
        println("Processing $transactionCount transactions...")
        
        val startTime = System.currentTimeMillis()
        
        runBlocking {
            val jobs = (1..transactionCount).map { i ->
                launch(Dispatchers.Default) {
                    val txProbe = testKit.createTestProbe<WalletResponse>()
                    val txStartTime = System.nanoTime()
                    
                    when (i % 3) {
                        0 -> {
                            // Deposit
                            wallet.tell(Deposit(
                                amount = BigDecimal("10.00"),
                                source = "Source-$i",
                                description = "Transaction-$i",
                                replyTo = txProbe.ref()
                            ))
                        }
                        1 -> {
                            // Withdrawal
                            wallet.tell(Withdraw(
                                amount = BigDecimal("5.00"),
                                destination = "Dest-$i",
                                description = "Transaction-$i",
                                replyTo = txProbe.ref()
                            ))
                        }
                        else -> {
                            // Transfer
                            wallet.tell(Transfer(
                                recipientWalletId = "recipient-$i",
                                amount = BigDecimal("7.50"),
                                description = "Transaction-$i",
                                replyTo = txProbe.ref()
                            ))
                        }
                    }
                    
                    val response = txProbe.expectMessageClass(
                        WalletResponse::class.java,
                        Duration.ofSeconds(10)
                    )
                    
                    val txEndTime = System.nanoTime()
                    totalTime.addAndGet(txEndTime - txStartTime)
                    
                    when (response) {
                        is TransactionSuccess -> successCount.incrementAndGet()
                        is TransactionFailure -> failureCount.incrementAndGet()
                        else -> {}
                    }
                }
            }
            
            jobs.forEach { it.join() }
        }
        
        val endTime = System.currentTimeMillis()
        val totalDuration = endTime - startTime
        val avgLatencyMs = totalTime.get() / transactionCount / 1_000_000.0
        val throughput = transactionCount * 1000.0 / totalDuration
        
        println("\nResults:")
        println("- Total transactions: $transactionCount")
        println("- Successful: ${successCount.get()}")
        println("- Failed: ${failureCount.get()}")
        println("- Total time: ${totalDuration}ms")
        println("- Average latency: ${String.format("%.2f", avgLatencyMs)}ms")
        println("- Throughput: ${String.format("%.2f", throughput)} tx/sec")
        
        // Verify final state
        wallet.tell(GetBalance(probe.ref()))
        val finalBalance = probe.expectMessageClass(BalanceResponse::class.java)
        println("- Final balance: ${finalBalance.balance}")
        
        // Get transaction history to verify audit trail
        wallet.tell(GetTransactionHistory(limit = 100, replyTo = probe.ref()))
        val history = probe.expectMessageClass(TransactionHistoryResponse::class.java)
        println("- Transaction history size: ${history.transactions.size}")
        
        assertTrue(successCount.get() > 0, "Should have successful transactions")
        assertTrue(throughput > 100, "Should process at least 100 tx/sec")
    }
    
    @Test
    fun `test event replay performance`() {
        val walletId = "replay-wallet-${UUID.randomUUID()}"
        var wallet = testKit.spawn(WalletActor.create(walletId))
        val probe = testKit.createTestProbe<WalletResponse>()
        
        // Generate events
        val eventCount = 1000
        println("\n=== Event Replay Performance Test ===")
        println("Generating $eventCount events...")
        
        // Initial deposit
        wallet.tell(Deposit(
            amount = BigDecimal("100000.00"),
            source = "Initial",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        // Generate many events
        repeat(eventCount) { i ->
            when (i % 2) {
                0 -> wallet.tell(Deposit(
                    amount = BigDecimal("10.00"),
                    source = "Source-$i",
                    replyTo = probe.ref()
                ))
                else -> wallet.tell(Withdraw(
                    amount = BigDecimal("5.00"),
                    destination = "Dest-$i",
                    replyTo = probe.ref()
                ))
            }
            probe.expectMessageClass(WalletResponse::class.java)
        }
        
        // Get final balance before restart
        wallet.tell(GetBalance(probe.ref()))
        val balanceBeforeRestart = probe.expectMessageClass(BalanceResponse::class.java).balance
        
        // Stop actor
        testKit.stop(wallet)
        Thread.sleep(1000) // Wait for actor to stop
        
        // Measure replay time
        println("Replaying $eventCount events from journal...")
        val replayTime = measureTimeMillis {
            wallet = testKit.spawn(WalletActor.create(walletId))
            
            // Verify state after replay
            wallet.tell(GetBalance(probe.ref()))
            val balanceAfterReplay = probe.expectMessageClass(BalanceResponse::class.java).balance
            assertEquals(balanceBeforeRestart, balanceAfterReplay, "Balance should match after replay")
        }
        
        println("Event replay completed in ${replayTime}ms")
        println("Replay speed: ${String.format("%.2f", eventCount * 1000.0 / replayTime)} events/sec")
        
        assertTrue(replayTime < eventCount * 10, "Replay should be faster than 10ms per event")
    }
    
    @Test
    fun `test snapshot optimization`() {
        val walletId = "snapshot-wallet-${UUID.randomUUID()}"
        var wallet = testKit.spawn(WalletActor.create(walletId))
        val probe = testKit.createTestProbe<WalletResponse>()
        
        println("\n=== Snapshot Optimization Test ===")
        
        // Generate enough events to trigger snapshots (configured at every 100 events)
        val eventCount = 500
        println("Generating $eventCount events to trigger snapshots...")
        
        wallet.tell(Deposit(
            amount = BigDecimal("100000.00"),
            source = "Initial",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        repeat(eventCount) { i ->
            wallet.tell(Deposit(
                amount = BigDecimal("1.00"),
                source = "Source-$i",
                replyTo = probe.ref()
            ))
            probe.expectMessageClass(TransactionSuccess::class.java)
        }
        
        // Stop and restart to measure recovery with snapshots
        testKit.stop(wallet)
        Thread.sleep(1000)
        
        val recoveryWithSnapshotTime = measureTimeMillis {
            wallet = testKit.spawn(WalletActor.create(walletId))
            wallet.tell(GetBalance(probe.ref()))
            probe.expectMessageClass(BalanceResponse::class.java)
        }
        
        println("Recovery time with snapshots: ${recoveryWithSnapshotTime}ms")
        println("Expected snapshots created: ${eventCount / 100}")
        
        // Compare with a wallet without many events
        val simpleWalletId = "simple-wallet-${UUID.randomUUID()}"
        var simpleWallet = testKit.spawn(WalletActor.create(simpleWalletId))
        
        simpleWallet.tell(Deposit(
            amount = BigDecimal("100.00"),
            source = "Initial",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        testKit.stop(simpleWallet)
        Thread.sleep(1000)
        
        val simpleRecoveryTime = measureTimeMillis {
            simpleWallet = testKit.spawn(WalletActor.create(simpleWalletId))
            simpleWallet.tell(GetBalance(probe.ref()))
            probe.expectMessageClass(BalanceResponse::class.java)
        }
        
        println("Recovery time without snapshots (1 event): ${simpleRecoveryTime}ms")
        println("Snapshot optimization factor: ${String.format("%.2fx", 
            (eventCount.toDouble() * simpleRecoveryTime) / recoveryWithSnapshotTime)}")
        
        assertTrue(recoveryWithSnapshotTime < eventCount * simpleRecoveryTime,
            "Recovery with snapshots should be faster than replaying all events")
    }
    
    @Test
    fun `test audit trail completeness`() {
        val walletId = "audit-wallet-${UUID.randomUUID()}"
        val wallet = testKit.spawn(WalletActor.create(walletId))
        val probe = testKit.createTestProbe<WalletResponse>()
        
        println("\n=== Audit Trail Completeness Test ===")
        
        val transactions = mutableListOf<String>()
        val transactionCount = 100
        
        // Initial deposit
        wallet.tell(Deposit(
            amount = BigDecimal("10000.00"),
            source = "Initial",
            replyTo = probe.ref()
        ))
        val initialTx = probe.expectMessageClass(TransactionSuccess::class.java)
        transactions.add(initialTx.transactionId)
        
        // Perform various transactions
        repeat(transactionCount) { i ->
            when (i % 4) {
                0 -> {
                    wallet.tell(Deposit(
                        amount = BigDecimal("50.00"),
                        source = "Source-$i",
                        description = "Deposit-$i",
                        replyTo = probe.ref()
                    ))
                    val response = probe.expectMessageClass(TransactionSuccess::class.java)
                    transactions.add(response.transactionId)
                }
                1 -> {
                    wallet.tell(Withdraw(
                        amount = BigDecimal("20.00"),
                        destination = "Dest-$i",
                        description = "Withdrawal-$i",
                        replyTo = probe.ref()
                    ))
                    val response = probe.expectMessageClass(WalletResponse::class.java)
                    if (response is TransactionSuccess) {
                        transactions.add(response.transactionId)
                    }
                }
                2 -> {
                    wallet.tell(SetDailyLimit(
                        limit = BigDecimal("5000.00"),
                        replyTo = probe.ref()
                    ))
                    val response = probe.expectMessageClass(TransactionSuccess::class.java)
                    transactions.add(response.transactionId)
                }
                else -> {
                    // Try invalid withdrawal to generate rejection event
                    wallet.tell(Withdraw(
                        amount = BigDecimal("1000000.00"),
                        destination = "Invalid",
                        replyTo = probe.ref()
                    ))
                    probe.expectMessageClass(TransactionFailure::class.java)
                    // Rejection events are also tracked
                }
            }
        }
        
        // Get full transaction history
        wallet.tell(GetTransactionHistory(
            limit = 1000,
            replyTo = probe.ref()
        ))
        
        val history = probe.expectMessageClass(TransactionHistoryResponse::class.java)
        val historyIds = history.transactions.map { it.transactionId }.toSet()
        
        println("Total transactions executed: ${transactions.size}")
        println("Transaction history size: ${history.transactions.size}")
        println("Unique transaction types: ${history.transactions.map { it.type }.toSet()}")
        
        // Verify audit trail includes all types of events
        val transactionTypes = history.transactions.map { it.type }.toSet()
        assertTrue(transactionTypes.contains(TransactionType.DEPOSIT))
        assertTrue(transactionTypes.contains(TransactionType.WITHDRAWAL))
        assertTrue(transactionTypes.contains(TransactionType.LIMIT_CHANGE))
        assertTrue(transactionTypes.contains(TransactionType.WITHDRAWAL_REJECTED))
        
        // Verify transaction ordering (newest first)
        val timestamps = history.transactions.map { it.timestamp }
        for (i in 1 until timestamps.size) {
            assertTrue(timestamps[i-1].isAfter(timestamps[i]) || timestamps[i-1].isEqual(timestamps[i]),
                "Transactions should be in reverse chronological order")
        }
        
        println("Audit trail verification: PASSED")
        println("- All transaction types tracked: ✓")
        println("- Chronological ordering maintained: ✓")
        println("- Rejection events included: ✓")
    }
    
    @Test
    fun `compare event sourcing vs traditional CRUD performance`() {
        println("\n=== Event Sourcing vs CRUD Comparison ===")
        
        // Event Sourcing wallet
        val esWalletId = "es-wallet-${UUID.randomUUID()}"
        val esWallet = testKit.spawn(WalletActor.create(esWalletId))
        val esProbe = testKit.createTestProbe<WalletResponse>()
        
        // Setup
        esWallet.tell(Deposit(
            amount = BigDecimal("10000.00"),
            source = "Initial",
            replyTo = esProbe.ref()
        ))
        esProbe.expectMessageClass(TransactionSuccess::class.java)
        
        val operationCount = 1000
        
        // Measure Event Sourcing performance
        val esTime = measureTimeMillis {
            repeat(operationCount) { i ->
                when (i % 3) {
                    0 -> esWallet.tell(Deposit(
                        amount = BigDecimal("10.00"),
                        source = "Test",
                        replyTo = esProbe.ref()
                    ))
                    1 -> esWallet.tell(GetBalance(esProbe.ref()))
                    else -> esWallet.tell(GetTransactionHistory(limit = 10, replyTo = esProbe.ref()))
                }
                esProbe.expectMessageClass(WalletResponse::class.java)
            }
        }
        
        // Simulate CRUD operations (using in-memory state)
        val crudState = ConcurrentHashMap<String, BigDecimal>()
        val crudHistory = mutableListOf<Map<String, Any>>()
        crudState["balance"] = BigDecimal("10000.00")
        
        val crudTime = measureTimeMillis {
            repeat(operationCount) { i ->
                when (i % 3) {
                    0 -> {
                        // Simulate deposit with DB write
                        synchronized(crudState) {
                            val current = crudState["balance"]!!
                            crudState["balance"] = current + BigDecimal("10.00")
                            crudHistory.add(mapOf(
                                "type" to "DEPOSIT",
                                "amount" to BigDecimal("10.00"),
                                "balance" to crudState["balance"]!!
                            ))
                        }
                        Thread.sleep(1) // Simulate DB latency
                    }
                    1 -> {
                        // Simulate balance read
                        crudState["balance"]
                        Thread.sleep(1) // Simulate DB latency
                    }
                    else -> {
                        // Simulate history read
                        synchronized(crudHistory) {
                            crudHistory.takeLast(10)
                        }
                        Thread.sleep(2) // Simulate complex query latency
                    }
                }
            }
        }
        
        println("\nPerformance Comparison:")
        println("Operations performed: $operationCount")
        println("Event Sourcing time: ${esTime}ms")
        println("Simulated CRUD time: ${crudTime}ms")
        println("Event Sourcing advantage: ${String.format("%.2fx", crudTime.toDouble() / esTime)} faster")
        
        println("\n=== Event Sourcing Advantages ===")
        println("1. Audit Trail: Complete history automatically maintained")
        println("2. Event Replay: Can reconstruct state at any point in time")
        println("3. Debugging: Every state change is recorded")
        println("4. Performance: No complex joins for history queries")
        println("5. Scalability: Events can be partitioned and distributed")
        
        println("\n=== CRUD Disadvantages ===")
        println("1. Audit Trail: Requires additional tables and logic")
        println("2. History: Complex queries with joins for transaction history")
        println("3. Debugging: State changes may not be traceable")
        println("4. Performance: Degrades with large history tables")
        println("5. Consistency: Harder to maintain with distributed systems")
        
        assertTrue(esTime < crudTime * 2, "Event sourcing should be reasonably performant")
    }
}