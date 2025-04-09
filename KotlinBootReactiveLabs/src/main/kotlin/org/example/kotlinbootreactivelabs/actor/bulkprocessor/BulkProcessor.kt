package org.example.kotlinbootreactivelabs.actor.bulkprocessor

import org.apache.pekko.actor.Cancellable
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import java.time.Duration

sealed class BulkProcessorCommand
data class DataEvent(val data: Any, val replyTo: ActorRef<Any>) : BulkProcessorCommand()
object Flush : BulkProcessorCommand()
private object FlushTimeout : BulkProcessorCommand() // 내부 타임아웃 이벤트

sealed class BulkProcessorResponse
data class BulkTaskCompleted(val message: String) : BulkProcessorResponse()


class BulkProcessor private constructor(
    context: ActorContext<BulkProcessorCommand>,
    private val buffer: MutableList<Any> = mutableListOf()
) : AbstractBehavior<BulkProcessorCommand>(context) {

    companion object {
        fun create(): Behavior<BulkProcessorCommand> {
            return Behaviors.setup { context ->
                BulkProcessor(context)
            }
        }
    }

    override fun createReceive(): Receive<BulkProcessorCommand> {
        return idle()
    }

    private fun idle(): Receive<BulkProcessorCommand> {
        return newReceiveBuilder()
            .onMessage(DataEvent::class.java) { event ->
                if (event.data == "testend") {
                    event.replyTo.tell(BulkTaskCompleted("Bulk task completed"))
                    flushBuffer()
                    idle()
                } else {
                    context.log.info("Received first DataEvent, switching to active state.")
                    buffer.add(event.data)
                    startFlushTimer()
                    active()
                }
            }
            .onMessage(Flush::class.java) {
                // Idle 상태에서 Flush 명령이 오면 무시하거나 로깅
                Behaviors.same()
            }
            .build()
    }

    private fun active(): Receive<BulkProcessorCommand> {
        return newReceiveBuilder()
            .onMessage(DataEvent::class.java) { event ->
                if (event.data == "testend") {
                    event.replyTo.tell(BulkTaskCompleted("Bulk task completed"))
                    flushBuffer()
                    idle()
                } else {
                    buffer.add(event.data)
                    if (buffer.size >= 100) {
                        context.log.info("Buffer size reached 100, flushing data.")
                        flushBuffer()
                        idle()
                    } else {
                        Behaviors.same()
                    }
                }
            }
            .onMessage(Flush::class.java) {
                context.log.info("Flush command received, flushing data.")
                flushBuffer()
                idle()
            }
            .onMessageEquals(FlushTimeout) {
                context.log.info("Flush timeout reached, flushing data.")
                flushBuffer()
                idle()
            }
            .build()
    }

    private fun flushBuffer() {
        // 실제 벌크 처리를 수행하는 로직을 여기에 구현
        context.log.info("Processing ${buffer.size} events.")
        buffer.clear()
        stopFlushTimer()
    }

    // 타이머 관리
    private var flushTimer: Cancellable? = null

    private fun startFlushTimer() {
        flushTimer = context.scheduleOnce(
            Duration.ofSeconds(3),
            context.self,
            FlushTimeout
        )
    }

    private fun stopFlushTimer() {
        flushTimer?.cancel()
        flushTimer = null
    }
}