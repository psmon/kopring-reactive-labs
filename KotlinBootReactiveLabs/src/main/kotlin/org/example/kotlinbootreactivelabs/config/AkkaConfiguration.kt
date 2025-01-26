package org.example.kotlinbootreactivelabs.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import org.apache.pekko.cluster.typed.Cluster
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

@Configuration
class AkkaConfiguration {

    private val logger: org.slf4j.Logger = LoggerFactory.getLogger(AkkaConfiguration::class.java)

    private lateinit var mainStage: ActorSystem<MainStageActorCommand>

    private lateinit var helloState: ActorSystem<HelloStateActorCommand>

    private lateinit var helloStateStore : ActorSystem<HelloStateStoreActorCommand>

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
        helloState = ActorSystem.create(HelloStateActor.create(HelloState.HAPPY), "HelloStateActor")
        helloStateStore = ActorSystem.create(HelloStateStoreActor.create("test-perstistid-00001",durableRepository), "helloStateStore")

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

        if (selfMember.hasRole("shard")) {
            logger.info("My Application Role shard")
            for (i in 1..1000) {
                val entityId = "test-$i"
                var typeKey = EntityTypeKey.create(CounterCommand::class.java, entityId)

                var shardSystem = ClusterSharding.get(mainStage)

                var shardCountActorEx = shardSystem.init(Entity.of(typeKey,
                    { entityContext -> CounterActor.create(entityContext.entityId) }
                ))
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        mainStage.terminate()
    }

    fun getMainStage(): ActorSystem<MainStageActorCommand> {
        return mainStage
    }

    fun getHelloState(): ActorSystem<HelloStateActorCommand> {
        return helloState
    }

    fun getScheduler() = mainStage.scheduler()

}