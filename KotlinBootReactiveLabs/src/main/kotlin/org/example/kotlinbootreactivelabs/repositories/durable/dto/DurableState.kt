package org.example.kotlinbootreactivelabs.repositories.durable.dto

import java.time.LocalDateTime


data class DurableState(
    val slice: Int,
    val entityType: String,
    val persistenceId: String,
    val revision: Long,
    val dbTimestamp: LocalDateTime,
    val stateSerId: Int,
    val stateSerManifest: String,
    val statePayload: ByteArray,
    val tags: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DurableState

        if (revision != other.revision) return false
        if (slice != other.slice) return false
        if (entityType != other.entityType) return false
        if (persistenceId != other.persistenceId) return false
        if (dbTimestamp != other.dbTimestamp) return false
        if (stateSerId != other.stateSerId) return false
        if (stateSerManifest != other.stateSerManifest) return false
        if (!statePayload.contentEquals(other.statePayload)) return false
        if (tags != other.tags) return false

        return true
    }

    override fun hashCode(): Int {
        var result = revision.hashCode()
        result = 31 * result + slice.hashCode()
        result = 31 * result + entityType.hashCode()
        result = 31 * result + persistenceId.hashCode()
        result = 31 * result + dbTimestamp.hashCode()
        result = 31 * result + stateSerId.hashCode()
        result = 31 * result + stateSerManifest.hashCode()
        result = 31 * result + statePayload.contentHashCode()
        result = 31 * result + tags.hashCode()
        return result
    }
}