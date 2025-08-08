package com.example.persistdurablecluster.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import java.io.Serializable
import java.time.LocalDateTime

interface PersistenceSerializable : Serializable

data class UserState @JsonCreator constructor(
    @JsonProperty("mallId") val mallId: String,
    @JsonProperty("userId") val userId: String,
    @JsonProperty("lastLogin") val lastLogin: LocalDateTime?,
    @JsonProperty("lastCartUsedTime") val lastCartUsedTime: LocalDateTime?,
    @JsonProperty("recentProducts") val recentProducts: List<String> = emptyList(),
    @JsonProperty("marketingOptIn") val marketingOptIn: Boolean = false,
    @JsonProperty("lastEventTime") val lastEventTime: LocalDateTime = LocalDateTime.now()
) : PersistenceSerializable {
    
    fun withRecentProduct(productId: String): UserState {
        val updatedProducts = (listOf(productId) + recentProducts).distinct().take(3)
        return copy(recentProducts = updatedProducts, lastEventTime = LocalDateTime.now())
    }
    
    fun withLogin(): UserState {
        return copy(lastLogin = LocalDateTime.now(), lastEventTime = LocalDateTime.now())
    }
    
    fun withCartUsed(): UserState {
        return copy(lastCartUsedTime = LocalDateTime.now(), lastEventTime = LocalDateTime.now())
    }
    
    fun withMarketingOptIn(optIn: Boolean): UserState {
        return copy(marketingOptIn = optIn, lastEventTime = LocalDateTime.now())
    }
}