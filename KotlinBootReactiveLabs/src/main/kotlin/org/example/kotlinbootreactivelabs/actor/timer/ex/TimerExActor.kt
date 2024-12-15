package org.example.kotlinbootreactivelabs.actor.timer.ex

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.javadsl.TimerScheduler
import java.time.Duration
import java.time.Instant

sealed class TimerActorCommand
object TimerLoop : TimerActorCommand()
object TimerStop : TimerActorCommand()
object TimerResume : TimerActorCommand()

data class AssignTask(val key: String, val timestamp: Instant) : TimerActorCommand()
data class AcceptTask(val key: String, val timestamp: Instant) : TimerActorCommand()
data class RejectTask(val key: String, val timestamp: Instant) : TimerActorCommand()
data class CompleteTask(val key: String, val timestamp: Instant) : TimerActorCommand()
data class CheckMonitor(val timestamp: Instant) : TimerActorCommand()

sealed class TimerActorState
object WaitingForTask : TimerActorState()
object TaskAssigned : TimerActorState()
object TaskAccepted : TimerActorState()
object TaskRejected : TimerActorState()
object TaskCompleted : TimerActorState()


data class StateTransitionLog(val key: String, val state: TimerActorState, val timestamp: Instant)

class TimerExActor private constructor(
    private val context: ActorContext<TimerActorCommand>,
    private val timers: TimerScheduler<TimerActorCommand>
) : AbstractBehavior<TimerActorCommand>(context) {

    private var timerKey: Unit
    private var timerCount = 0
    private val slot: MutableMap<String, TimerActorState> = mutableMapOf()
    private val stateTransitionLogs: MutableList<StateTransitionLog> = mutableListOf()

    private var fakeNowTime: Instant = Instant.now()

    companion object {
        fun create(): Behavior<TimerActorCommand> {
            return Behaviors.withTimers { timers ->
                Behaviors.setup { context -> TimerExActor(context, timers) }
            }
        }
    }

    init {
        timerKey = timers.startTimerAtFixedRate(TimerLoop, Duration.ofSeconds(1))
    }

    override fun createReceive(): Receive<TimerActorCommand> {
        return newReceiveBuilder()
            .onMessage(TimerLoop::class.java, this::onTimerLoop)
            .onMessage(TimerStop::class.java, this::onTimerStop)
            .onMessage(TimerResume::class.java, this::onTimerResumed)
            .onMessage(AssignTask::class.java, this::onAssignTask)
            .onMessage(AcceptTask::class.java, this::onAcceptTask)
            .onMessage(CompleteTask::class.java, this::onCompleteTask)
            .onMessage(RejectTask::class.java, this::onRejectedTask)
            .onMessage(CheckMonitor::class.java, this::onCheckMonitor)
            .build()
    }

    private fun onTimerLoop(command: TimerLoop): Behavior<TimerActorCommand> {
        timerCount++
        //val now = Instant.now()
        val now = fakeNowTime
        val tasksToReject = stateTransitionLogs.filter {
            it.state == TaskAssigned && Duration.between(it.timestamp, now).seconds >= 30
        }

        tasksToReject.forEach {
            // Timer에의해 자동 Rejected 수행시 주석해제
            //onRejectedTask(RejectTask(it.key, now))
        }
        return this
    }

    private fun onTimerStop(command: TimerStop): Behavior<TimerActorCommand> {
        if (timers.isTimerActive(timerKey)) {
            timers.cancel(TimerLoop)
        }
        return this
    }

    private fun onTimerResumed(command: TimerResume): Behavior<TimerActorCommand> {
        if (!timers.isTimerActive(timerKey)) {
            timerKey = timers.startTimerAtFixedRate(TimerLoop, Duration.ofSeconds(1))
        }
        return this
    }

    private fun onAssignTask(command: AssignTask): Behavior<TimerActorCommand> {
        fakeNowTime = command.timestamp

        if (slot[command.key] == null) {
            context.log.info("Task assigned with key: ${command.key}")
            slot[command.key] = TaskAssigned
            stateTransitionLogs.add(StateTransitionLog(command.key, TaskAssigned, command.timestamp  ))
        }
        return this
    }

    private fun onAcceptTask(command: AcceptTask): Behavior<TimerActorCommand> {
        fakeNowTime = command.timestamp

        if (slot[command.key] == TaskAssigned) {
            val previousLog = stateTransitionLogs.last { it.key == command.key && it.state == TaskAssigned }
            val duration = Duration.between(previousLog.timestamp, command.timestamp )
            context.log.info("Task accepted with key: ${command.key}, time taken: ${duration.toMillis()} ms")
            slot[command.key] = TaskAccepted
            stateTransitionLogs.add(StateTransitionLog(command.key, TaskAccepted, command.timestamp ))
        }
        return this
    }

    private fun onCompleteTask(command: CompleteTask): Behavior<TimerActorCommand> {
        fakeNowTime = command.timestamp

        if (slot[command.key] == TaskAccepted) {
            val previousLog = stateTransitionLogs.last { it.key == command.key && it.state == TaskAccepted }
            val duration = Duration.between(previousLog.timestamp, Instant.now())
            context.log.info("Task completed with key: ${command.key}, time taken: ${duration.toMillis()} ms")
            slot[command.key] = TaskCompleted
            stateTransitionLogs.add(StateTransitionLog(command.key, TaskCompleted, command.timestamp ))
        }
        return this
    }

    private fun onRejectedTask(command: RejectTask): Behavior<TimerActorCommand> {
        fakeNowTime = command.timestamp

        if (slot[command.key] == TaskAssigned) {
            val previousLog = stateTransitionLogs.last { it.key == command.key && it.state == TaskAssigned }
            val duration = Duration.between(previousLog.timestamp, fakeNowTime )
            context.log.info("Task Rejected with key: ${command.key}, time taken: ${duration.toMillis()} ms")
            slot[command.key] = TaskRejected
            stateTransitionLogs.add(StateTransitionLog(command.key, TaskRejected, command.timestamp ))
        }
        return this
    }

    private fun onCheckMonitor(command: CheckMonitor): Behavior<TimerActorCommand> {
        fakeNowTime = command.timestamp

        val monitorString = buildMonitorString()
        context.log.info(monitorString)
        // Here you can add code to send the monitorString to an external system if needed
        return this
    }

    private fun buildMonitorString(): String {
        val builder = StringBuilder()
        builder.append("Current Slot States and Transition Times:\n")

        val taskAssignedCount = slot.values.count { it == TaskAssigned }
        val taskAcceptedCount = slot.values.count { it == TaskAccepted }
        val taskRejectedCount = slot.values.count { it == TaskRejected }
        val taskCompletedCount = slot.values.count { it == TaskCompleted }

        builder.append("Task Counts:\n")
        builder.append("  TaskAssigned: $taskAssignedCount\n")
        builder.append("  TaskAccepted: $taskAcceptedCount\n")
        builder.append("  TaskRejected: $taskRejectedCount\n")
        builder.append("  TaskCompleted: $taskCompletedCount\n")

        slot.forEach { (key, state) ->
            builder.append("Key: $key, State: $state\n")
            val transitions = stateTransitionLogs.filter { it.key == key }
            transitions.zipWithNext { a, b ->
                val duration = Duration.between(a.timestamp, b.timestamp).toMillis()
                builder.append("  Transition from ${a.state::class.simpleName} to ${b.state::class.simpleName}: ${duration} ms\n")
            }
            val totalDuration = transitions.zipWithNext { a, b -> Duration.between(a.timestamp, b.timestamp) }
                .sumOf { it.toMillis() }
            builder.append("  Total Time: ${totalDuration} ms\n")
        }
        return builder.toString()
    }
}