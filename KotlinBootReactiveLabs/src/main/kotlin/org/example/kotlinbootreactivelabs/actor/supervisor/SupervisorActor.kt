package org.example.kotlinbootreactivelabs.actor.supervisor

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.Terminated
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.example.kotlinbootreactivelabs.actor.state.store.HelloStateStoreActor
import org.example.kotlinbootreactivelabs.actor.state.store.HelloStateStoreActorCommand
import org.example.kotlinbootreactivelabs.actor.state.store.HelloStore
import org.example.kotlinbootreactivelabs.repositories.durable.DurableRepository

sealed class SupervisorCommand
data class CreateChild(val name: String, val persistId: String, val replyTo: ActorRef<Any>) : SupervisorCommand()
data class RestartChild(val name: String, val childActor: ActorRef<HelloStateStoreActorCommand>) : SupervisorCommand()

data class SendHello(
    val childName: String,
    val message: String,
    val replyTo: ActorRef<Any>
) : SupervisorCommand()

data class GetChildCount(val replyTo: ActorRef<Int>) : SupervisorCommand()
data class TerminateChild(val name: String) : SupervisorCommand()

sealed class SupervisorResponse
data class CreatedChild(val actor: ActorRef<HelloStateStoreActorCommand>) : SupervisorResponse()


class SupervisorActor private constructor(
    context:  ActorContext<SupervisorCommand>,
    private val durableRepository: DurableRepository,
) : AbstractBehavior<SupervisorCommand>(context) {
    companion object {
        fun create(durableRepository: DurableRepository): Behavior<SupervisorCommand> {
            return Behaviors.setup { context -> SupervisorActor(context, durableRepository) }
        }
    }

    private val children = mutableMapOf<String, ActorRef<HelloStateStoreActorCommand>>()

    override fun createReceive(): Receive<SupervisorCommand> {
        return newReceiveBuilder()
            .onMessage(CreateChild::class.java, this::onCreateChild)
            .onMessage(RestartChild::class.java, this::onRestartChild)
            .onMessage(SendHello::class.java, this::onSendHello)
            .onMessage(GetChildCount::class.java, this::onGetChildCount)
            .onMessage(TerminateChild::class.java, this::onTerminateChild)
            .onSignal(Terminated::class.java, this::onChildTerminated)
            .build()
    }

    private fun onCreateChild(command :CreateChild): Behavior<SupervisorCommand> {
        val childName = command.name
        val childActor = context.spawn(
            Behaviors.supervise(HelloStateStoreActor.create(command.persistId, durableRepository))
                .onFailure(SupervisorStrategy.restart().withLoggingEnabled(true)),
            childName
        )

        context.watch(childActor)
        children[childName] = childActor

        command.replyTo.tell(CreatedChild(childActor))

        context.log.info("Created child actor with name: $childName, persistenceId: ${command.persistId}")
        return this
    }

    private fun onRestartChild(command : RestartChild): Behavior<SupervisorCommand> {
        children[command.name] = command.childActor
        context.log.info("Recovery child actor: ${command.name}")
        return this
    }

    private fun onSendHello(command: SendHello): Behavior<SupervisorCommand> {
        val child = children[command.childName]
        if (child != null) {
            child.tell(HelloStore(command.message, command.replyTo))
        } else {
            context.log.warn("Child actor [${command.childName}] does not exist.")
        }

        return this
    }

    private fun onGetChildCount(command: GetChildCount): Behavior<SupervisorCommand> {
        command.replyTo.tell(children.size)
        return this
    }

    private fun onChildTerminated(terminated: Terminated): Behavior<SupervisorCommand> {
        val childActor = terminated.ref
        val childName = childActor.path().name()
        children.remove(childName)
        context.log.info("Child actor terminated: $childName")
        return this
    }

    private fun onTerminateChild(command: TerminateChild): Behavior<SupervisorCommand> {
        val child = children[command.name]
        if (child != null) {
            context.stop(child)
            context.log.info("Terminated child actor: ${command.name}")
        } else {
            context.log.warn("Attempted to terminate non-existent child actor: ${command.name}")
        }
        return this
    }

}