package org.example.kotlinbootreactivelabs.controller.actor.cluster

import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.future.await
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import org.example.kotlinbootreactivelabs.actor.cluster.CounterActor
import org.example.kotlinbootreactivelabs.actor.cluster.CounterCommand
import org.example.kotlinbootreactivelabs.actor.cluster.CounterState
import org.example.kotlinbootreactivelabs.actor.cluster.GetCount
import org.example.kotlinbootreactivelabs.actor.cluster.Increment
import org.example.kotlinbootreactivelabs.config.AkkaConfiguration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/api/actor/cluster")
@Tag(name = "Cluster Controller")
class ClusterController(private val akka: AkkaConfiguration) {

    private val akkaSystem = akka.getMainStage()

    @PostMapping("/increment-single-count-actor")
    suspend fun createSingleCountActor(@RequestParam count: Int): String {
        val single = akka.getSingleCount()
        single.tell(Increment(count))
        return "Incremented Single Count Actor $count"
    }

    @GetMapping("/get-single-count-actor")
    suspend fun getSingleCount(): String {
        val single = akka.getSingleCount()
        val response = AskPattern.ask(
            single,
            { replyTo: ActorRef<CounterState> -> GetCount(replyTo) },
            Duration.ofSeconds(3),
            akka.getScheduler()
        ).await()
        return "Get Single Count Actor ${response.count}"
    }

    @PutMapping("/create-shard-count-actor")
    suspend fun createShardCountActor(@RequestParam entityId: String): String {
        val typeKey = EntityTypeKey.create(CounterCommand::class.java, entityId)
        val shardSystem = ClusterSharding.get(akkaSystem)
        shardSystem.init(
            Entity.of(typeKey) { entityContext -> CounterActor.create(entityContext.entityId) }
        )
        return "Shard Count Actor Created"
    }

    @PostMapping("/increment-shard-count-actor")
    suspend fun incrementShardCount(@RequestParam entityId: String, @RequestParam count: Int): String {
        val typeKey = EntityTypeKey.create(CounterCommand::class.java, entityId)
        val shardSystem = ClusterSharding.get(akkaSystem)
        val shardCountActor: EntityRef<CounterCommand> = shardSystem.entityRefFor(typeKey, entityId)
        shardCountActor.tell(Increment(count))
        return "Incremented Shard Count Actor $count"
    }

    @GetMapping("/get-shard-count-actor")
    suspend fun getShardCount(@RequestParam entityId: String): String {
        val typeKey = EntityTypeKey.create(CounterCommand::class.java, entityId)
        val shardSystem = ClusterSharding.get(akkaSystem)
        val shardCountActor: EntityRef<CounterCommand> = shardSystem.entityRefFor(typeKey, entityId)
        val response = AskPattern.ask(
            shardCountActor,
            { replyTo: ActorRef<CounterState> -> GetCount(replyTo) },
            Duration.ofSeconds(3),
            akka.getScheduler()
        ).await()
        return "Get Shard Count Actor ${response.count}"
    }
}