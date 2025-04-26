package org.example.kotlinbootreactivelabs.actor.core

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class KHelloCommand
data class KHello(val message: String, val replyTo: Channel<KHelloResponse>) : KHelloCommand()
data class KHelloResponse(val message: String)

class KHelloActor {
    private val channel = Channel<KHelloCommand>()

    fun start(): Job {
        return GlobalScope.launch {
            for (command in channel) {
                if (!isActive) break // 코루틴이 취소되면 루프 종료
                when (command) {
                    is KHello -> {
                        if (command.message == "Hello") {
                            command.replyTo.send(KHelloResponse("Kotlin"))
                        }
                    }
                }
            }
        }
    }

    suspend fun send(command: KHelloCommand) {
        channel.send(command)
    }

    fun close() {
        channel.close()
    }
}