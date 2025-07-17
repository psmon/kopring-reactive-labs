package com.example.ssepushsystem.config

import com.example.ssepushsystem.actor.TopicManagerActor
import com.example.ssepushsystem.actor.UserEventActor
import com.example.ssepushsystem.model.CborSerializable
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.time.Duration
import java.util.concurrent.CompletableFuture

// Commands
sealed class MainStageActorCommand : CborSerializable
data class CreateTopicManager(val replyTo: ActorRef<TopicManagerCreated>) : MainStageActorCommand()
data class CreateUserEventActor(val replyTo: ActorRef<UserEventActorCreated>) : MainStageActorCommand()

// Responses
data class TopicManagerCreated(val actorRef: ActorRef<TopicManagerActor.Command>) : CborSerializable
data class UserEventActorCreated(val actorRef: ActorRef<UserEventActor.Command>) : CborSerializable

// Main Stage Actor
class MainStageActor private constructor(
    context: ActorContext<MainStageActorCommand>
) : AbstractBehavior<MainStageActorCommand>(context) {
    
    private var topicManagerRef: ActorRef<TopicManagerActor.Command>? = null
    private var userEventRef: ActorRef<UserEventActor.Command>? = null
    
    companion object {
        fun create(): Behavior<MainStageActorCommand> {
            return Behaviors.setup { context -> MainStageActor(context) }
        }
    }
    
    override fun createReceive(): Receive<MainStageActorCommand> {
        return newReceiveBuilder()
            .onMessage(CreateTopicManager::class.java, this::onCreateTopicManager)
            .onMessage(CreateUserEventActor::class.java, this::onCreateUserEventActor)
            .build()
    }
    
    private fun onCreateTopicManager(command: CreateTopicManager): Behavior<MainStageActorCommand> {
        if (topicManagerRef == null) {
            topicManagerRef = context.spawn(
                Behaviors.supervise(TopicManagerActor.create())
                    .onFailure(SupervisorStrategy.resume()),
                "topicManager"
            )
            context.watch(topicManagerRef!!)
        }
        command.replyTo.tell(TopicManagerCreated(topicManagerRef!!))
        return this
    }
    
    private fun onCreateUserEventActor(command: CreateUserEventActor): Behavior<MainStageActorCommand> {
        // Ensure TopicManager exists first
        if (topicManagerRef == null) {
            topicManagerRef = context.spawn(
                Behaviors.supervise(TopicManagerActor.create())
                    .onFailure(SupervisorStrategy.resume()),
                "topicManager"
            )
            context.watch(topicManagerRef!!)
        }
        
        if (userEventRef == null) {
            userEventRef = context.spawn(
                Behaviors.supervise(UserEventActor.create(topicManagerRef!!))
                    .onFailure(SupervisorStrategy.resume()),
                "userEventActor"
            )
            context.watch(userEventRef!!)
        }
        
        command.replyTo.tell(UserEventActorCreated(userEventRef!!))
        return this
    }
}

@Configuration
class ActorSystemConfig {
    
    private lateinit var mainStage: ActorSystem<MainStageActorCommand>
    private lateinit var topicManagerActor: CompletableFuture<ActorRef<TopicManagerActor.Command>>
    private lateinit var userEventActor: CompletableFuture<ActorRef<UserEventActor.Command>>
    
    @PostConstruct
    fun init() {
        mainStage = ActorSystem.create(MainStageActor.create(), "SSEPushSystem")
        initializeActors()
    }
    
    private fun initializeActors() {
        // Create TopicManager
        topicManagerActor = createActor { CreateTopicManager(it) }
            .thenApply { it.actorRef }
        
        // Create UserEventActor (depends on TopicManager)
        userEventActor = createActor { CreateUserEventActor(it) }
            .thenApply { it.actorRef }
    }
    
    private inline fun <reified T : CborSerializable> createActor(
        crossinline commandFactory: (ActorRef<T>) -> MainStageActorCommand
    ): CompletableFuture<T> {
        return AskPattern.ask(
            mainStage,
            { replyTo: ActorRef<T> -> commandFactory(replyTo) },
            Duration.ofSeconds(5),
            mainStage.scheduler()
        ).toCompletableFuture()
    }
    
    @Bean
    fun actorSystem(): ActorSystem<Nothing> {
        @Suppress("UNCHECKED_CAST")
        return mainStage as ActorSystem<Nothing>
    }
    
    @Bean
    fun topicManagerActor(): ActorRef<TopicManagerActor.Command> {
        return topicManagerActor.get()
    }
    
    @Bean
    fun userEventActor(): ActorRef<UserEventActor.Command> {
        return userEventActor.get()
    }
    
    @PreDestroy
    fun terminate() {
        mainStage.terminate()
    }
}