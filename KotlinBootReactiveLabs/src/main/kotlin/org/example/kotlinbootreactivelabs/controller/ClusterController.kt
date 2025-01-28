package org.example.kotlinbootreactivelabs.controller

import io.swagger.v3.oas.annotations.tags.Tag
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
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
import reactor.core.publisher.Mono
import java.time.Duration

@RestController
@RequestMapping("/api/cluster")
@Tag(name = "Cluster Controller")
class ClusterController(private val akka: AkkaConfiguration) {

    private val akkaSystem = akka.getMainStage()

    @PostMapping("/increment-single-count-actor")
    fun createSingleCountActor(@RequestParam count: Int): Mono<String> {
        return Mono.fromCallable {

            val single = akka.getSingleCount()
            single.tell(Increment(count))

            "Incremented Single Count Actor $count"
        }
    }

    @GetMapping("/get-single-count-actor")
    fun getSingleCount(): Mono<String> {
        return Mono.fromCallable {

            val single = akka.getSingleCount()

            val response = AskPattern.ask(
                single,
                { replyTo: ActorRef<CounterState> -> GetCount(replyTo) },
                Duration.ofSeconds(3),
                akka.getScheduler()
            ).toCompletableFuture().get()

            "Get Single Count Actor ${response.count}"
        }
    }

    @PutMapping("/create-shard-count-actor")
    fun createShardCountActor(@RequestParam entityId: String): Mono<String> {
        return Mono.fromCallable {

            var typeKey = EntityTypeKey.create(CounterCommand::class.java, entityId)

            var shardSystem = ClusterSharding.get(akkaSystem)

            var shardCountActorEx = shardSystem.init(Entity.of(typeKey,
                { entityContext -> CounterActor.create(entityContext.entityId) }
            ))

            "Shard Count Actor Created"
        }
    }

    @PostMapping("/increment-shard-count-actor")
    fun incrementShardCount(@RequestParam entityId: String, @RequestParam count: Int): Mono<String> {
        return Mono.fromCallable {

            var typeKey = EntityTypeKey.create(CounterCommand::class.java, entityId)

            var shardSystem = ClusterSharding.get(akkaSystem)

            var shardCountActor:EntityRef<CounterCommand> = shardSystem.entityRefFor(typeKey, entityId)

            shardCountActor.tell(Increment(count))

            "Incremented Shard Count Actor $count"
        }
    }

    @GetMapping("/get-shard-count-actor")
    fun getShardCount(@RequestParam entityId: String): Mono<String> {
        return Mono.fromCallable {
            var typeKey = EntityTypeKey.create(CounterCommand::class.java, entityId)

            var shardSystem = ClusterSharding.get(akkaSystem)

            val shardRegionOrProxy: ActorRef<ShardingEnvelope<CounterCommand>> =
                shardSystem.init(
                    Entity.of(typeKey) { ctx -> CounterActor.create(ctx.entityId) }.withRole("shard")
                )

            var shardCountActor:EntityRef<CounterCommand> = shardSystem.entityRefFor(typeKey, entityId)

            val response = AskPattern.ask(
                shardCountActor,
                { replyTo: ActorRef<CounterState> -> GetCount(replyTo) },
                Duration.ofSeconds(3),
                akka.getScheduler()
            ).toCompletableFuture().get()

            "Get Shard Count Actor ${response.count}"
        }
    }
}