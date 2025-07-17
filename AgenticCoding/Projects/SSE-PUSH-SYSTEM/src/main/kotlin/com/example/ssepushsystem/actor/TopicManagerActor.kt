package com.example.ssepushsystem.actor

import com.example.ssepushsystem.model.CborSerializable
import com.example.ssepushsystem.model.TopicEvent
import com.example.ssepushsystem.model.EventHistory
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.pubsub.Topic
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object TopicManagerActor {
    
    sealed class Command : CborSerializable
    data class PublishEvent(val event: TopicEvent, val replyTo: ActorRef<EventPublished>) : Command()
    data class Subscribe(val userId: String, val topic: String, val subscriber: ActorRef<TopicEvent>) : Command()
    data class Unsubscribe(val userId: String, val topic: String) : Command()
    data class GetRecentEvents(val userId: String, val topics: Set<String>, val replyTo: ActorRef<List<TopicEvent>>) : Command()
    data class GetTopicHistory(val topic: String, val replyTo: ActorRef<EventHistory>) : Command()
    private data class CleanupOldEvents(val now: Instant) : Command()
    
    data class EventPublished(val success: Boolean, val event: TopicEvent) : CborSerializable
    
    private const val MAX_EVENTS_PER_TOPIC = 100
    private val EVENT_RETENTION_DURATION = Duration.ofHours(24)
    
    fun create(): Behavior<Command> = Behaviors.setup { context ->
        val topicActors = ConcurrentHashMap<String, ActorRef<Topic.Command<TopicEvent>>>()
        val eventHistory = ConcurrentHashMap<String, MutableList<TopicEvent>>()
        val userSubscriptions = ConcurrentHashMap<String, MutableSet<String>>()
        
        // Schedule periodic cleanup
        context.system.scheduler().scheduleAtFixedRate(
            Duration.ofMinutes(5),
            Duration.ofMinutes(5),
            { context.self.tell(CleanupOldEvents(Instant.now())) },
            context.executionContext
        )
        
        Behaviors.receiveMessage { message ->
            when (message) {
                is PublishEvent -> {
                    val topicName = message.event.topic
                    
                    // Get or create topic actor
                    val topicActor = topicActors.computeIfAbsent(topicName) {
                        context.spawn(Topic.create(TopicEvent::class.java, topicName), "topic-$topicName")
                    }
                    
                    // Store in history
                    val history = eventHistory.computeIfAbsent(topicName) { mutableListOf() }
                    history.add(message.event)
                    
                    // Keep only last MAX_EVENTS_PER_TOPIC events
                    if (history.size > MAX_EVENTS_PER_TOPIC) {
                        history.removeAt(0)
                    }
                    
                    // Publish to topic
                    topicActor.tell(Topic.publish(message.event))
                    
                    message.replyTo.tell(EventPublished(true, message.event))
                    Behaviors.same()
                }
                
                is Subscribe -> {
                    val topicName = message.topic
                    
                    // Get or create topic actor
                    val topicActor = topicActors.computeIfAbsent(topicName) {
                        context.spawn(Topic.create(TopicEvent::class.java, topicName), "topic-$topicName")
                    }
                    
                    // Track user subscription
                    userSubscriptions.computeIfAbsent(message.userId) { mutableSetOf() }.add(topicName)
                    
                    // Subscribe to topic
                    topicActor.tell(Topic.subscribe(message.subscriber))
                    
                    context.log.info("User {} subscribed to topic {}", message.userId, topicName)
                    Behaviors.same()
                }
                
                is Unsubscribe -> {
                    val topicActor = topicActors[message.topic]
                    if (topicActor != null) {
                        // Note: Pekko Topic doesn't support direct unsubscribe, 
                        // the subscription will be cleaned up when the subscriber actor stops
                        userSubscriptions[message.userId]?.remove(message.topic)
                        context.log.info("User {} unsubscribed from topic {}", message.userId, message.topic)
                    }
                    Behaviors.same()
                }
                
                is GetRecentEvents -> {
                    val recentEvents = mutableListOf<TopicEvent>()
                    
                    message.topics.forEach { topic ->
                        eventHistory[topic]?.let { events ->
                            recentEvents.addAll(events)
                        }
                    }
                    
                    // Sort by timestamp and return
                    val sortedEvents = recentEvents.sortedByDescending { it.timestamp }
                    message.replyTo.tell(sortedEvents.take(MAX_EVENTS_PER_TOPIC))
                    Behaviors.same()
                }
                
                is GetTopicHistory -> {
                    val events = eventHistory[message.topic] ?: emptyList()
                    message.replyTo.tell(EventHistory(message.topic, events.toList()))
                    Behaviors.same()
                }
                
                is CleanupOldEvents -> {
                    val cutoffTime = message.now.minus(EVENT_RETENTION_DURATION)
                    
                    eventHistory.forEach { (topic, events) ->
                        events.removeIf { event ->
                            event.timestamp.isBefore(cutoffTime)
                        }
                        
                        if (events.isEmpty()) {
                            eventHistory.remove(topic)
                        }
                    }
                    
                    context.log.debug("Cleaned up old events, cutoff time: {}", cutoffTime)
                    Behaviors.same()
                }
            }
        }
    }
}