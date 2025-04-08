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
    fun init() {
        val finalConfig = loadConfiguration()
        initializeMainStage(finalConfig)
        initializeActors()
        initializeHelloStateActors()
        initializeClusterRoles()
        initializeClusterSharding()
        initializeClusterSingleton()
    }

    private fun loadConfiguration(): Config {
        val baseConfig = ConfigFactory.load("application.conf")
        val clusterConfigName = System.getProperty("Cluster") ?: ""
        return if (clusterConfigName.isNotEmpty()) {
            val clusterConfig = ConfigFactory.load("$clusterConfigName.conf")
            clusterConfig.withFallback(baseConfig)
        } else {
            val clusterConfig = ConfigFactory.load("standalone.conf")
            clusterConfig.withFallback(baseConfig)
        }.also {
            logger.info("Starting Akka Actor System with config: $clusterConfigName")
        }
    }

    private fun initializeMainStage(config: Config) {
        mainStage = ActorSystem.create(MainStageActor.create(), "ClusterSystem", config)
    }

    private fun initializeActors() {
        simpleSessionActor = createActor { CreateSimpleSocketSessionManager(it) } as CompletableFuture<ActorRef<SimpleSessionCommand>>
        sessionManagerActor = createActor { CreateSocketSessionManager(it) } as CompletableFuture<ActorRef<UserSessionCommand>>
        supervisorChannelActor = createActor { CreateSupervisorChannelActor(it) } as CompletableFuture<ActorRef<SupervisorChannelCommand>>
    }

    private fun <T : MainStageActorResponse> createActor(
        command: (ActorRef<T>) -> MainStageActorCommand
    ): CompletableFuture<ActorRef<*>> {
        return AskPattern.ask(
            mainStage,
            command,
            Duration.ofSeconds(3),
            mainStage.scheduler()
        ).toCompletableFuture().thenApply { res ->
            when (res) {
                is SocketSimpleSessionManagerCreated -> {
                    logger.info("SocketSimpleSessionManager created: ${res.actorRef.path()}")
                    res.actorRef
                }
                is SocketSessionManagerCreated -> {
                    logger.info("SocketSessionManager created: ${res.actorRef.path()}")
                    res.actorRef
                }
                is SupervisorChannelActorCreated -> {
                    logger.info("SupervisorChannelActor created: ${res.actorRef.path()}")
                    res.actorRef
                }
                else -> throw IllegalStateException("Failed to create actor")
            }
        }
    }

    private fun initializeHelloStateActors() {
        helloState = ActorSystem.create(HelloStateActor.create(HelloState.HAPPY), "HelloStateActor")
        helloStateStore = ActorSystem.create(
            HelloStateStoreActor.create("test-perstistid-00001", durableRepository),
            "helloStateStore"
        )
    }

    private fun initializeClusterRoles() {
        val selfMember = Cluster.get(mainStage).selfMember()
        if (selfMember.hasRole("seed")) logger.info("My Application Role Seed")
        if (selfMember.hasRole("helloA")) logger.info("My Application Role HelloA")
        if (selfMember.hasRole("helloB")) logger.info("My Application Role HelloB")
    }

    private fun initializeClusterSharding() {
        val selfMember = Cluster.get(mainStage).selfMember()
        if (selfMember.hasRole("shard")) {
            logger.info("My Application Role shard")
            val shardSystem = ClusterSharding.get(mainStage)
            for (i in 1..100) {
                val entityId = "test-$i"
                val typeKey = EntityTypeKey.create(CounterCommand::class.java, entityId)
                shardSystem.init(Entity.of(typeKey) { CounterActor.create(it.entityId) })
            }
        }
    }

    private fun initializeClusterSingleton() {
        val single = ClusterSingleton.get(mainStage)
        singleCount = single.init(
            SingletonActor.of(
                Behaviors.supervise(CounterActor.create("singleId"))
                    .onFailure(SupervisorStrategy.restartWithBackoff(
                        Duration.ofSeconds(1), Duration.ofSeconds(2), 0.2
                    )),
                "GlobalCounter"
            )
        )
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