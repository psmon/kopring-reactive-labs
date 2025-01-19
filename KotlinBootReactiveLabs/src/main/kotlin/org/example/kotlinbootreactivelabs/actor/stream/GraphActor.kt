package org.example.kotlinbootreactivelabs.actor.stream

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.PostStop
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.javadsl.Flow
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.javadsl.Source
import java.time.Duration

sealed class GraphCommand
data class ProcessNumber(val number: Int, val replyTo: ActorRef<GraphCommand>) : GraphCommand()
data class ProcessedNumber(val result: Int) : GraphCommand()
object SwitchToMultiply : GraphCommand()
object SwitchToAdd : GraphCommand()

class GraphActor private constructor(
    context: ActorContext<GraphCommand>,
    private var operation: Flow<Int, Int, *>
) : AbstractBehavior<GraphCommand>(context) {

    companion object {
        fun create(): Behavior<GraphCommand> {
            return Behaviors.setup { context ->
                val initialOperation = Flow.of(Int::class.java).map { it + 1 }
                GraphActor(context, initialOperation)
            }
        }
    }

    private val materializer = Materializer.createMaterializer(context.system)

    override fun createReceive(): Receive<GraphCommand> {
        return newReceiveBuilder()
            .onMessage(ProcessNumber::class.java, this::onProcessNumber)
            .onMessage(ProcessedNumber::class.java, this::onProcessedNumber)
            .onMessage(SwitchToMultiply::class.java, this::onSwitchToMultiply)
            .onMessage(SwitchToAdd::class.java, this::onSwitchToAdd)
            .onSignal(PostStop::class.java, this::onPostStop)
            .build()
    }

    private fun onPostStop(command: PostStop): Behavior<GraphCommand> {
        context.log.info("GraphActor onPostStop - ${context.self.path().name()}")
        return this
    }

    private fun onProcessNumber(command: ProcessNumber): Behavior<GraphCommand> {
        context.log.info("Received ProcessNumber command with number: ${command.number} - ${context.self.path().name()}")
        Source.single(command.number)
            .via(operation)
            .buffer(1000, OverflowStrategy.dropHead())
            .throttle(10, Duration.ofSeconds(1))
            .runWith(Sink.foreach { result -> command.replyTo.tell(ProcessedNumber(result)) }, materializer)
        return this
    }

    private fun onProcessedNumber(command: ProcessedNumber): Behavior<GraphCommand> {
        context.log.info("Received ProcessedNumber command with result: ${command.result} - ${context.self.path().name()}")
        return this
    }

    private fun onSwitchToMultiply(command: SwitchToMultiply): Behavior<GraphCommand> {
        context.log.info("Switching operation to multiply - ${context.self.path().name()}")
        operation = Flow.of(Int::class.java).map { it * 2 }
        return this
    }

    private fun onSwitchToAdd(command: SwitchToAdd): Behavior<GraphCommand> {
        context.log.info("Switching operation to add - ${context.self.path().name()}")
        operation = Flow.of(Int::class.java).map { it + 1 }
        return this
    }
}