package com.example.persisteventsource.actor

import com.example.persisteventsource.model.*
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalletActorTest {
    
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
    fun `test deposit adds funds to wallet`() {
        val probe = testKit.createTestProbe<WalletResponse>()
        val walletId = "wallet-${UUID.randomUUID()}"
        val wallet = testKit.spawn(WalletActor.create(walletId))
        
        // Deposit funds
        wallet.tell(Deposit(
            amount = BigDecimal("100.00"),
            source = "Bank Transfer",
            description = "Initial deposit",
            replyTo = probe.ref()
        ))
        
        val response = probe.expectMessageClass(TransactionSuccess::class.java)
        assertEquals("Deposit successful", response.message)
        assertEquals(BigDecimal("100.00"), response.newBalance)
        assertNotNull(response.transactionId)
        
        // Verify balance
        wallet.tell(GetBalance(probe.ref()))
        val balanceResponse = probe.expectMessageClass(BalanceResponse::class.java)
        assertEquals(BigDecimal("100.00"), balanceResponse.balance)
        assertEquals(AccountStatus.ACTIVE, balanceResponse.accountStatus)
    }
    
    @Test
    fun `test withdrawal with sufficient funds`() {
        val probe = testKit.createTestProbe<WalletResponse>()
        val walletId = "wallet-${UUID.randomUUID()}"
        val wallet = testKit.spawn(WalletActor.create(walletId))
        
        // First deposit
        wallet.tell(Deposit(
            amount = BigDecimal("500.00"),
            source = "Bank",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        // Withdraw
        wallet.tell(Withdraw(
            amount = BigDecimal("200.00"),
            destination = "ATM",
            description = "Cash withdrawal",
            replyTo = probe.ref()
        ))
        
        val response = probe.expectMessageClass(TransactionSuccess::class.java)
        assertEquals("Withdrawal successful", response.message)
        assertEquals(BigDecimal("300.00"), response.newBalance)
    }
    
    @Test
    fun `test withdrawal with insufficient funds`() {
        val probe = testKit.createTestProbe<WalletResponse>()
        val walletId = "wallet-${UUID.randomUUID()}"
        val wallet = testKit.spawn(WalletActor.create(walletId))
        
        // Deposit small amount
        wallet.tell(Deposit(
            amount = BigDecimal("50.00"),
            source = "Bank",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        // Try to withdraw more than balance
        wallet.tell(Withdraw(
            amount = BigDecimal("100.00"),
            destination = "ATM",
            replyTo = probe.ref()
        ))
        
        val response = probe.expectMessageClass(TransactionFailure::class.java)
        assertEquals("Insufficient funds", response.reason)
        assertEquals(BigDecimal("50.00"), response.currentBalance)
    }
    
    @Test
    fun `test daily withdrawal limit enforcement`() {
        val probe = testKit.createTestProbe<WalletResponse>()
        val walletId = "wallet-${UUID.randomUUID()}"
        val wallet = testKit.spawn(WalletActor.create(walletId))
        
        // Deposit large amount
        wallet.tell(Deposit(
            amount = BigDecimal("5000.00"),
            source = "Bank",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        // First withdrawal within limit
        wallet.tell(Withdraw(
            amount = BigDecimal("800.00"),
            destination = "ATM",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        // Second withdrawal that would exceed daily limit
        wallet.tell(Withdraw(
            amount = BigDecimal("300.00"),
            destination = "ATM",
            replyTo = probe.ref()
        ))
        
        val response = probe.expectMessageClass(TransactionFailure::class.java)
        assertTrue(response.reason.contains("Daily withdrawal limit exceeded"))
    }
    
    @Test
    fun `test transfer between wallets`() {
        val probe = testKit.createTestProbe<WalletResponse>()
        val senderWalletId = "sender-${UUID.randomUUID()}"
        val recipientWalletId = "recipient-${UUID.randomUUID()}"
        
        val senderWallet = testKit.spawn(WalletActor.create(senderWalletId))
        val recipientWallet = testKit.spawn(WalletActor.create(recipientWalletId))
        
        // Fund sender wallet
        senderWallet.tell(Deposit(
            amount = BigDecimal("1000.00"),
            source = "Bank",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        // Transfer from sender to recipient
        senderWallet.tell(Transfer(
            recipientWalletId = recipientWalletId,
            amount = BigDecimal("250.00"),
            description = "Payment",
            replyTo = probe.ref()
        ))
        
        val transferResponse = probe.expectMessageClass(TransactionSuccess::class.java)
        assertEquals("Transfer successful", transferResponse.message)
        assertEquals(BigDecimal("750.00"), transferResponse.newBalance)
        
        // Receive transfer in recipient wallet
        recipientWallet.tell(ReceiveTransfer(
            senderWalletId = senderWalletId,
            amount = BigDecimal("250.00"),
            description = "Payment",
            replyTo = probe.ref()
        ))
        
        val receiveResponse = probe.expectMessageClass(TransactionSuccess::class.java)
        assertEquals("Transfer received", receiveResponse.message)
        assertEquals(BigDecimal("250.00"), receiveResponse.newBalance)
    }
    
    @Test
    fun `test account freeze and unfreeze`() {
        val probe = testKit.createTestProbe<WalletResponse>()
        val walletId = "wallet-${UUID.randomUUID()}"
        val wallet = testKit.spawn(WalletActor.create(walletId))
        
        // Deposit funds
        wallet.tell(Deposit(
            amount = BigDecimal("500.00"),
            source = "Bank",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        // Freeze account
        wallet.tell(FreezeAccount(
            reason = "Suspicious activity",
            frozenBy = "Security Team",
            replyTo = probe.ref()
        ))
        
        val freezeResponse = probe.expectMessageClass(TransactionSuccess::class.java)
        assertEquals("Account frozen", freezeResponse.message)
        
        // Try withdrawal on frozen account
        wallet.tell(Withdraw(
            amount = BigDecimal("100.00"),
            destination = "ATM",
            replyTo = probe.ref()
        ))
        
        val withdrawResponse = probe.expectMessageClass(TransactionFailure::class.java)
        assertEquals("Account is frozen", withdrawResponse.reason)
        
        // Unfreeze account
        wallet.tell(UnfreezeAccount(
            reason = "Investigation completed",
            unfrozenBy = "Security Team",
            replyTo = probe.ref()
        ))
        
        val unfreezeResponse = probe.expectMessageClass(TransactionSuccess::class.java)
        assertEquals("Account unfrozen", unfreezeResponse.message)
        
        // Now withdrawal should work
        wallet.tell(Withdraw(
            amount = BigDecimal("100.00"),
            destination = "ATM",
            replyTo = probe.ref()
        ))
        
        val successfulWithdraw = probe.expectMessageClass(TransactionSuccess::class.java)
        assertEquals("Withdrawal successful", successfulWithdraw.message)
        assertEquals(BigDecimal("400.00"), successfulWithdraw.newBalance)
    }
    
    @Test
    fun `test setting daily withdrawal limit`() {
        val probe = testKit.createTestProbe<WalletResponse>()
        val walletId = "wallet-${UUID.randomUUID()}"
        val wallet = testKit.spawn(WalletActor.create(walletId))
        
        // Set new daily limit
        wallet.tell(SetDailyLimit(
            limit = BigDecimal("2000.00"),
            replyTo = probe.ref()
        ))
        
        val response = probe.expectMessageClass(TransactionSuccess::class.java)
        assertTrue(response.message.contains("Daily limit updated to 2000"))
        
        // Deposit funds
        wallet.tell(Deposit(
            amount = BigDecimal("5000.00"),
            source = "Bank",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        // Withdraw amount that would exceed old limit but within new limit
        wallet.tell(Withdraw(
            amount = BigDecimal("1500.00"),
            destination = "ATM",
            replyTo = probe.ref()
        ))
        
        val withdrawResponse = probe.expectMessageClass(TransactionSuccess::class.java)
        assertEquals("Withdrawal successful", withdrawResponse.message)
        assertEquals(BigDecimal("3500.00"), withdrawResponse.newBalance)
    }
    
    @Test
    fun `test transaction history tracking`() {
        val probe = testKit.createTestProbe<WalletResponse>()
        val walletId = "wallet-${UUID.randomUUID()}"
        val wallet = testKit.spawn(WalletActor.create(walletId))
        
        // Perform multiple transactions
        wallet.tell(Deposit(
            amount = BigDecimal("1000.00"),
            source = "Salary",
            description = "Monthly salary",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        wallet.tell(Withdraw(
            amount = BigDecimal("200.00"),
            destination = "ATM",
            description = "Cash withdrawal",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        wallet.tell(Transfer(
            recipientWalletId = "friend-wallet",
            amount = BigDecimal("150.00"),
            description = "Lunch money",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        // Get transaction history
        wallet.tell(GetTransactionHistory(
            limit = 10,
            replyTo = probe.ref()
        ))
        
        val historyResponse = probe.expectMessageClass(TransactionHistoryResponse::class.java)
        val transactions = historyResponse.transactions
        
        assertEquals(3, transactions.size)
        
        // Verify transactions are in reverse chronological order (newest first)
        assertEquals(TransactionType.TRANSFER_SENT, transactions[0].type)
        assertEquals(BigDecimal("150.00"), transactions[0].amount)
        assertEquals(BigDecimal("650.00"), transactions[0].balanceAfter)
        
        assertEquals(TransactionType.WITHDRAWAL, transactions[1].type)
        assertEquals(BigDecimal("200.00"), transactions[1].amount)
        assertEquals(BigDecimal("800.00"), transactions[1].balanceAfter)
        
        assertEquals(TransactionType.DEPOSIT, transactions[2].type)
        assertEquals(BigDecimal("1000.00"), transactions[2].amount)
        assertEquals(BigDecimal("1000.00"), transactions[2].balanceAfter)
    }
    
    @Test
    fun `test account status query`() {
        val probe = testKit.createTestProbe<WalletResponse>()
        val walletId = "wallet-${UUID.randomUUID()}"
        val wallet = testKit.spawn(WalletActor.create(walletId))
        
        // Initial deposit
        wallet.tell(Deposit(
            amount = BigDecimal("1500.00"),
            source = "Bank",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        // Make a withdrawal
        wallet.tell(Withdraw(
            amount = BigDecimal("300.00"),
            destination = "ATM",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        // Get account status
        wallet.tell(GetAccountStatus(probe.ref()))
        
        val statusResponse = probe.expectMessageClass(AccountStatusResponse::class.java)
        assertEquals(AccountStatus.ACTIVE, statusResponse.status)
        assertEquals(BigDecimal("1200.00"), statusResponse.balance)
        assertEquals(BigDecimal("1000.00"), statusResponse.dailyLimit)
        assertEquals(BigDecimal("300.00"), statusResponse.todayWithdrawn)
        assertNotNull(statusResponse.lastTransaction)
    }
    
    @Test
    fun `test event replay after restart`() {
        val probe = testKit.createTestProbe<WalletResponse>()
        val walletId = "persistent-wallet-${UUID.randomUUID()}"
        
        // Create wallet and perform transactions
        var wallet = testKit.spawn(WalletActor.create(walletId))
        
        wallet.tell(Deposit(
            amount = BigDecimal("1000.00"),
            source = "Bank",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        wallet.tell(Withdraw(
            amount = BigDecimal("250.00"),
            destination = "ATM",
            replyTo = probe.ref()
        ))
        probe.expectMessageClass(TransactionSuccess::class.java)
        
        // Stop the wallet actor
        testKit.stop(wallet)
        
        // Allow time for actor to stop
        Thread.sleep(1000)
        
        // Recreate wallet with same ID - should replay events
        wallet = testKit.spawn(WalletActor.create(walletId))
        
        // Verify state was reconstructed from events
        wallet.tell(GetBalance(probe.ref()))
        val balanceResponse = probe.expectMessageClass(BalanceResponse::class.java)
        assertEquals(BigDecimal("750.00"), balanceResponse.balance)
        
        // Verify transaction history was reconstructed
        wallet.tell(GetTransactionHistory(
            limit = 10,
            replyTo = probe.ref()
        ))
        
        val historyResponse = probe.expectMessageClass(TransactionHistoryResponse::class.java)
        assertEquals(2, historyResponse.transactions.size)
    }
    
    @Test
    fun `test concurrent transactions`() {
        val walletId = "concurrent-wallet-${UUID.randomUUID()}"
        val wallet = testKit.spawn(WalletActor.create(walletId))
        
        // Initial deposit
        val setupProbe = testKit.createTestProbe<WalletResponse>()
        wallet.tell(Deposit(
            amount = BigDecimal("10000.00"),
            source = "Bank",
            replyTo = setupProbe.ref()
        ))
        setupProbe.expectMessageClass(TransactionSuccess::class.java)
        
        // Create multiple probes for concurrent operations
        val probes = (1..10).map { testKit.createTestProbe<WalletResponse>() }
        
        // Send concurrent withdrawal requests
        probes.forEach { probe ->
            wallet.tell(Withdraw(
                amount = BigDecimal("100.00"),
                destination = "ATM-${probe.hashCode()}",
                replyTo = probe.ref()
            ))
        }
        
        // Collect responses
        val responses = probes.map { probe ->
            probe.expectMessageClass(WalletResponse::class.java)
        }
        
        // All should succeed (total withdrawal 1000, within daily limit)
        val successCount = responses.count { it is TransactionSuccess }
        assertEquals(10, successCount)
        
        // Verify final balance
        val finalProbe = testKit.createTestProbe<WalletResponse>()
        wallet.tell(GetBalance(finalProbe.ref()))
        val finalBalance = finalProbe.expectMessageClass(BalanceResponse::class.java)
        assertEquals(BigDecimal("9000.00"), finalBalance.balance)
    }
}