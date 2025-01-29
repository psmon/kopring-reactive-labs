package org.example.kotlinbootreactivelabs.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import org.example.kotlinbootreactivelabs.actor.MainStageActor
import org.example.kotlinbootreactivelabs.actor.MainStageActorCommand
import org.example.kotlinbootreactivelabs.actor.cluster.CounterActor
import org.example.kotlinbootreactivelabs.actor.cluster.CounterCommand
import org.example.kotlinbootreactivelabs.actor.state.HelloState
import org.example.kotlinbootreactivelabs.actor.state.HelloStateActor
import org.example.kotlinbootreactivelabs.actor.state.HelloStateActorCommand
import org.example.kotlinbootreactivelabs.actor.state.store.HelloStateStoreActor
import org.example.kotlinbootreactivelabs.actor.state.store.HelloStateStoreActorCommand
import org.example.kotlinbootreactivelabs.repositories.durable.DurableRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class AkkaConfiguration {

    private val logger: org.slf4j.Logger = LoggerFactory.getLogger(AkkaConfiguration::class.java)

    private lateinit var mainStage: ActorSystem<MainStageActorCommand>

    private lateinit var helloState: ActorSystem<HelloStateActorCommand>

    private lateinit var helloStateStore : ActorSystem<HelloStateStoreActorCommand>

    private lateinit var singleCount: ActorRef<CounterCommand>

    @Autowired lateinit var durableRepository: DurableRepository

    @PostConstruct
    fun init(){

        val baseConfig = ConfigFactory.load("application.conf")
        val clusterConfigName = System.getProperty("Cluster") ?: ""
        val finalConfig: Config = if (clusterConfigName.isNotEmpty()) {
            val clusterConfig = ConfigFactory.load("$clusterConfigName.conf")
            clusterConfig.withFallback(baseConfig)
        } else {
            baseConfig
        }

        logger.info("Starting Akka Actor System with config: $clusterConfigName")

        mainStage = ActorSystem.create(MainStageActor.create(), "ClusterSystem", finalConfig)

        // Locla Actor
        helloState = ActorSystem.create(HelloStateActor.create(HelloState.HAPPY), "HelloStateActor")
        helloStateStore = ActorSystem.create(HelloStateStoreActor.create("test-perstistid-00001",durableRepository), "helloStateStore")

        if(clusterConfigName.isEmpty()){
            logger.info("Cluster Config is Empty - Skip Cluster Init")
            return
        }

        // Cluster Init
        val selfMember = Cluster.get(mainStage).selfMember()

        if (selfMember.hasRole("seed")) {
            logger.info("My Application Role Seed")
        }

        if (selfMember.hasRole("helloA")) {
            logger.info("My Application Role HelloA")
        }

        if (selfMember.hasRole("helloB")) {
            logger.info("My Application Role HelloB")
        }

        // ClusterSharding
        if (selfMember.hasRole("shard")) {
            logger.info("My Application Role shard")
            for (i in 1..100) {
                val entityId = "test-$i"
                var typeKey = EntityTypeKey.create(CounterCommand::class.java, entityId)
                var shardSystem = ClusterSharding.get(mainStage)
                shardSystem.init(Entity.of(typeKey, {
                    entityContext -> CounterActor.create(entityContext.entityId) }
                ))
            }
        }

        // ClusterSingleton
        val single:ClusterSingleton = ClusterSingleton.get(mainStage)
        singleCount = single.init(
            SingletonActor.of(
                Behaviors.supervise(CounterActor.create("singleId"))
                    .onFailure(SupervisorStrategy.restartWithBackoff(
                        Duration.ofSeconds(1), Duration.ofSeconds(2), 0.2)
                    ),
                "GlobalCounter"))

    }

    @PreDestroy
    fun shutdown() {
        mainStage.terminate()
    }

    fun getMainStage(): ActorSystem<MainStageActorCommand> {
        return mainStage
    }

    fun getSingleCount(): ActorRef<CounterCommand> {
        return singleCount
    }

    fun getHelloState(): ActorSystem<HelloStateActorCommand> {
        return helloState
    }

    fun getScheduler() = mainStage.scheduler()

}