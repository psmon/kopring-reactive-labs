package org.example.kotlinbootreactivelabs.actor.state.model

/** 상태 정의 */
enum class HappyStashState {
    HAPPY, ANGRY
}

data class HelloStashState (
    var happyState: HappyStashState,
    var helloCount: Int,
    var helloTotalCount: Int
)