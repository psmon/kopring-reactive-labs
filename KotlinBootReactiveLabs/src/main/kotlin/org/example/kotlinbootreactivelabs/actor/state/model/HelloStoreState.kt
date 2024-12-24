package org.example.kotlinbootreactivelabs.actor.state.model

/** 상태 정의 */
enum class HappyState {
    HAPPY, ANGRY
}

data class HelloStoreState (
    var happyState: HappyState,
    var helloCount: Int,
    var helloTotalCount: Int
)

