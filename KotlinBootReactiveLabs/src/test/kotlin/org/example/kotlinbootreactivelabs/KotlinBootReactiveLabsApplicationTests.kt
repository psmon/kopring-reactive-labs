package org.example.kotlinbootreactivelabs


import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.example.kotlinbootreactivelabs.actor.state.Hello
import org.example.kotlinbootreactivelabs.config.AkkaConfiguration
import org.example.labs.common.TestModel

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import kotlin.Any


@SpringBootTest
@ActiveProfiles("test")
class KotlinBootReactiveLabsApplicationTests {

    @Autowired
    lateinit
    var akka:AkkaConfiguration

    @Test
    fun contextLoads() {
        // 공용 LIB 모듈 생성테스트
        val testModel = TestModel("test", 1L)
        println("testModelId: $testModel.id")

        // Actor 작동 Test
        val response = AskPattern.ask(
            akka.getHelloState(),
            { replyTo: ActorRef<Any> -> Hello("Hello", replyTo) },
            Duration.ofSeconds(3),
            akka.getScheduler()
        ).toCompletableFuture();

        println("ActorResponse -  ${response.get()}")
    }
}

