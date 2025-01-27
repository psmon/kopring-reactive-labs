package org.example.kotlinbootreactivelabs.actor.timer.basic

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.javadsl.TimerScheduler

import java.time.Duration

sealed class TimerActorCommand
object TimerLoop : TimerActorCommand()
object TimerStop : TimerActorCommand()
object TimerResume : TimerActorCommand()


class TimerActor private constructor(
    private val context: ActorContext<TimerActorCommand>,
    private val timers: TimerScheduler<TimerActorCommand>,
) : AbstractBehavior<TimerActorCommand>(context) {

    private var timerKey: Unit
    private var timerCount = 0

    companion object {
        fun create(): Behavior<TimerActorCommand> {
            return Behaviors.withTimers { timers ->
                Behaviors.setup { context -> TimerActor(context, timers) }
            }
        }
    }

    init {
        timerKey = timers.startTimerAtFixedRate(TimerLoop, Duration.ofSeconds(10))
        //timers.startSingleTimer(TimerStop, Duration.ofSeconds(5))
    }

    override fun createReceive(): Receive<TimerActorCommand> {
        return newReceiveBuilder()
            .onMessage(TimerLoop::class.java, this::onTimerLoop)
            .onMessage(TimerStop::class.java, this::onTimerStop)
            .onMessage(TimerResume::class.java, this::onTimerResumed)
            .build()
    }

    private fun onTimerLoop(command: TimerLoop): Behavior<TimerActorCommand> {
        timerCount++
        context.log.info("Timer loop - $timerCount")
        return this
    }

    private fun onTimerStop(command: TimerStop): Behavior<TimerActorCommand> {
        if (timers.isTimerActive(timerKey)) {
            context.log.info("Timer stopped")
            timers.cancel(timerKey)
        }
        return this
    }

    private fun onTimerResumed(command: TimerResume): Behavior<TimerActorCommand> {
        if (!timers.isTimerActive(timerKey)) {
            context.log.info("Timer resumed")
            timerKey = timers.startTimerAtFixedRate(TimerLoop, Duration.ofSeconds(1))
        }
        return this
    }
}