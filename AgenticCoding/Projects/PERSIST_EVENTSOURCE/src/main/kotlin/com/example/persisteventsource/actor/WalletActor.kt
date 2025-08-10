package com.example.persisteventsource.actor

import com.example.persisteventsource.model.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.javadsl.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class WalletActor private constructor(
    private val context: ActorContext<WalletCommand>,
    private val persistenceId: PersistenceId
) : EventSourcedBehavior<WalletCommand, WalletEvent, WalletState>(persistenceId) {
    
    companion object {
        fun create(walletId: String): Behavior<WalletCommand> {
            return Behaviors.setup { context ->
                WalletActor(context, PersistenceId.of("Wallet", walletId))
            }
        }
    }
    
    override fun emptyState(): WalletState {
        return WalletState.empty(persistenceId.id())
    }
    
    override fun commandHandler(): CommandHandler<WalletCommand, WalletEvent, WalletState> {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(Deposit::class.java) { state, command -> onDeposit(state, command) }
            .onCommand(Withdraw::class.java) { state, command -> onWithdraw(state, command) }
            .onCommand(Transfer::class.java) { state, command -> onTransfer(state, command) }
            .onCommand(ReceiveTransfer::class.java) { state, command -> onReceiveTransfer(state, command) }
            .onCommand(SetDailyLimit::class.java) { state, command -> onSetDailyLimit(state, command) }
            .onCommand(FreezeAccount::class.java) { state, command -> onFreezeAccount(state, command) }
            .onCommand(UnfreezeAccount::class.java) { state, command -> onUnfreezeAccount(state, command) }
            .onCommand(GetBalance::class.java) { state, command -> onGetBalance(state, command) }
            .onCommand(GetTransactionHistory::class.java) { state, command -> onGetTransactionHistory(state, command) }
            .onCommand(GetAccountStatus::class.java) { state, command -> onGetAccountStatus(state, command) }
            .build()
    }
    
    override fun eventHandler(): EventHandler<WalletState, WalletEvent> {
        return newEventHandlerBuilder()
            .forAnyState()
            .onEvent(DepositAdded::class.java) { state, event -> applyDeposit(state, event) }
            .onEvent(WithdrawalMade::class.java) { state, event -> applyWithdrawal(state, event) }
            .onEvent(TransferSent::class.java) { state, event -> applyTransferSent(state, event) }
            .onEvent(TransferReceived::class.java) { state, event -> applyTransferReceived(state, event) }
            .onEvent(DailyLimitSet::class.java) { state, event -> applyDailyLimitSet(state, event) }
            .onEvent(AccountFrozen::class.java) { state, event -> applyAccountFrozen(state, event) }
            .onEvent(AccountUnfrozen::class.java) { state, event -> applyAccountUnfrozen(state, event) }
            .onEvent(WithdrawalRejected::class.java) { state, event -> applyWithdrawalRejected(state, event) }
            .build()
    }
    
    // Command handlers
    private fun onDeposit(state: WalletState, command: Deposit): Effect<WalletEvent, WalletState> {
        if (command.amount <= BigDecimal.ZERO) {
            command.replyTo.tell(TransactionFailure("Amount must be positive"))
            return Effect().none()
        }
        
        val transactionId = UUID.randomUUID().toString()
        val event = DepositAdded(
            amount = command.amount,
            timestamp = LocalDateTime.now(),
            transactionId = transactionId,
            source = command.source,
            description = command.description
        )
        
        return Effect()
            .persist(event)
            .thenRun { newState: WalletState ->
                context.log.info("Deposit: {} to wallet {} - new balance: {}", 
                    command.amount, persistenceId.id(), newState.balance)
                command.replyTo.tell(TransactionSuccess(
                    transactionId = transactionId,
                    message = "Deposit successful",
                    newBalance = newState.balance,
                    timestamp = event.timestamp
                ))
            }
    }
    
    private fun onWithdraw(state: WalletState, command: Withdraw): Effect<WalletEvent, WalletState> {
        if (command.amount <= BigDecimal.ZERO) {
            command.replyTo.tell(TransactionFailure("Amount must be positive"))
            return Effect().none()
        }
        
        if (state.accountStatus != AccountStatus.ACTIVE) {
            val event = WithdrawalRejected(
                attemptedAmount = command.amount,
                reason = "Account is frozen",
                timestamp = LocalDateTime.now()
            )
            return Effect()
                .persist(event)
                .thenRun { _: WalletState ->
                    command.replyTo.tell(TransactionFailure("Account is frozen", state.balance))
                }
        }
        
        if (command.amount > state.balance) {
            val event = WithdrawalRejected(
                attemptedAmount = command.amount,
                reason = "Insufficient funds",
                timestamp = LocalDateTime.now()
            )
            return Effect()
                .persist(event)
                .thenRun { _: WalletState ->
                    command.replyTo.tell(TransactionFailure("Insufficient funds", state.balance))
                }
        }
        
        if (!state.canWithdraw(command.amount)) {
            val event = WithdrawalRejected(
                attemptedAmount = command.amount,
                reason = "Daily withdrawal limit exceeded",
                timestamp = LocalDateTime.now()
            )
            return Effect()
                .persist(event)
                .thenRun { _: WalletState ->
                    command.replyTo.tell(TransactionFailure(
                        "Daily withdrawal limit exceeded. Remaining: ${state.getRemainingDailyWithdrawal()}", 
                        state.balance
                    ))
                }
        }
        
        val transactionId = UUID.randomUUID().toString()
        val event = WithdrawalMade(
            amount = command.amount,
            timestamp = LocalDateTime.now(),
            transactionId = transactionId,
            destination = command.destination,
            description = command.description
        )
        
        return Effect()
            .persist(event)
            .thenRun { newState: WalletState ->
                context.log.info("Withdrawal: {} from wallet {} - new balance: {}", 
                    command.amount, persistenceId.id(), newState.balance)
                command.replyTo.tell(TransactionSuccess(
                    transactionId = transactionId,
                    message = "Withdrawal successful",
                    newBalance = newState.balance,
                    timestamp = event.timestamp
                ))
            }
    }
    
    private fun onTransfer(state: WalletState, command: Transfer): Effect<WalletEvent, WalletState> {
        if (command.amount <= BigDecimal.ZERO) {
            command.replyTo.tell(TransactionFailure("Amount must be positive"))
            return Effect().none()
        }
        
        if (state.accountStatus != AccountStatus.ACTIVE) {
            command.replyTo.tell(TransactionFailure("Account is frozen", state.balance))
            return Effect().none()
        }
        
        if (command.amount > state.balance) {
            command.replyTo.tell(TransactionFailure("Insufficient funds", state.balance))
            return Effect().none()
        }
        
        val transactionId = UUID.randomUUID().toString()
        val event = TransferSent(
            recipientWalletId = command.recipientWalletId,
            amount = command.amount,
            timestamp = LocalDateTime.now(),
            transactionId = transactionId,
            description = command.description
        )
        
        return Effect()
            .persist(event)
            .thenRun { newState: WalletState ->
                context.log.info("Transfer: {} from wallet {} to {} - new balance: {}", 
                    command.amount, persistenceId.id(), command.recipientWalletId, newState.balance)
                command.replyTo.tell(TransactionSuccess(
                    transactionId = transactionId,
                    message = "Transfer successful",
                    newBalance = newState.balance,
                    timestamp = event.timestamp
                ))
            }
    }
    
    private fun onReceiveTransfer(state: WalletState, command: ReceiveTransfer): Effect<WalletEvent, WalletState> {
        val transactionId = UUID.randomUUID().toString()
        val event = TransferReceived(
            senderWalletId = command.senderWalletId,
            amount = command.amount,
            timestamp = LocalDateTime.now(),
            transactionId = transactionId,
            description = command.description
        )
        
        return Effect()
            .persist(event)
            .thenRun { newState: WalletState ->
                context.log.info("Transfer received: {} to wallet {} from {} - new balance: {}", 
                    command.amount, persistenceId.id(), command.senderWalletId, newState.balance)
                command.replyTo.tell(TransactionSuccess(
                    transactionId = transactionId,
                    message = "Transfer received",
                    newBalance = newState.balance,
                    timestamp = event.timestamp
                ))
            }
    }
    
    private fun onSetDailyLimit(state: WalletState, command: SetDailyLimit): Effect<WalletEvent, WalletState> {
        if (command.limit <= BigDecimal.ZERO) {
            command.replyTo.tell(TransactionFailure("Limit must be positive"))
            return Effect().none()
        }
        
        val event = DailyLimitSet(
            newLimit = command.limit,
            previousLimit = state.dailyWithdrawalLimit,
            timestamp = LocalDateTime.now()
        )
        
        return Effect()
            .persist(event)
            .thenRun { newState: WalletState ->
                context.log.info("Daily limit set to {} for wallet {}", command.limit, persistenceId.id())
                command.replyTo.tell(TransactionSuccess(
                    transactionId = event.transactionId,
                    message = "Daily limit updated to ${command.limit}",
                    newBalance = newState.balance,
                    timestamp = event.timestamp
                ))
            }
    }
    
    private fun onFreezeAccount(state: WalletState, command: FreezeAccount): Effect<WalletEvent, WalletState> {
        if (state.accountStatus == AccountStatus.FROZEN) {
            command.replyTo.tell(TransactionFailure("Account is already frozen"))
            return Effect().none()
        }
        
        val event = AccountFrozen(
            reason = command.reason,
            timestamp = LocalDateTime.now(),
            frozenBy = command.frozenBy
        )
        
        return Effect()
            .persist(event)
            .thenRun { _: WalletState ->
                context.log.warn("Account frozen: wallet {} by {} - reason: {}", 
                    persistenceId.id(), command.frozenBy, command.reason)
                command.replyTo.tell(TransactionSuccess(
                    transactionId = event.transactionId,
                    message = "Account frozen",
                    newBalance = state.balance,
                    timestamp = event.timestamp
                ))
            }
    }
    
    private fun onUnfreezeAccount(state: WalletState, command: UnfreezeAccount): Effect<WalletEvent, WalletState> {
        if (state.accountStatus == AccountStatus.ACTIVE) {
            command.replyTo.tell(TransactionFailure("Account is already active"))
            return Effect().none()
        }
        
        val event = AccountUnfrozen(
            reason = command.reason,
            timestamp = LocalDateTime.now(),
            unfrozenBy = command.unfrozenBy
        )
        
        return Effect()
            .persist(event)
            .thenRun { _: WalletState ->
                context.log.info("Account unfrozen: wallet {} by {} - reason: {}", 
                    persistenceId.id(), command.unfrozenBy, command.reason)
                command.replyTo.tell(TransactionSuccess(
                    transactionId = event.transactionId,
                    message = "Account unfrozen",
                    newBalance = state.balance,
                    timestamp = event.timestamp
                ))
            }
    }
    
    private fun onGetBalance(state: WalletState, command: GetBalance): Effect<WalletEvent, WalletState> {
        command.replyTo.tell(BalanceResponse(
            balance = state.balance,
            dailyWithdrawalRemaining = state.getRemainingDailyWithdrawal(),
            accountStatus = state.accountStatus
        ))
        return Effect().none()
    }
    
    private fun onGetTransactionHistory(state: WalletState, command: GetTransactionHistory): Effect<WalletEvent, WalletState> {
        val transactions = state.transactionHistory.take(command.limit)
        command.replyTo.tell(TransactionHistoryResponse(transactions))
        return Effect().none()
    }
    
    private fun onGetAccountStatus(state: WalletState, command: GetAccountStatus): Effect<WalletEvent, WalletState> {
        command.replyTo.tell(AccountStatusResponse(
            status = state.accountStatus,
            balance = state.balance,
            dailyLimit = state.dailyWithdrawalLimit,
            todayWithdrawn = state.todayWithdrawn,
            lastTransaction = state.lastTransactionTimestamp
        ))
        return Effect().none()
    }
    
    // Event handlers
    private fun applyDeposit(state: WalletState, event: DepositAdded): WalletState {
        val newState = state.deposit(event.amount)
        val record = TransactionRecord(
            transactionId = event.transactionId,
            type = TransactionType.DEPOSIT,
            amount = event.amount,
            timestamp = event.timestamp,
            description = event.description,
            balanceAfter = newState.balance
        )
        return newState.addTransaction(record)
    }
    
    private fun applyWithdrawal(state: WalletState, event: WithdrawalMade): WalletState {
        val newState = state.withdraw(event.amount)
        val record = TransactionRecord(
            transactionId = event.transactionId,
            type = TransactionType.WITHDRAWAL,
            amount = event.amount,
            timestamp = event.timestamp,
            description = event.description,
            balanceAfter = newState.balance
        )
        return newState.addTransaction(record)
    }
    
    private fun applyTransferSent(state: WalletState, event: TransferSent): WalletState {
        val newState = state.withdraw(event.amount)
        val record = TransactionRecord(
            transactionId = event.transactionId,
            type = TransactionType.TRANSFER_SENT,
            amount = event.amount,
            timestamp = event.timestamp,
            description = "Transfer to ${event.recipientWalletId}: ${event.description ?: ""}",
            balanceAfter = newState.balance
        )
        return newState.addTransaction(record)
    }
    
    private fun applyTransferReceived(state: WalletState, event: TransferReceived): WalletState {
        val newState = state.deposit(event.amount)
        val record = TransactionRecord(
            transactionId = event.transactionId,
            type = TransactionType.TRANSFER_RECEIVED,
            amount = event.amount,
            timestamp = event.timestamp,
            description = "Transfer from ${event.senderWalletId}: ${event.description ?: ""}",
            balanceAfter = newState.balance
        )
        return newState.addTransaction(record)
    }
    
    private fun applyDailyLimitSet(state: WalletState, event: DailyLimitSet): WalletState {
        val newState = state.setDailyLimit(event.newLimit)
        val record = TransactionRecord(
            transactionId = event.transactionId,
            type = TransactionType.LIMIT_CHANGE,
            amount = event.newLimit,
            timestamp = event.timestamp,
            description = "Daily limit changed from ${event.previousLimit} to ${event.newLimit}",
            balanceAfter = state.balance
        )
        return newState.addTransaction(record)
    }
    
    private fun applyAccountFrozen(state: WalletState, event: AccountFrozen): WalletState {
        val newState = state.freeze()
        val record = TransactionRecord(
            transactionId = event.transactionId,
            type = TransactionType.ACCOUNT_FROZEN,
            amount = BigDecimal.ZERO,
            timestamp = event.timestamp,
            description = "Account frozen by ${event.frozenBy}: ${event.reason}",
            balanceAfter = state.balance
        )
        return newState.addTransaction(record)
    }
    
    private fun applyAccountUnfrozen(state: WalletState, event: AccountUnfrozen): WalletState {
        val newState = state.unfreeze()
        val record = TransactionRecord(
            transactionId = event.transactionId,
            type = TransactionType.ACCOUNT_UNFROZEN,
            amount = BigDecimal.ZERO,
            timestamp = event.timestamp,
            description = "Account unfrozen by ${event.unfrozenBy}: ${event.reason}",
            balanceAfter = state.balance
        )
        return newState.addTransaction(record)
    }
    
    private fun applyWithdrawalRejected(state: WalletState, event: WithdrawalRejected): WalletState {
        val record = TransactionRecord(
            transactionId = event.transactionId,
            type = TransactionType.WITHDRAWAL_REJECTED,
            amount = event.attemptedAmount,
            timestamp = event.timestamp,
            description = "Withdrawal rejected: ${event.reason}",
            balanceAfter = state.balance
        )
        return state.addTransaction(record)
    }
    
    // Snapshot configuration
    override fun retentionCriteria(): RetentionCriteria {
        return RetentionCriteria.snapshotEvery(100, 3)
    }
    
    override fun shouldSnapshot(state: WalletState, event: WalletEvent, sequenceNr: Long): Boolean {
        return sequenceNr % 100 == 0L
    }
}