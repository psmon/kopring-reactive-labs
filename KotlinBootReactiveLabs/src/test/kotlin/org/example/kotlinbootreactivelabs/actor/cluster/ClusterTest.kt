package org.example.kotlinbootreactivelabs.actor.cluster

import com.typesafe.config.ConfigFactory

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration

class ClusterTest {

    companion object{
        private lateinit var nodeA: ActorTestKit
        private lateinit var nodeB: ActorTestKit

        @BeforeAll
        @JvmStatic
        fun setup(){
            val clusterConfigA = ConfigFactory.load("cluster1.conf")
            val clusterConfigB = ConfigFactory.load("cluster2.conf")

            nodeA = ActorTestKit.create("ClusterSystem",clusterConfigA)
            nodeB = ActorTestKit.create("ClusterSystem",clusterConfigB)

            val clusterA = Cluster.get(nodeA.system())
            val clusterB = Cluster.get(nodeB.system())

        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            nodeB.shutdownTestKit()
            nodeA.shutdownTestKit()
        }
    }

    @Test
    fun testSingleCluster(){

        var givenInItCount = 5

        val testProbe = nodeA.createTestProbe<CounterState>()
        val testProbe2 = nodeB.createTestProbe<CounterState>()

        val sigleton1:ClusterSingleton = ClusterSingleton.get(nodeA.system())

        var proxy1:ActorRef<CounterCommand> = sigleton1.init(SingletonActor.of(CounterActor.create("singleId"), "GlobalCounter"))

        val sigleton2:ClusterSingleton = ClusterSingleton.get(nodeB.system())

        var proxy2:ActorRef<CounterCommand> = sigleton2.init(SingletonActor.of(CounterActor.create("singleId"), "GlobalCounter"))

        proxy1.tell(Increment(3))

        proxy1.tell(GetCount(testProbe.ref()))

        testProbe.expectMessage(CounterState(3 + givenInItCount))

        proxy2.tell(Increment(2))

        proxy2.tell(GetCount(testProbe2.ref()))

        testProbe2.expectMessage(CounterState(5 + givenInItCount))

    }

    @Test
    fun testSingleToneClusterWithSuperVise(){

        var givenInItCount = 5

        val testProbe = nodeA.createTestProbe<CounterState>()
        val testProbe2 = nodeB.createTestProbe<CounterState>()

        val sigleton1:ClusterSingleton = ClusterSingleton.get(nodeA.system())

        var proxy1:ActorRef<CounterCommand> = sigleton1.init(
            SingletonActor.of(
            Behaviors.supervise(CounterActor.create("singleId"))
                .onFailure(SupervisorStrategy.restartWithBackoff(
                    Duration.ofSeconds(1), Duration.ofSeconds(2), 0.2)
                ),
            "GlobalCounter"))


        val sigleton2:ClusterSingleton = ClusterSingleton.get(nodeB.system())

        var proxy2:ActorRef<CounterCommand> = sigleton2.init(
            SingletonActor.of(
                Behaviors.supervise(CounterActor.create("singleId"))
                    .onFailure(SupervisorStrategy.restartWithBackoff(
                        Duration.ofSeconds(1), Duration.ofSeconds(2), 0.2)
                ),
            "GlobalCounter"))

        proxy1.tell(Increment(3))

        proxy1.tell(GetCount(testProbe.ref()))

        testProbe.expectMessage(CounterState(3+givenInItCount))

        proxy2.tell(Increment(2))

        proxy2.tell(GetCount(testProbe2.ref()))

        testProbe2.expectMessage(CounterState(5+givenInItCount))

        proxy1.tell(GoodByeCounter)

        proxy2.tell(GetCount(testProbe2.ref()))

        testProbe2.expectMessage(CounterState(givenInItCount))

    }

    @Test
    fun testSharedCluster(){
        var givenInItCount = 5

        var typeKey = EntityTypeKey.create(CounterCommand::class.java, "Counter")

        val testProbe = nodeA.createTestProbe<CounterState>()
        val testProbe2 = nodeB.createTestProbe<CounterState>()

        var shard1 = ClusterSharding.get(nodeA.system())
        var shard2 = ClusterSharding.get(nodeB.system())

        var shardRegion1 = shard1.init(Entity.of(typeKey,
            { entityContext -> CounterActor.create(entityContext.entityId) }
        ))

        var shardRegion2 = shard2.init(Entity.of(typeKey,
            { entityContext -> CounterActor.create(entityContext.entityId) }
        ))

        var counter1:EntityRef<CounterCommand> = shard1.entityRefFor(typeKey, "counter1")
        var counter2:EntityRef<CounterCommand> = shard2.entityRefFor(typeKey, "counter2")

        // Resion1 at counter1
        shardRegion1.tell(ShardingEnvelope("counter1", Increment(3)))
        shardRegion1.tell(ShardingEnvelope("counter1", GetCount(testProbe.ref())))
        testProbe.expectMessage(CounterState(3 + givenInItCount))

        // Resion2 at counter1
        shardRegion2.tell(ShardingEnvelope("counter1", Increment(3)))
        shardRegion2.tell(ShardingEnvelope("counter1", GetCount(testProbe2.ref())))
        testProbe2.expectMessage(CounterState(6 + givenInItCount))

        // Resion2 at counter2
        shardRegion2.tell(ShardingEnvelope("counter2", Increment(3)))
        shardRegion2.tell(ShardingEnvelope("counter2", GetCount(testProbe2.ref())))
        testProbe2.expectMessage(CounterState(3 + givenInItCount))

    }
}