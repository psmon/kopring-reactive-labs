package com.example.actorstream.actor

import com.example.actorstream.model.*
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.stream.*
import org.apache.pekko.stream.javadsl.*
import java.time.Duration
import org.apache.pekko.japi.Pair

class StreamProcessorActor private constructor(
    context: ActorContext<StreamCommand>,
    private val replyTo: ActorRef<StreamCommand>
) : AbstractBehavior<StreamCommand>(context) {

    companion object {
        fun create(replyTo: ActorRef<StreamCommand>): Behavior<StreamCommand> {
            return Behaviors.setup { context ->
                StreamProcessorActor(context, replyTo)
            }
        }
    }

    private val materializer = Materializer.createMaterializer(context.system)

    override fun createReceive(): Receive<StreamCommand> {
        return newReceiveBuilder()
            .onMessage(ProcessText::class.java, this::onProcessText)
            .build()
    }

    private fun onProcessText(command: ProcessText): Behavior<StreamCommand> {
        context.log.info("Processing text: ${command.text}")
        
        val graph = RunnableGraph.fromGraph(
            GraphDSL.create { builder ->
                // Source
                val source = builder.add(Source.single(command.text))
                
                // Splitter - separates words and numbers
                val splitter = builder.add(Flow.of(String::class.java)
                    .map { text ->
                        val parts = text.split("\\s+".toRegex())
                        val words = parts.filter { it.matches("[^\\d]+".toRegex()) && it.isNotBlank() }
                        val numbers = parts.filter { it.matches("\\d+".toRegex()) }.map { it.toInt() }
                        Pair.create(words, numbers)
                    })
                
                // Broadcast for parallel processing
                val broadcast = builder.add(Broadcast.create<Pair<List<String>, List<Int>>>(2))
                
                // Words processor with throttling
                val wordsFlow = builder.add(Flow.of(Pair::class.java as Class<Pair<List<String>, List<Int>>>)
                    .map { it.first() }
                    .buffer(100, OverflowStrategy.backpressure())
                    .throttle(10, Duration.ofMillis(100)))
                
                // Numbers processor with throttling
                val numbersFlow = builder.add(Flow.of(Pair::class.java as Class<Pair<List<String>, List<Int>>>)
                    .map { it.second().sum() }
                    .buffer(100, OverflowStrategy.backpressure())
                    .throttle(10, Duration.ofMillis(100)))
                
                // Zip to combine results
                val zip = builder.add(Zip.create<List<String>, Int>())
                
                // Final result creator
                val resultFlow = builder.add(Flow.fromFunction<Pair<List<String>, Int>, StreamResult> { pair ->
                    StreamResult(
                        originalText = command.text,
                        words = pair.first(),
                        wordCount = pair.first().size,
                        numberSum = pair.second()
                    )
                })
                
                // Sink
                val sink = builder.add(Sink.foreach<StreamResult> { result ->
                    context.log.info("Stream result: Words=${result.words}, Count=${result.wordCount}, Sum=${result.numberSum}")
                    replyTo.tell(result)
                    replyTo.tell(StreamCompleted)
                })
                
                // Connect the flow
                builder.from(source.out()).via(splitter).viaFanOut(broadcast)
                builder.from(broadcast.out(0)).via(wordsFlow).toInlet(zip.in0())
                builder.from(broadcast.out(1)).via(numbersFlow).toInlet(zip.in1())
                builder.from(zip.out()).via(resultFlow).to(sink)
                
                ClosedShape.getInstance()
            }
        )
        
        graph.run(materializer)
        return this
    }
}