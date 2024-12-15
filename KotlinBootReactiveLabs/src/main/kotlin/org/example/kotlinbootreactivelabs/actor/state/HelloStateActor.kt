package org.example.kotlinbootreactivelabs.actor.state

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.stream.Materializer

import org.apache.pekko.stream.javadsl.Source
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.OverflowStrategy

import org.apache.pekko.actor.typed.javadsl.TimerScheduler
import org.apache.pekko.stream.QueueOfferResult

import java.time.Duration

/** HelloStateActor 처리할 수 있는 명령들 */
sealed class HelloStateActorCommand
data class Hello(val message: String, val replyTo: ActorRef<Any>) : HelloStateActorCommand()
data class GetHelloCount(val replyTo: ActorRef<Any>) : HelloStateActorCommand()
data class GetHelloTotalCount(val replyTo: ActorRef<Any>) : HelloStateActorCommand()
data class ChangeState(val newHelloState: HelloState) : HelloStateActorCommand()
data class HelloLimit(val message: String, val replyTo: ActorRef<Any>) : HelloStateActorCommand()
object ResetHelloCount : HelloStateActorCommand()
object StopResetTimer : HelloStateActorCommand()

/** HelloStateActor 반환할 수 있는 응답들 */
sealed class HelloStateActorResponse
data class HelloResponse(val message: String) : HelloStateActorResponse()
data class HelloCountResponse(val count: Int) : HelloStateActorResponse()

/** 상태 정의 */
enum class HelloState {
    HAPPY, ANGRY
}

/** HelloStateActor 클래스 */
class HelloStateActor private constructor(
    private val context: ActorContext<HelloStateActorCommand>,
    private val timers: TimerScheduler<HelloStateActorCommand>,
    private var helloState: HelloState
) : AbstractBehavior<HelloStateActorCommand>(context) {

    companion object {
        fun create(initialHelloState: HelloState): Behavior<HelloStateActorCommand> {
            return Behaviors.withTimers { timers ->
                Behaviors.setup { context -> HelloStateActor(context, timers, initialHelloState) }
            }
        }
    }

    init {
        timers.startTimerAtFixedRate(ResetHelloCount, Duration.ofSeconds(10))
    }

    override fun createReceive(): Receive<HelloStateActorCommand> {
        return newReceiveBuilder()
            .onMessage(Hello::class.java, this::onHello)
            .onMessage(HelloLimit::class.java, this::onHelloLimit)
            .onMessage(GetHelloCount::class.java, this::onGetHelloCount)
            .onMessage(GetHelloTotalCount::class.java, this::onGetHelloTotalCount)
            .onMessage(ChangeState::class.java, this::onChangeState)
            .onMessage(ResetHelloCount::class.java, this::onResetHelloCount)
            .onMessage(StopResetTimer::class.java) {
                timers.cancel(ResetHelloCount)
                Behaviors.same()
            }
            .build()
    }

    private var helloCount: Int = 0

    private var helloTotalCount: Int = 0

    private val materializer = Materializer.createMaterializer(context.system)

    private val helloLimitSource = Source.queue<HelloLimit>(100, OverflowStrategy.backpressure())
        .throttle(3, Duration.ofSeconds(1))
        .to(Sink.foreach { cmd ->
            context.self.tell(Hello(cmd.message, cmd.replyTo))
        })
        .run(materializer)

    private fun onHello(command: Hello): Behavior<HelloStateActorCommand> {
        when (helloState) {
            HelloState.HAPPY -> {
                if (command.message == "Hello") {
                    helloCount++
                    helloTotalCount++
                    command.replyTo.tell(HelloResponse("Kotlin"))
                    context.log.info("onHello-Kotlin")
                }
            }
            HelloState.ANGRY -> {
                command.replyTo.tell(HelloResponse("Don't talk to me!"))
            }
        }
        return this
    }

    private fun onHelloLimit(command: HelloLimit): Behavior<HelloStateActorCommand> {
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

    private fun onGetHelloCount(command: GetHelloCount): Behavior<HelloStateActorCommand> {
        command.replyTo.tell(HelloCountResponse(helloCount))
        context.log.info("onGetHelloCount-helloCount: $helloCount")
        return this
    }

    private fun onGetHelloTotalCount(command: GetHelloTotalCount): Behavior<HelloStateActorCommand> {
        command.replyTo.tell(HelloCountResponse(helloTotalCount))
        return this
    }

    private fun onChangeState(command: ChangeState): Behavior<HelloStateActorCommand> {
        helloState = command.newHelloState
        return this
    }

    private fun onResetHelloCount(command: ResetHelloCount): Behavior<HelloStateActorCommand> {
        context.log.info("Resetting hello count")
        helloCount = 0
        return this
    }
}