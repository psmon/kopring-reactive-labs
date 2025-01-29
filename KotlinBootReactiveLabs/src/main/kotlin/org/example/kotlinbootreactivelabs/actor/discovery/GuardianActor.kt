package org.example.kotlinbootreactivelabs.actor.discovery

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.receptionist.Receptionist
import org.example.kotlinbootreactivelabs.actor.PersitenceSerializable

sealed class GuardianActorCommand : PersitenceSerializable

data class KeepAlive @JsonCreator constructor(
    @JsonProperty("replyTo") val replyTo: ActorRef<Any>
) : GuardianActorCommand()

data class ReceptionCounter @JsonCreator constructor(
    @JsonProperty("replyTo") val replyTo: ActorRef<Int>
) : GuardianActorCommand()

class GuardianActor(context: ActorContext<Any>) : AbstractBehavior<Any>(context) {

    companion object {
        fun create(): Behavior<Any> {
            return Behaviors.setup { context ->

                context.system.receptionist()
                    .tell(Receptionist.subscribe(PingServiceActor.PingServiceActorKey, context.self.narrow()))

                GuardianActor(context)
            }
        }
    }

    var count = 0

    override fun createReceive(): Receive<Any> {
        return newReceiveBuilder()
            .onMessage(Receptionist.Listing::class.java, this::onListing)
            .onMessage(KeepAlive::class.java, this::onKeepAlive)
            .onMessage(ReceptionCounter::class.java) {
                it.replyTo.tell(count)
                Behaviors.same()
            }
            .build()
    }

    private fun onListing(command: Receptionist.Listing): Behavior<Any> {
        context.log.info("Listing received: ${command.getServiceInstances(PingServiceActor.PingServiceActorKey).count()}")

        count = command.getServiceInstances(PingServiceActor.PingServiceActorKey).count()

        command.getServiceInstances(PingServiceActor.PingServiceActorKey).forEach { serviceInstance ->
            context.log.info("onListing Service instance: ${serviceInstance.path()}")
            serviceInstance.tell(AutoPing)
        }

        return this
    }

    private fun onKeepAlive(command: KeepAlive): Behavior<Any> {
        command.replyTo.tell("Guardian is alive")
        return this
    }

}