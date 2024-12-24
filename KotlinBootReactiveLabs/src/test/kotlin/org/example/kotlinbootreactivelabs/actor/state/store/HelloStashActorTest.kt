package org.example.kotlinbootreactivelabs.actor.state.store

import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.example.kotlinbootreactivelabs.actor.state.model.HappyStashState
import org.example.kotlinbootreactivelabs.actor.state.model.HelloStashState
import org.example.kotlinbootreactivelabs.repositories.durable.DurableRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import reactor.core.publisher.Mono


class HelloStashActorTest {

    companion object {
        private lateinit var testKit: ActorTestKit

        @BeforeAll
        @JvmStatic
        fun setup() {
            testKit = ActorTestKit.create()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun testInitialState() {
        val durableRepository = Mockito.mock(DurableRepository::class.java)
        Mockito.`when`(durableRepository.findByIdEx<HelloStashState>("testId", 1L))
            .thenReturn(Mono.just(HelloStashState(HappyStashState.HAPPY, 0, 0)))


        val probe = testKit.createTestProbe<HelloStashState>()
        val actor = testKit.spawn(Behaviors.withStash(100) { stashBuffer ->
            HelloStashActor.create("testId", durableRepository, stashBuffer)
        })

        actor.tell(GetState(probe.ref))

        probe.expectMessageClass(HelloStashState::class.java)
    }

    @Test
    fun testGetState() {
        val durableRepository = Mockito.mock(DurableRepository::class.java)
        Mockito.`when`(durableRepository.findByIdEx<HelloStashState>("testId", 1L))
            .thenReturn(Mono.just(HelloStashState(HappyStashState.HAPPY, 0, 0)))

        val probe = testKit.createTestProbe<HelloStashState>()
        val actor = testKit.spawn(Behaviors.withStash(100) { stashBuffer ->
            HelloStashActor.create("testId", durableRepository, stashBuffer)
        })

        actor.tell(GetState(probe.ref))
        val state = probe.receiveMessage()
        assert(state.happyState == HappyStashState.HAPPY)
        assert(state.helloCount == 0)
        assert(state.helloTotalCount == 0)
    }

    @Test
    fun testSaveState() {
        val durableRepository = Mockito.mock(DurableRepository::class.java)

        Mockito.`when`(durableRepository.createOrUpdateDurableStateEx<HelloStashState>("testId", 1L, HelloStashState(HappyStashState.HAPPY, 1, 1)))
            .thenReturn(Mono.just(HelloStashState(HappyStashState.HAPPY, 1, 1)))

        Mockito.`when`(durableRepository.findByIdEx<HelloStashState>("testId", 1L))
            .thenReturn(Mono.just(HelloStashState(HappyStashState.HAPPY, 0, 0)))

        val probe = testKit.createTestProbe<Done>()
        val actor = testKit.spawn(Behaviors.withStash(100) { stashBuffer ->
            HelloStashActor.create("testId", durableRepository, stashBuffer)
        })

        actor.tell(SavaState(HelloStashState(HappyStashState.HAPPY, 1, 1), probe.ref))
        probe.expectMessage(Done.getInstance())
    }
}