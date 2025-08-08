package com.example.persistdurablecluster.sharding

import com.example.persistdurablecluster.actor.ClusteredUserStateActor
import com.example.persistdurablecluster.model.UserCommand
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef
import org.apache.pekko.cluster.typed.Cluster
import org.slf4j.LoggerFactory

class UserShardingManager(private val system: ActorSystem<*>) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(UserShardingManager::class.java)
        private const val ROLE_NAME = "shard"
        
        fun extractShardId(entityId: String): String {
            // Use hash-based sharding for even distribution
            val hashCode = entityId.hashCode()
            val numberOfShards = 100 // Configurable based on cluster size
            val shardId = Math.abs(hashCode % numberOfShards)
            return shardId.toString()
        }
    }
    
    private val sharding: ClusterSharding = ClusterSharding.get(system)
    private val cluster: Cluster = Cluster.get(system)
    
    init {
        logger.info("Initializing UserShardingManager on node: ${system.address()}")
        initializeSharding()
    }
    
    private fun initializeSharding() {
        logger.info("Setting up cluster sharding for UserEntity")
        
        sharding.init(
            Entity.of(UserCommand.TYPE_KEY) { entityContext ->
                logger.info("Creating entity ${entityContext.entityId} on shard ${entityContext.shard.path()}")
                ClusteredUserStateActor.create(entityContext)
            }.withRole(ROLE_NAME)
             .withMessageExtractor(UserMessageExtractor())
        )
        
        logger.info("Cluster sharding initialized with role: $ROLE_NAME")
    }
    
    fun getUserEntity(mallId: String, userId: String): EntityRef<UserCommand> {
        val entityId = ClusteredUserStateActor.extractEntityId(mallId, userId)
        val entityRef = sharding.entityRefFor(UserCommand.TYPE_KEY, entityId)
        
        logger.debug("Getting entity reference for entityId: $entityId, will route to shard: ${extractShardId(entityId)}")
        
        return entityRef
    }
    
    fun sendCommand(mallId: String, userId: String, command: UserCommand) {
        val entityRef = getUserEntity(mallId, userId)
        entityRef.tell(command)
    }
    
    fun getClusterStatus(): ClusterStatus {
        val membersList = cluster.state().members.toList()
        val selfMember = cluster.selfMember()
        
        return ClusterStatus(
            selfAddress = selfMember.address().toString(),
            selfRoles = selfMember.roles,
            numberOfMembers = membersList.size,
            memberAddresses = membersList.map { it.address().toString() }
        )
    }
    
    data class ClusterStatus(
        val selfAddress: String,
        val selfRoles: Set<String>,
        val numberOfMembers: Int,
        val memberAddresses: List<String>
    )
}