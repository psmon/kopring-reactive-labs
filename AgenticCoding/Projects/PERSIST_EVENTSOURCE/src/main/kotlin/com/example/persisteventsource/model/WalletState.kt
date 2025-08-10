package com.example.persisteventsource.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class WalletState @JsonCreator constructor(
    @JsonProperty("walletId") val walletId: String,
    @JsonProperty("balance") val balance: BigDecimal = BigDecimal.ZERO,
    @JsonProperty("accountStatus") val accountStatus: AccountStatus = AccountStatus.ACTIVE,
    @JsonProperty("dailyWithdrawalLimit") val dailyWithdrawalLimit: BigDecimal = BigDecimal("1000.00"),
    @JsonProperty("todayWithdrawn") val todayWithdrawn: BigDecimal = BigDecimal.ZERO,
    @JsonProperty("lastWithdrawalDate") val lastWithdrawalDate: LocalDate? = null,
    @JsonProperty("lastTransactionTimestamp") val lastTransactionTimestamp: LocalDateTime? = null,
    @JsonProperty("transactionHistory") val transactionHistory: List<TransactionRecord> = emptyList(),
    @JsonProperty("createdAt") val createdAt: LocalDateTime = LocalDateTime.now()
) : PersistenceSerializable {
    
    companion object {
        fun empty(walletId: String): WalletState = WalletState(walletId)
        
        const val MAX_HISTORY_SIZE = 1000
        const val DEFAULT_DAILY_LIMIT = "1000.00"
    }
    
    // Check if withdrawal limit exceeded
    fun canWithdraw(amount: BigDecimal): Boolean {
        if (accountStatus != AccountStatus.ACTIVE) return false
        if (amount > balance) return false
        
        val today = LocalDate.now()
        val effectiveWithdrawn = if (lastWithdrawalDate == today) todayWithdrawn else BigDecimal.ZERO
        
        return (effectiveWithdrawn + amount) <= dailyWithdrawalLimit
    }
    
    // Update daily withdrawal tracking
    fun updateDailyWithdrawal(amount: BigDecimal): WalletState {
        val today = LocalDate.now()
        return if (lastWithdrawalDate == today) {
            copy(todayWithdrawn = todayWithdrawn + amount)
        } else {
            copy(
                todayWithdrawn = amount,
                lastWithdrawalDate = today
            )
        }
    }
    
    // Add transaction to history
    fun addTransaction(record: TransactionRecord): WalletState {
        val updatedHistory = (listOf(record) + transactionHistory).take(MAX_HISTORY_SIZE)
        return copy(
            transactionHistory = updatedHistory,
            lastTransactionTimestamp = record.timestamp
        )
    }
    
    // Get remaining withdrawal amount for today
    fun getRemainingDailyWithdrawal(): BigDecimal {
        val today = LocalDate.now()
        val effectiveWithdrawn = if (lastWithdrawalDate == today) todayWithdrawn else BigDecimal.ZERO
        return (dailyWithdrawalLimit - effectiveWithdrawn).max(BigDecimal.ZERO)
    }
    
    // Process deposit
    fun deposit(amount: BigDecimal): WalletState {
        return copy(balance = balance + amount)
    }
    
    // Process withdrawal
    fun withdraw(amount: BigDecimal): WalletState {
        return copy(balance = balance - amount)
            .updateDailyWithdrawal(amount)
    }
    
    // Freeze account
    fun freeze(): WalletState {
        return copy(accountStatus = AccountStatus.FROZEN)
    }
    
    // Unfreeze account
    fun unfreeze(): WalletState {
        return copy(accountStatus = AccountStatus.ACTIVE)
    }
    
    // Set daily limit
    fun setDailyLimit(newLimit: BigDecimal): WalletState {
        return copy(dailyWithdrawalLimit = newLimit)
    }
}