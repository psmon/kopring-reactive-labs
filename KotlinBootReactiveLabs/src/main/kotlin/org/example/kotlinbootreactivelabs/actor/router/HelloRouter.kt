package org.example.kotlinbootreactivelabs.actor.router

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.PoolRouter
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.javadsl.Routers
import org.example.kotlinbootreactivelabs.actor.state.Hello
import org.example.kotlinbootreactivelabs.actor.state.HelloState
import org.example.kotlinbootreactivelabs.actor.state.HelloStateActor
import org.example.kotlinbootreactivelabs.actor.state.HelloStateActorCommand

sealed class HelloRouterCommand
data class DistributedHello(val message: String, val replyTo: ActorRef<Any>) : HelloRouterCommand()

class HelloRouter private constructor(
        context: ActorContext<HelloRouterCommand>,
        private var router: PoolRouter<HelloStateActorCommand>,
        private var routerRef: ActorRef<HelloStateActorCommand>
): AbstractBehavior<HelloRouterCommand>(context) {

    companion object {
        fun create(poolSize: Int): Behavior<HelloRouterCommand> {
            return  Behaviors.setup { context ->
                val router = Routers.pool(poolSize, Behaviors.supervise(HelloStateActor.create(HelloState.HAPPY))
                    .onFailure(SupervisorStrategy.restart()))   // resume, restart, stop, escalate
                    .withRoundRobinRouting()
                    //.withBroadcastPredicate({ command -> command is HelloStateActorCommand })
                    //.withConsistentHashingRouting(0, { case: HelloStateActorCommand -> case.hashCode() })
                    //.withRandomRouting()
                    //.withRandomRouting()

                val routerRef: ActorRef<HelloStateActorCommand> = context.spawn(router, "hello-router-pool")
                HelloRouter(context, router, routerRef)
            }
        }
    }

    override fun createReceive(): Receive<HelloRouterCommand> {
        return newReceiveBuilder()
            .onMessage(DistributedHello::class.java, this::onDistributedHello)
            .build()
    }

    fun onDistributedHello(command: DistributedHello): Behavior<HelloRouterCommand> {
        routerRef.tell(Hello(command.message, command.replyTo))
        return this
    }
}