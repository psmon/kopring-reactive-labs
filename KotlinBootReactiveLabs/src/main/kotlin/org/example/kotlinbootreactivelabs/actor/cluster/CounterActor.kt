package org.example.kotlinbootreactivelabs.actor.cluster

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.example.kotlinbootreactivelabs.actor.persistent.PersitenceSerializable


sealed class CounterCommand : PersitenceSerializable

data class Increment @JsonCreator constructor(
    @JsonProperty("value") val value: Int
) : CounterCommand()

data class GetCount @JsonCreator constructor(
    @JsonProperty("replyTo") val replyTo: ActorRef<CounterState>
) : CounterCommand()


object GoodByeCounter : CounterCommand()

data class CounterState @JsonCreator constructor(
    @JsonProperty("count") val count: Int
) : PersitenceSerializable


class CounterActor(context: ActorContext<CounterCommand>, val entityId: String) : AbstractBehavior<CounterCommand>(context) {

    companion object {
        fun create(entityId: String): Behavior<CounterCommand> {
            return Behaviors.setup { context -> CounterActor(context, entityId) }
        }
    }

    private var count = 0

    init {
        // TODO : Recoverty LastState by Persistence
        count = 5
    }

    override fun createReceive(): Receive<CounterCommand> {
        return newReceiveBuilder()
            .onMessage(Increment::class.java) { command ->
                count += command.value
                context.log.info("Count incremented to $count")
                this
            }
            .onMessage(GetCount::class.java) { commnad ->
                context.log.info("Current count is $count")
                commnad.replyTo.tell(CounterState(count))
                this
            }
            .onMessage(GoodByeCounter::class.java) { commnad->
                context.log.info("Goodbye Counter")
                throw IllegalStateException("crash..")
                this
            }
            .build()
    }


}