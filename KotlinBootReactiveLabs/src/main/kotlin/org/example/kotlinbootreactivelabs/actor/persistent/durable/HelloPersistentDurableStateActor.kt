package org.example.kotlinbootreactivelabs.actor.persistent.durable

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.state.javadsl.CommandHandler
import org.apache.pekko.persistence.typed.state.javadsl.DurableStateBehavior
import org.apache.pekko.persistence.typed.state.javadsl.Effect
import org.example.kotlinbootreactivelabs.actor.PersitenceSerializable

enum class State {
    HAPPY,
    ANGRY
}

data class HelloState(
    val state: State,
    val helloCount: Int,
    val helloTotalCount: Int
) : PersitenceSerializable


sealed class HelloPersistentStateActorCommand : PersitenceSerializable
data class HelloPersistentDurable(val message: String, val replyTo: ActorRef<Any>) : HelloPersistentStateActorCommand()
data class GetHelloCountPersistentDurable(val replyTo: ActorRef<Any>) : HelloPersistentStateActorCommand()
data class GetHelloTotalCountPersitentDurable(val replyTo: ActorRef<Any>) : HelloPersistentStateActorCommand()
data class ChangeState(val state:State) : HelloPersistentStateActorCommand()

object ResetHelloCount : HelloPersistentStateActorCommand()

sealed class HelloPersistentStateActorResponse : PersitenceSerializable
data class HelloResponse(val message: String) : HelloPersistentStateActorResponse()
data class HelloCountResponse(val count: Number) : HelloPersistentStateActorResponse()


class HelloPersistentDurableStateActor private constructor(
    private val context: ActorContext<HelloPersistentStateActorCommand>,
    private val persistenceId: PersistenceId
) : DurableStateBehavior<HelloPersistentStateActorCommand, HelloState>(persistenceId) {

    companion object {
        fun create(persistenceId: PersistenceId): Behavior<HelloPersistentStateActorCommand> {
            return Behaviors.setup { context -> HelloPersistentDurableStateActor(context, persistenceId) }
        }
    }

    override fun tag(): String {
        return "tag1"
    }

    override fun emptyState(): HelloState = HelloState(State.HAPPY, 0, 0)

    override fun commandHandler(): CommandHandler<HelloPersistentStateActorCommand, HelloState> {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(HelloPersistentDurable::class.java) { state, command -> onHello(state, command) }
            .onCommand(GetHelloCountPersistentDurable::class.java) { state, command -> onGetHelloCount(state, command) }
            .onCommand(GetHelloTotalCountPersitentDurable::class.java) { state, command -> onGetHelloTotalCount(state, command) }
            .onCommand(ChangeState::class.java) { state, command -> onChangeState(state, command) }
            .onCommand(ResetHelloCount::class.java) { state, _ -> onResetHelloCount(state) }
            .build()
    }


    private fun onHello(state: HelloState, command: HelloPersistentDurable): Effect<HelloState> {
        return when (state.state) {
            State.HAPPY -> {
                if (command.message == "Hello") {
                    context.log.info("onHello-Kotlin")
                    val newState = state.copy(
                        helloCount = state.helloCount + 1,
                        helloTotalCount = state.helloTotalCount + 1,
                    )

                    Effect().persist(newState).thenRun {
                        command.replyTo.tell(HelloResponse("Kotlin"))
                    }

                } else {
                    Effect().none()
                }
            }
            State.ANGRY -> {
                command.replyTo.tell(HelloResponse("Don't talk to me!"))
                Effect().none()
            }
        }
    }

    private fun onGetHelloCount(state: HelloState, command: GetHelloCountPersistentDurable): Effect<HelloState> {
        command.replyTo.tell(HelloCountResponse(state.helloCount))
        context.log.info("onGetHelloCount-helloCount: ${state.helloCount}")
        return Effect().none()
    }

    private fun onGetHelloTotalCount(state: HelloState, command: GetHelloTotalCountPersitentDurable): Effect<HelloState> {
        command.replyTo.tell(HelloCountResponse(state.helloTotalCount))
        return Effect().none()
    }

    private fun onChangeState(state: HelloState, command: ChangeState): Effect<HelloState> {
        val newState = state.copy(state = command.state )
        return Effect().persist(newState)
    }

    private fun onResetHelloCount(state: HelloState): Effect<HelloState> {
        context.log.info("Resetting hello count")
        val newState = state.copy(helloCount = 0)
        return Effect().persist(newState)
    }
}