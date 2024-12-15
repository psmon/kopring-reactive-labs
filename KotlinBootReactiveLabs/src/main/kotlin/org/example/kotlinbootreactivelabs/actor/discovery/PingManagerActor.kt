package org.example.kotlinbootreactivelabs.actor.discovery

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.receptionist.Receptionist

sealed class PingManagerActorCommand
data class PingAll(val message: String, val replyTo:ActorRef<PingManagerActorResponse>) : PingManagerActorCommand()
data class ListingResponse(val listing:Receptionist.Listing ) : PingManagerActorCommand()

sealed class PingManagerActorResponse
data class PingAllResponse(val message: String) : PingManagerActorResponse()

class PingManagerActor(context: ActorContext<PingManagerActorCommand>) : AbstractBehavior<PingManagerActorCommand>(context) {

    companion object {
        fun create(): Behavior<PingManagerActorCommand> {
            return Behaviors.setup { context -> PingManagerActor(context) }
        }
    }

    var listingResponseAdapter = context.messageAdapter(Receptionist.Listing::class.java, { ListingResponse(it) })

    init {
        context.spawnAnonymous(PingServiceActor.create())
    }

    override fun createReceive(): Receive<PingManagerActorCommand> {
        return newReceiveBuilder()
            .onMessage(PingAll::class.java, this::onPingAll)
            .onMessage(ListingResponse::class.java, this::onListingResponse)
            .build()
    }

    private fun onPingAll(command: PingAll): Behavior<PingManagerActorCommand> {
        context.log.info("Pinging all with message: ${command.message}")

        context.system.receptionist().tell(Receptionist.find(PingServiceActor.PingServiceActorKey, listingResponseAdapter))

        command.replyTo.tell(PingAllResponse("Pinging all"))

        return this
    }

    private fun onListingResponse(command: ListingResponse): Behavior<PingManagerActorCommand> {
        context.log.info("Listing received: ${command.listing.getServiceInstances(PingServiceActor.PingServiceActorKey).count()}")
        command.listing.getServiceInstances(PingServiceActor.PingServiceActorKey).forEach { serviceInstance ->
            context.log.info("onListing Service instance: ${serviceInstance.path()}")
            context.spawnAnonymous(PingerActor.create(serviceInstance)
        )}
        return this
    }
}