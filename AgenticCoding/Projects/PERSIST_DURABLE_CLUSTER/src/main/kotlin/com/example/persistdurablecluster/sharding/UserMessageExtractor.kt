package com.example.persistdurablecluster.sharding

import com.example.persistdurablecluster.model.UserCommand
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor

class UserMessageExtractor : ShardingMessageExtractor<ShardingEnvelope<UserCommand>, UserCommand>() {
    
    override fun entityId(envelope: ShardingEnvelope<UserCommand>): String {
        return envelope.entityId()
    }
    
    override fun shardId(entityId: String): String {
        return UserShardingManager.extractShardId(entityId)
    }
    
    override fun unwrapMessage(envelope: ShardingEnvelope<UserCommand>): UserCommand {
        return envelope.message()
    }
}