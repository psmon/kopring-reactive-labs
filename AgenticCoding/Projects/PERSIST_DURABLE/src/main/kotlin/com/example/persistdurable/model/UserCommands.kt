package com.example.persistdurable.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.pekko.actor.typed.ActorRef

sealed class UserCommand : PersistenceSerializable

data class UserLogin @JsonCreator constructor(
    @JsonProperty("replyTo") val replyTo: ActorRef<UserResponse>? = null
) : UserCommand()

data class UseCart @JsonCreator constructor(
    @JsonProperty("replyTo") val replyTo: ActorRef<UserResponse>? = null
) : UserCommand()

data class ViewProduct @JsonCreator constructor(
    @JsonProperty("productId") val productId: String,
    @JsonProperty("replyTo") val replyTo: ActorRef<UserResponse>? = null
) : UserCommand()

data class SetMarketingOptIn @JsonCreator constructor(
    @JsonProperty("optIn") val optIn: Boolean,
    @JsonProperty("replyTo") val replyTo: ActorRef<UserResponse>? = null
) : UserCommand()

data class GetUserState @JsonCreator constructor(
    @JsonProperty("replyTo") val replyTo: ActorRef<UserStateResponse>
) : UserCommand()

object CheckInactivity : UserCommand()

sealed class UserResponse : PersistenceSerializable

data class ActionCompleted @JsonCreator constructor(
    @JsonProperty("action") val action: String
) : UserResponse()

data class UserStateResponse @JsonCreator constructor(
    @JsonProperty("state") val state: UserState
) : UserResponse()