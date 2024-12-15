package org.example.kotlinbootreactivelabs.config

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.pekko.actor.typed.ActorSystem
import org.example.kotlinbootreactivelabs.actor.MainStageActor
import org.example.kotlinbootreactivelabs.actor.MainStageActorCommand
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
        logger.info("Starting Akka Actor System")

        mainStage = ActorSystem.create(MainStageActor.create(), "MainStageActor")

        helloState = ActorSystem.create(HelloStateActor.create(HelloState.HAPPY), "HelloStateActor")

        helloStateStore = ActorSystem.create(HelloStateStoreActor.create("test-perstistid-00001",durableRepository), "helloStateStore")

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