package com.example.persisteventsource.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.pekko.actor.typed.ActorRef
import java.math.BigDecimal
import java.time.LocalDateTime

// Base command interface
sealed class WalletCommand : PersistenceSerializable

// Deposit command
data class Deposit @JsonCreator constructor(
    @JsonProperty("amount") val amount: BigDecimal,
    @JsonProperty("source") val source: String,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("replyTo") val replyTo: ActorRef<WalletResponse>
) : WalletCommand()

// Withdraw command
data class Withdraw @JsonCreator constructor(
    @JsonProperty("amount") val amount: BigDecimal,
    @JsonProperty("destination") val destination: String,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("replyTo") val replyTo: ActorRef<WalletResponse>
) : WalletCommand()

// Transfer command
data class Transfer @JsonCreator constructor(
    @JsonProperty("recipientWalletId") val recipientWalletId: String,
    @JsonProperty("amount") val amount: BigDecimal,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("replyTo") val replyTo: ActorRef<WalletResponse>
) : WalletCommand()

// Receive transfer command (internal)
data class ReceiveTransfer @JsonCreator constructor(
    @JsonProperty("senderWalletId") val senderWalletId: String,
    @JsonProperty("amount") val amount: BigDecimal,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("replyTo") val replyTo: ActorRef<WalletResponse>
) : WalletCommand()

// Set daily limit command
data class SetDailyLimit @JsonCreator constructor(
    @JsonProperty("limit") val limit: BigDecimal,
    @JsonProperty("replyTo") val replyTo: ActorRef<WalletResponse>
) : WalletCommand()

// Freeze account command
data class FreezeAccount @JsonCreator constructor(
    @JsonProperty("reason") val reason: String,
    @JsonProperty("frozenBy") val frozenBy: String,
    @JsonProperty("replyTo") val replyTo: ActorRef<WalletResponse>
) : WalletCommand()

// Unfreeze account command
data class UnfreezeAccount @JsonCreator constructor(
    @JsonProperty("reason") val reason: String,
    @JsonProperty("unfrozenBy") val unfrozenBy: String,
    @JsonProperty("replyTo") val replyTo: ActorRef<WalletResponse>
) : WalletCommand()

// Get balance query
data class GetBalance @JsonCreator constructor(
    @JsonProperty("replyTo") val replyTo: ActorRef<WalletResponse>
) : WalletCommand()

// Get transaction history query
data class GetTransactionHistory @JsonCreator constructor(
    @JsonProperty("limit") val limit: Int = 100,
    @JsonProperty("replyTo") val replyTo: ActorRef<WalletResponse>
) : WalletCommand()

// Get account status query
data class GetAccountStatus @JsonCreator constructor(
    @JsonProperty("replyTo") val replyTo: ActorRef<WalletResponse>
) : WalletCommand()

// Response types
sealed class WalletResponse : PersistenceSerializable

data class TransactionSuccess @JsonCreator constructor(
    @JsonProperty("transactionId") val transactionId: String,
    @JsonProperty("message") val message: String,
    @JsonProperty("newBalance") val newBalance: BigDecimal,
    @JsonProperty("timestamp") val timestamp: LocalDateTime
) : WalletResponse()

data class TransactionFailure @JsonCreator constructor(
    @JsonProperty("reason") val reason: String,
    @JsonProperty("currentBalance") val currentBalance: BigDecimal? = null
) : WalletResponse()

data class BalanceResponse @JsonCreator constructor(
    @JsonProperty("balance") val balance: BigDecimal,
    @JsonProperty("dailyWithdrawalRemaining") val dailyWithdrawalRemaining: BigDecimal,
    @JsonProperty("accountStatus") val accountStatus: AccountStatus
) : WalletResponse()

data class TransactionHistoryResponse @JsonCreator constructor(
    @JsonProperty("transactions") val transactions: List<TransactionRecord>
) : WalletResponse()

data class AccountStatusResponse @JsonCreator constructor(
    @JsonProperty("status") val status: AccountStatus,
    @JsonProperty("balance") val balance: BigDecimal,
    @JsonProperty("dailyLimit") val dailyLimit: BigDecimal,
    @JsonProperty("todayWithdrawn") val todayWithdrawn: BigDecimal,
    @JsonProperty("lastTransaction") val lastTransaction: LocalDateTime?
) : WalletResponse()

// Transaction record for history
data class TransactionRecord @JsonCreator constructor(
    @JsonProperty("transactionId") val transactionId: String,
    @JsonProperty("type") val type: TransactionType,
    @JsonProperty("amount") val amount: BigDecimal,
    @JsonProperty("timestamp") val timestamp: LocalDateTime,
    @JsonProperty("description") val description: String?,
    @JsonProperty("balanceAfter") val balanceAfter: BigDecimal
) : PersistenceSerializable

enum class TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_SENT,
    TRANSFER_RECEIVED,
    LIMIT_CHANGE,
    ACCOUNT_FROZEN,
    ACCOUNT_UNFROZEN,
    WITHDRAWAL_REJECTED
}

enum class AccountStatus {
    ACTIVE,
    FROZEN
}