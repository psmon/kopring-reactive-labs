package org.example.kotlinbootreactivelabs.actor.state.store

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.StashBuffer
import org.example.kotlinbootreactivelabs.repositories.durable.DurableRepository
import org.apache.pekko.Done

// https://nightlies.apache.org/pekko/docs/pekko/1.1/docs/typed/stash.html

sealed class HelloStashCommand

//Stash
data class InitialState(val happyState: HappyStashState, val helloCount: Int, val helloTotalCount: Int) : HelloStashCommand()
data class DbError(val error:RuntimeException) : HelloStashCommand()
data class SavaState(val state: HelloStashState, val replyTo:ActorRef<Done>) : HelloStashCommand()
data class GetState(val replyTo:ActorRef<HelloStashState>) : HelloStashCommand()
data object SaveSuccess : HelloStashCommand()

/** 상태 정의 */
enum class HappyStashState {
    HAPPY, ANGRY
}

data class HelloStashState (
    var happyState: HappyStashState,
    var helloCount: Int,
    var helloTotalCount: Int
)

class HelloStashActor private constructor(
    private val context: ActorContext<HelloStashCommand>,
    private val persistenceId : String,
    private val durableRepository: DurableRepository,
) {

    companion object {
        fun create(persistenceId: String, durableRepository: DurableRepository): Behavior<HelloStashCommand> {

            return Behaviors.withStash(100, {
                    Behaviors.setup {
                        context ->
                            context.pipeToSelf(
                                durableRepository.findByIdEx<HelloStashState>(persistenceId, 1L).toFuture(),
                                { value, cause ->
                                    if (cause == null) {
                                        if(value == null) {
                                            // First State
                                            InitialState(HappyStashState.HAPPY, 0, 0)
                                        } else {
                                            InitialState(value.happyState, value.helloCount, value.helloTotalCount)
                                        }
                                    } else {
                                        DbError(RuntimeException(cause))
                                    }
                                })

                            HelloStashActor(context, persistenceId, durableRepository).start()
                    }
                })
        }
    }

    private lateinit var buffer: StashBuffer<HelloStashCommand>

    init {
        context.log.info("Create HelloStashStoreActor")
    }

    private fun start() : Behavior<HelloStashCommand> {
        return Behaviors.receive(HelloStashCommand::class.java)
            .onMessage(InitialState::class.java, this::onInitialState)
            .onMessage(DbError::class.java, this::onDBError)
            .onMessage(HelloStashCommand::class.java, this::stashOtherCommand)
            .build()
    }

    private fun onInitialState(command: InitialState): Behavior<HelloStashCommand> {
        var state:HelloStashState = HelloStashState(command.happyState, command.helloCount, command.helloTotalCount)
        return buffer.unstashAll(active(state));
    }

    private fun onDBError(command: DbError): Behavior<HelloStashCommand> {
        throw command.error;
    }

    private fun stashOtherCommand(command: HelloStashCommand): Behavior<HelloStashCommand> {
        buffer.stash(command)
        return Behaviors.same()
    }

    private fun active(state:HelloStashState) : Behavior<HelloStashCommand>{
        return Behaviors.receive(HelloStashCommand::class.java)
            .onMessage(GetState::class.java, {message -> onGetState(state, message)})
            .onMessage(SavaState::class.java, this::onSaveState)
            .build()
    }

    private fun onGetState(state:HelloStashState, message: GetState): Behavior<HelloStashCommand> {
        message.replyTo.tell(state)
        return Behaviors.same()
    }

    private fun onSaveState( message: SavaState): Behavior<HelloStashCommand> {
        context.pipeToSelf(
            durableRepository.createOrUpdateDurableStateEx<HelloStashState>(persistenceId, 1L, message.state).toFuture(),
            { value, cause ->
                if (cause == null) {
                    message.replyTo.tell(Done.getInstance())
                    SaveSuccess
                } else {
                    DbError(RuntimeException(cause))
                }
            })
        return saving(message.state, message.replyTo)
    }


    private fun saving(state:HelloStashState, replyTo:ActorRef<Done>) : Behavior<HelloStashCommand> {
        return Behaviors.receive(HelloStashCommand::class.java)
            .onMessage(SaveSuccess::class.java, {message -> onSaveSucess(state, replyTo)})
            .onMessage(DbError::class.java, this::onDBError)
            .onMessage(HelloStashCommand::class.java, this::stashOtherCommand)
            .build()
    }

    private fun onSaveSucess(state:HelloStashState, replyTo: ActorRef<Done>): Behavior<HelloStashCommand> {
        replyTo.tell(Done.getInstance())
        return buffer.unstashAll(active(state));
    }
}