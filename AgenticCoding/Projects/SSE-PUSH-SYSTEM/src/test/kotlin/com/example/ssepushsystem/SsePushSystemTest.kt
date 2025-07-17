package com.example.ssepushsystem

import com.example.ssepushsystem.actor.TopicManagerActor
import com.example.ssepushsystem.actor.UserEventActor
import com.example.ssepushsystem.model.TopicEvent
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.collect
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.javadsl.AskPattern
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class SsePushSystemTest : FunSpec() {
    
    private lateinit var testKit: ActorTestKit
    private lateinit var topicManagerActor: ActorRef<TopicManagerActor.Command>
    private lateinit var userEventActor: ActorRef<UserEventActor.Command>
    
    override suspend fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        testKit = ActorTestKit.create()
        topicManagerActor = testKit.spawn(TopicManagerActor.create())
        userEventActor = testKit.spawn(UserEventActor.create(topicManagerActor))
    }
    
    override suspend fun afterSpec(spec: Spec) {
        testKit.shutdownTestKit()
        super.afterSpec(spec)
    }
    
    init {
        test("User1 subscribes to topic A, User2 subscribes to topic B - only User1 receives topic A messages") {
            val user1Events = mutableListOf<TopicEvent>()
            val user2Events = mutableListOf<TopicEvent>()
            
            // User 1 subscribes to topic A
            val user1Sink = Sinks.many().multicast().onBackpressureBuffer<TopicEvent>()
            val user1Connected = AskPattern.ask(
                userEventActor,
                { replyTo: ActorRef<UserEventActor.Connected> ->
                    UserEventActor.Connect("user1", user1Sink, replyTo)
                },
                Duration.ofSeconds(5),
                testKit.system().scheduler()
            ).await()
            
            userEventActor.tell(UserEventActor.SubscribeToTopic("user1", "topicA"))
            
            // User 2 subscribes to topic B
            val user2Sink = Sinks.many().multicast().onBackpressureBuffer<TopicEvent>()
            val user2Connected = AskPattern.ask(
                userEventActor,
                { replyTo: ActorRef<UserEventActor.Connected> ->
                    UserEventActor.Connect("user2", user2Sink, replyTo)
                },
                Duration.ofSeconds(5),
                testKit.system().scheduler()
            ).await()
            
            userEventActor.tell(UserEventActor.SubscribeToTopic("user2", "topicB"))
            
            // Collect events
            user1Sink.asFlux().subscribe { event ->
                user1Events.add(event)
            }
            
            user2Sink.asFlux().subscribe { event ->
                user2Events.add(event)
            }
            
            // Give subscriptions time to establish
            Thread.sleep(500)
            
            // Publish event to topic A
            val eventA = TopicEvent("event1", "topicA", "Message for topic A")
            val publishResult = AskPattern.ask(
                topicManagerActor,
                { replyTo: ActorRef<TopicManagerActor.EventPublished> ->
                    TopicManagerActor.PublishEvent(eventA, replyTo)
                },
                Duration.ofSeconds(5),
                testKit.system().scheduler()
            ).await()
            
            publishResult.success shouldBe true
            
            // Wait for event propagation
            eventually(2.seconds) {
                user1Events shouldHaveSize 1
                user1Events[0].data shouldBe "Message for topic A"
                user2Events shouldHaveSize 0
            }
            
            // Cleanup
            userEventActor.tell(UserEventActor.Disconnect("user1"))
            userEventActor.tell(UserEventActor.Disconnect("user2"))
        }
        
        test("User3 connects late and receives historical events") {
            // First, publish some events
            val historicalEvents = listOf(
                TopicEvent("hist1", "topicC", "Historical event 1"),
                TopicEvent("hist2", "topicC", "Historical event 2"),
                TopicEvent("hist3", "topicC", "Historical event 3")
            )
            
            historicalEvents.forEach { event ->
                val result = AskPattern.ask(
                    topicManagerActor,
                    { replyTo: ActorRef<TopicManagerActor.EventPublished> ->
                        TopicManagerActor.PublishEvent(event, replyTo)
                    },
                    Duration.ofSeconds(5),
                    testKit.system().scheduler()
                ).await()
                
                result.success shouldBe true
            }
            
            // Give events time to be stored
            Thread.sleep(500)
            
            // Now User3 connects
            val user3Events = mutableListOf<TopicEvent>()
            val user3Sink = Sinks.many().multicast().onBackpressureBuffer<TopicEvent>()
            
            // Subscribe to collect events
            user3Sink.asFlux().subscribe { event ->
                user3Events.add(event)
            }
            
            // Connect and get recent events
            val connected = AskPattern.ask(
                userEventActor,
                { replyTo: ActorRef<UserEventActor.Connected> ->
                    UserEventActor.Connect("user3", user3Sink, replyTo)
                },
                Duration.ofSeconds(5),
                testKit.system().scheduler()
            ).await()
            
            // Get recent events for topic C
            val recentEvents = AskPattern.ask(
                topicManagerActor,
                { replyTo: ActorRef<List<TopicEvent>> ->
                    TopicManagerActor.GetRecentEvents("user3", setOf("topicC"), replyTo)
                },
                Duration.ofSeconds(5),
                testKit.system().scheduler()
            ).await()
            
            // Emit historical events to the user
            recentEvents.forEach { event ->
                user3Sink.tryEmitNext(event)
            }
            
            // Verify
            recentEvents shouldHaveSize 3
            recentEvents.map { it.data } shouldBe listOf(
                "Historical event 1",
                "Historical event 2",
                "Historical event 3"
            )
            
            eventually(2.seconds) {
                user3Events shouldHaveSize 3
                user3Events.map { it.data }.sorted() shouldBe listOf(
                    "Historical event 1",
                    "Historical event 2",
                    "Historical event 3"
                )
            }
            
            // Cleanup
            userEventActor.tell(UserEventActor.Disconnect("user3"))
        }
        
        test("Topic history retrieval") {
            // Publish multiple events to a topic
            val topic = "history-test-topic"
            repeat(5) { i ->
                val event = TopicEvent("event-$i", topic, "Event data $i")
                val result = AskPattern.ask(
                    topicManagerActor,
                    { replyTo: ActorRef<TopicManagerActor.EventPublished> ->
                        TopicManagerActor.PublishEvent(event, replyTo)
                    },
                    Duration.ofSeconds(5),
                    testKit.system().scheduler()
                ).await()
                
                result.success shouldBe true
            }
            
            // Get topic history
            val history = AskPattern.ask(
                topicManagerActor,
                { replyTo: ActorRef<com.example.ssepushsystem.model.EventHistory> ->
                    TopicManagerActor.GetTopicHistory(topic, replyTo)
                },
                Duration.ofSeconds(5),
                testKit.system().scheduler()
            ).await()
            
            history.topic shouldBe topic
            history.events shouldHaveSize 5
            history.events.map { it.data } shouldBe (0..4).map { "Event data $it" }
        }
    }
}