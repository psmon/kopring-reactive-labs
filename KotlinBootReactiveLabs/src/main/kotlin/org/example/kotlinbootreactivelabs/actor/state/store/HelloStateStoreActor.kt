package org.example.kotlinbootreactivelabs.actor.state.store

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorRefResolver
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.PreRestart
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.stream.Materializer

import org.apache.pekko.stream.javadsl.Source
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.OverflowStrategy

import org.apache.pekko.stream.QueueOfferResult
import org.example.kotlinbootreactivelabs.actor.state.model.HappyState
import org.example.kotlinbootreactivelabs.actor.state.model.HelloStoreState
import org.example.kotlinbootreactivelabs.actor.supervisor.RestartChild
import org.example.kotlinbootreactivelabs.actor.supervisor.SupervisorCommand
import org.example.kotlinbootreactivelabs.repositories.durable.DurableRepository

import java.time.Duration

/** HelloStateActor 처리할 수 있는 명령들 */
sealed class HelloStateStoreActorCommand
data class HelloStore(val message: String, val replyTo: ActorRef<Any>) : HelloStateStoreActorCommand()
data class GetHelloCountStore(val replyTo: ActorRef<Any>) : HelloStateStoreActorCommand()
data class GetHelloTotalCountStore(val replyTo: ActorRef<Any>) : HelloStateStoreActorCommand()
data class ChangeStateStore(val newHappy: HappyState) : HelloStateStoreActorCommand()
data class HelloLimitStore(val message: String, val replyTo: ActorRef<Any>) : HelloStateStoreActorCommand()
object ResetHelloCountStore : HelloStateStoreActorCommand()
object StopResetTimer : HelloStateStoreActorCommand()

/** HelloStateActor 반환할 수 있는 응답들 */
sealed class HelloStateStoreActorResponse
data class HelloResponseStore(val message: String) : HelloStateStoreActorResponse()
data class HelloCountResponseStore(val count: Int) : HelloStateStoreActorResponse()


/** HelloStateActor 클래스 */
class HelloStateStoreActor private constructor(
    private val context: ActorContext<HelloStateStoreActorCommand>,
    private val persistenceId : String,
    private val durableRepository: DurableRepository,
) : AbstractBehavior<HelloStateStoreActorCommand>(context) {

    companion object {
        fun create(persistenceId: String, durableRepository: DurableRepository): Behavior<HelloStateStoreActorCommand> {
            return Behaviors.setup { context -> HelloStateStoreActor(context, persistenceId, durableRepository) }
        }
    }

    private lateinit var helloStoreState: HelloStoreState

    init {
        context.log.info("Create HelloStateStoreActor - $persistenceId")
        initStoreState()
    }

    override fun createReceive(): Receive<HelloStateStoreActorCommand> {
        return newReceiveBuilder()
            .onMessage(HelloStore::class.java, this::onHello)
            .onMessage(HelloLimitStore::class.java, this::onHelloLimit)
            .onMessage(GetHelloCountStore::class.java, this::onGetHelloCount)
            .onMessage(GetHelloTotalCountStore::class.java, this::onGetHelloTotalCount)
            .onMessage(ChangeStateStore::class.java, this::onChangeState)
            .onMessage(ResetHelloCountStore::class.java, this::onResetHelloCount)
            .onSignal(PreRestart::class.java, this::onPreRestart)
            .build()
    }

    private val materializer = Materializer.createMaterializer(context.system)

    private val helloLimitSource = Source.queue<HelloLimitStore>(100, OverflowStrategy.backpressure())
        .throttle(3, Duration.ofSeconds(1))
        .to(Sink.foreach { cmd ->
            context.self.tell(HelloStore(cmd.message, cmd.replyTo))
        })
        .run(materializer)

    private fun onPreRestart(signal: PreRestart): Behavior<HelloStateStoreActorCommand> {
        context.log.info("HelloStateStoreActor actor restart: ")
        val parentPath = context.self.path().parent()
        val parentRef: ActorRef<SupervisorCommand> = ActorRefResolver.get(context.system).resolveActorRef(parentPath.toString())
        parentRef.tell(RestartChild(context.self.path().name(), context.self))

        return this
    }

    private fun onHello(command: HelloStore): Behavior<HelloStateStoreActorCommand> {

        if(command.message == "Crash") {
            throw RuntimeException("Crash")
        }

        when (helloStoreState.happyState) {
            HappyState.HAPPY -> {
                if (command.message == "Hello") {
                    helloStoreState.helloCount++
                    helloStoreState.helloTotalCount++

                    persist(helloStoreState, true)

                    command.replyTo.tell(HelloResponseStore("Kotlin"))
                    context.log.info("onHello-Kotlin - helloCount: ${helloStoreState.helloCount}")
                }
            }
            HappyState.ANGRY -> {
                command.replyTo.tell(HelloResponseStore("Don't talk to me!"))
            }
        }
        return this
    }

    private fun onHelloLimit(command: HelloLimitStore): Behavior<HelloStateStoreActorCommand> {
        helloLimitSource.offer(command).thenAccept { result ->
            when (result) {
                is QueueOfferResult.`Enqueued$` -> context.log.info("Command enqueued successfully")
                is QueueOfferResult.`Dropped$` -> context.log.error("Command dropped")
                is QueueOfferResult.Failure -> context.log.error("Failed to enqueue command", result.cause())
                is QueueOfferResult.`QueueClosed$` -> context.log.error("Queue was closed")
            }
        }
        return this
    }

    private fun onGetHelloCount(command: GetHelloCountStore): Behavior<HelloStateStoreActorCommand> {
        command.replyTo.tell(HelloCountResponseStore(helloStoreState.helloCount))
        context.log.info("onGetHelloCount-helloCount: ${helloStoreState.helloCount}")
        return this
    }

    private fun onGetHelloTotalCount(command: GetHelloTotalCountStore): Behavior<HelloStateStoreActorCommand> {
        command.replyTo.tell(HelloCountResponseStore(helloStoreState.helloTotalCount))
        return this
    }

    private fun onChangeState(command: ChangeStateStore): Behavior<HelloStateStoreActorCommand> {
        helloStoreState.happyState = command.newHappy
        persist(helloStoreState, true)
        return this
    }

    private fun onResetHelloCount(command: ResetHelloCountStore): Behavior<HelloStateStoreActorCommand> {
        context.log.info("Reset hello count")
        helloStoreState.helloCount = 0
        persist(helloStoreState, true)
        return this
    }

    private fun persist(newState: HelloStoreState, isAsync: Boolean = false) {
        if(isAsync) {
            persistAsync(newState)
        }
        else {
            runBlocking {
                durableRepository.createOrUpdateDurableStateEx(persistenceId, 1L, newState).awaitSingle()
            }
        }
    }

    private fun persistAsync(newState: HelloStoreState) {
        Source.single(newState)
            .mapAsync(1) { state ->
                durableRepository.createOrUpdateDurableStateEx(persistenceId, 1L, state).toFuture()
            }
            .runWith(Sink.ignore(), materializer)
    }

    private fun initStoreState() {
        runBlocking {
            var isNew: Boolean = false
            var result = durableRepository.findByIdEx<HelloStoreState>(persistenceId, 1L).awaitFirstOrNull()
            if(result != null) {
                helloStoreState = result
            }
            else{
                isNew = true
                helloStoreState = HelloStoreState(HappyState.HAPPY, 0, 0)
            }

            context.log.info("Init HelloStoreState - isNew: $isNew, $helloStoreState")

        }
    }
}