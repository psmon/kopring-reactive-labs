package org.example.kotlinbootreactivelabs.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import org.example.kotlinbootreactivelabs.actor.CreateSimpleSocketSessionManager
import org.example.kotlinbootreactivelabs.actor.CreateSocketSessionManager
import org.example.kotlinbootreactivelabs.actor.CreateSupervisorChannelActor
import org.example.kotlinbootreactivelabs.actor.MainStageActor
import org.example.kotlinbootreactivelabs.actor.MainStageActorCommand
import org.example.kotlinbootreactivelabs.actor.MainStageActorResponse
import org.example.kotlinbootreactivelabs.actor.SocketSessionManagerCreated
import org.example.kotlinbootreactivelabs.actor.SocketSimpleSessionManagerCreated
import org.example.kotlinbootreactivelabs.actor.SupervisorChannelActorCreated
import org.example.kotlinbootreactivelabs.actor.cluster.CounterActor
import org.example.kotlinbootreactivelabs.actor.cluster.CounterCommand
import org.example.kotlinbootreactivelabs.actor.state.HelloState
import org.example.kotlinbootreactivelabs.actor.state.HelloStateActor
import org.example.kotlinbootreactivelabs.actor.state.HelloStateActorCommand
import org.example.kotlinbootreactivelabs.actor.state.store.HelloStateStoreActor
import org.example.kotlinbootreactivelabs.actor.state.store.HelloStateStoreActorCommand
import org.example.kotlinbootreactivelabs.repositories.durable.DurableRepository
import org.example.kotlinbootreactivelabs.ws.actor.basic.SimpleSessionCommand
import org.example.kotlinbootreactivelabs.ws.actor.chat.UserSessionCommand
import org.example.kotlinbootreactivelabs.ws.actor.chat.SupervisorChannelCommand
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.util.concurrent.CompletableFuture

@Configuration
class AkkaConfiguration {

    private val logger: org.slf4j.Logger = LoggerFactory.getLogger(AkkaConfiguration::class.java)

    private lateinit var mainStage: ActorSystem<MainStageActorCommand>

    private lateinit var helloState: ActorSystem<HelloStateActorCommand>

    private lateinit var helloStateStore : ActorSystem<HelloStateStoreActorCommand>

    private lateinit var singleCount: ActorRef<CounterCommand>

    // CompletableFuture is used to handle the async response from the actor

    private lateinit var simpleSessionActor: CompletableFuture<ActorRef<SimpleSessionCommand>>

    private lateinit var sessionManagerActor: CompletableFuture<ActorRef<UserSessionCommand>>

    private lateinit var supervisorChannelActor: CompletableFuture<ActorRef<SupervisorChannelCommand>>

    @Autowired lateinit var durableRepository: DurableRepository

    @PostConstruct
    fun init(){

        val baseConfig = ConfigFactory.load("application.conf")
        val clusterConfigName = System.getProperty("Cluster") ?: ""
        val finalConfig: Config = if (clusterConfigName.isNotEmpty()) {
            val clusterConfig = ConfigFactory.load("$clusterConfigName.conf")
            clusterConfig.withFallback(baseConfig)
        } else {
            val clusterConfig = ConfigFactory.load("standalone.conf")
            clusterConfig.withFallback(baseConfig)
        }

        logger.info("Starting Akka Actor System with config: $clusterConfigName")

        mainStage = ActorSystem.create(MainStageActor.create(), "ClusterSystem", finalConfig)

        simpleSessionActor = AskPattern.ask(
            mainStage,
            { replyTo: ActorRef<MainStageActorResponse> -> CreateSimpleSocketSessionManager(replyTo) },
            Duration.ofSeconds(3),
            mainStage.scheduler()
        ).toCompletableFuture().thenApply { res ->
            if (res is SocketSimpleSessionManagerCreated) {
                logger.info("SocketSimpleSessionManager created: ${res.actorRef.path()}")
                res.actorRef
            } else {
                throw IllegalStateException("Failed to create SocketSimpleSessionManager")
            }
        }

        sessionManagerActor = AskPattern.ask(
            mainStage,
            { replyTo: ActorRef<MainStageActorResponse> -> CreateSocketSessionManager(replyTo) },
            Duration.ofSeconds(3),
            mainStage.scheduler()
        ).toCompletableFuture().thenApply { res ->
            if (res is SocketSessionManagerCreated) {
                logger.info("SocketSessionManager created: ${res.actorRef.path()}")
                res.actorRef
            } else {
                throw IllegalStateException("Failed to create SocketSessionManager")
            }
        }


        supervisorChannelActor = AskPattern.ask(
            mainStage,
            { replyTo: ActorRef<MainStageActorResponse> -> CreateSupervisorChannelActor(replyTo) },
            Duration.ofSeconds(3),
            mainStage.scheduler()
        ).toCompletableFuture().thenApply { res ->
            if (res is SupervisorChannelActorCreated) {
                logger.info("SupervisorChannelActor created: ${res.actorRef.path()}")
                res.actorRef
            } else {
                throw IllegalStateException("Failed to create SupervisorChannelActor")
            }
        }

        // Local Actor
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

    // For Singleton
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

    // For Dependency Injection
    @Bean
    fun actorSystem(): ActorSystem<MainStageActorCommand> {
        return mainStage
    }

    @Bean
    fun supervisorChannelActor(): ActorRef<SupervisorChannelCommand> {
        return supervisorChannelActor.get()
    }

    @Bean
    fun simpleSessionActor(): ActorRef<SimpleSessionCommand> {
        return simpleSessionActor.get()
    }

    @Bean
    fun sessionManagerActor(): ActorRef<UserSessionCommand> {
        return sessionManagerActor.get()
    }

}