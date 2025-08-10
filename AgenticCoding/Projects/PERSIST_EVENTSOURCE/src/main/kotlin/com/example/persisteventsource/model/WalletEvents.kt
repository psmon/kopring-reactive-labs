package com.example.persisteventsource.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

// Marker interface for serialization
interface PersistenceSerializable : Serializable

// Base event interface
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
sealed class WalletEvent : PersistenceSerializable {
    abstract val timestamp: LocalDateTime
    abstract val transactionId: String
}

// Deposit event
data class DepositAdded @JsonCreator constructor(
    @JsonProperty("amount") val amount: BigDecimal,
    @JsonProperty("timestamp") override val timestamp: LocalDateTime,
    @JsonProperty("transactionId") override val transactionId: String,
    @JsonProperty("source") val source: String,
    @JsonProperty("description") val description: String? = null
) : WalletEvent()

// Withdrawal event
data class WithdrawalMade @JsonCreator constructor(
    @JsonProperty("amount") val amount: BigDecimal,
    @JsonProperty("timestamp") override val timestamp: LocalDateTime,
    @JsonProperty("transactionId") override val transactionId: String,
    @JsonProperty("destination") val destination: String,
    @JsonProperty("description") val description: String? = null
) : WalletEvent()

// Transfer sent event
data class TransferSent @JsonCreator constructor(
    @JsonProperty("recipientWalletId") val recipientWalletId: String,
    @JsonProperty("amount") val amount: BigDecimal,
    @JsonProperty("timestamp") override val timestamp: LocalDateTime,
    @JsonProperty("transactionId") override val transactionId: String,
    @JsonProperty("description") val description: String? = null
) : WalletEvent()

// Transfer received event
data class TransferReceived @JsonCreator constructor(
    @JsonProperty("senderWalletId") val senderWalletId: String,
    @JsonProperty("amount") val amount: BigDecimal,
    @JsonProperty("timestamp") override val timestamp: LocalDateTime,
    @JsonProperty("transactionId") override val transactionId: String,
    @JsonProperty("description") val description: String? = null
) : WalletEvent()

// Account frozen event
data class AccountFrozen @JsonCreator constructor(
    @JsonProperty("reason") val reason: String,
    @JsonProperty("timestamp") override val timestamp: LocalDateTime,
    @JsonProperty("transactionId") override val transactionId: String = UUID.randomUUID().toString(),
    @JsonProperty("frozenBy") val frozenBy: String
) : WalletEvent()

// Account unfrozen event
data class AccountUnfrozen @JsonCreator constructor(
    @JsonProperty("reason") val reason: String,
    @JsonProperty("timestamp") override val timestamp: LocalDateTime,
    @JsonProperty("transactionId") override val transactionId: String = UUID.randomUUID().toString(),
    @JsonProperty("unfrozenBy") val unfrozenBy: String
) : WalletEvent()

// Daily limit set event
data class DailyLimitSet @JsonCreator constructor(
    @JsonProperty("newLimit") val newLimit: BigDecimal,
    @JsonProperty("previousLimit") val previousLimit: BigDecimal?,
    @JsonProperty("timestamp") override val timestamp: LocalDateTime,
    @JsonProperty("transactionId") override val transactionId: String = UUID.randomUUID().toString()
) : WalletEvent()

// Withdrawal rejected event (for audit purposes)
data class WithdrawalRejected @JsonCreator constructor(
    @JsonProperty("attemptedAmount") val attemptedAmount: BigDecimal,
    @JsonProperty("reason") val reason: String,
    @JsonProperty("timestamp") override val timestamp: LocalDateTime,
    @JsonProperty("transactionId") override val transactionId: String = UUID.randomUUID().toString()
) : WalletEvent()