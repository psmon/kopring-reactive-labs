package com.example.ssepushsystem.actor

import com.example.ssepushsystem.model.CborSerializable
import com.example.ssepushsystem.model.TopicEvent
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

object UserEventActor {
    
    sealed class Command : CborSerializable
    data class Connect(
        val userId: String,
        val sink: Sinks.Many<TopicEvent>,
        val replyTo: ActorRef<Connected>
    ) : Command()
    data class Disconnect(val userId: String) : Command()
    data class SubscribeToTopic(val userId: String, val topic: String) : Command()
    data class UnsubscribeFromTopic(val userId: String, val topic: String) : Command()
    data class ReceiveEvent(val event: TopicEvent) : Command()
    private object Timeout : Command()
    
    data class Connected(val userId: String, val eventActor: ActorRef<TopicEvent>) : CborSerializable
    
    private data class UserConnection(
        val userId: String,
        val sink: Sinks.Many<TopicEvent>,
        val eventReceiver: ActorRef<TopicEvent>,
        val subscribedTopics: MutableSet<String> = mutableSetOf()
    )
    
    fun create(topicManager: ActorRef<TopicManagerActor.Command>): Behavior<Command> = Behaviors.setup { context ->
        val connections = ConcurrentHashMap<String, UserConnection>()
        
        Behaviors.withTimers { timers ->
            // Set up idle timeout
            timers.startTimerAtFixedRate(Timeout, Duration.ofMinutes(1))
            
            Behaviors.receiveMessage { message ->
                when (message) {
                    is Connect -> {
                        // Check if user already has a connection
                        connections[message.userId]?.let { existingConnection ->
                            // Clean up existing connection
                            existingConnection.subscribedTopics.forEach { topic ->
                                topicManager.tell(
                                    TopicManagerActor.Unsubscribe(message.userId, topic)
                                )
                            }
                            existingConnection.sink.tryEmitComplete()
                            context.stop(existingConnection.eventReceiver)
                            connections.remove(message.userId)
                            context.log.info("Cleaned up existing connection for user {}", message.userId)
                        }
                        
                        // Create event receiver actor for this user
                        val eventReceiver = context.spawn(
                            createEventReceiver(message.userId, context.self),
                            "event-receiver-${message.userId}-${System.currentTimeMillis()}"
                        )
                        
                        val connection = UserConnection(
                            userId = message.userId,
                            sink = message.sink,
                            eventReceiver = eventReceiver
                        )
                        
                        connections[message.userId] = connection
                        
                        // Send connected response
                        message.replyTo.tell(Connected(message.userId, eventReceiver))
                        
                        context.log.info("User {} connected", message.userId)
                        Behaviors.same()
                    }
                    
                    is Disconnect -> {
                        connections.remove(message.userId)?.let { connection ->
                            // Unsubscribe from all topics
                            connection.subscribedTopics.forEach { topic ->
                                topicManager.tell(
                                    TopicManagerActor.Unsubscribe(message.userId, topic)
                                )
                            }
                            
                            // Complete the sink
                            connection.sink.tryEmitComplete()
                            
                            // Stop the event receiver
                            context.stop(connection.eventReceiver)
                            
                            context.log.info("User {} disconnected", message.userId)
                        }
                        Behaviors.same()
                    }
                    
                    is SubscribeToTopic -> {
                        connections[message.userId]?.let { connection ->
                            if (connection.subscribedTopics.add(message.topic)) {
                                topicManager.tell(
                                    TopicManagerActor.Subscribe(
                                        message.userId,
                                        message.topic,
                                        connection.eventReceiver
                                    )
                                )
                                context.log.info("User {} subscribed to topic {}", message.userId, message.topic)
                            }
                        }
                        Behaviors.same()
                    }
                    
                    is UnsubscribeFromTopic -> {
                        connections[message.userId]?.let { connection ->
                            if (connection.subscribedTopics.remove(message.topic)) {
                                topicManager.tell(
                                    TopicManagerActor.Unsubscribe(message.userId, message.topic)
                                )
                                context.log.info("User {} unsubscribed from topic {}", message.userId, message.topic)
                            }
                        }
                        Behaviors.same()
                    }
                    
                    is ReceiveEvent -> {
                        // Find the user connection by checking which topics they're subscribed to
                        connections.values.find { connection ->
                            connection.subscribedTopics.contains(message.event.topic)
                        }?.let { connection ->
                            val emitResult = connection.sink.tryEmitNext(message.event)
                            if (emitResult.isFailure) {
                                context.log.warn(
                                    "Failed to emit event to user {}: {}",
                                    connection.userId,
                                    emitResult
                                )
                                // Disconnect user if sink is not working
                                context.self.tell(Disconnect(connection.userId))
                            }
                        }
                        Behaviors.same()
                    }
                    
                    is Timeout -> {
                        // Clean up any dead connections
                        val toRemove = mutableListOf<String>()
                        connections.forEach { (userId, connection) ->
                            if (connection.sink.currentSubscriberCount() == 0) {
                                toRemove.add(userId)
                            }
                        }
                        
                        toRemove.forEach { userId ->
                            context.self.tell(Disconnect(userId))
                        }
                        
                        Behaviors.same()
                    }
                }
            }
        }
    }
    
    private fun createEventReceiver(
        userId: String,
        parent: ActorRef<Command>
    ): Behavior<TopicEvent> = Behaviors.receive { context, event ->
        parent.tell(ReceiveEvent(event))
        Behaviors.same()
    }
}