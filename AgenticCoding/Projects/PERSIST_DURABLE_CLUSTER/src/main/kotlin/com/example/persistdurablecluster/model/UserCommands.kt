package com.example.persistdurablecluster.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
sealed class UserCommand : PersistenceSerializable {
    companion object {
        val TYPE_KEY: EntityTypeKey<UserCommand> = EntityTypeKey.create(UserCommand::class.java, "UserEntity")
    }
}

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

data class CheckInactivity @JsonCreator constructor(
    @JsonProperty("dummy") val dummy: String = ""
) : UserCommand()

sealed class UserResponse : PersistenceSerializable

data class ActionCompleted @JsonCreator constructor(
    @JsonProperty("action") val action: String
) : UserResponse()

data class UserStateResponse @JsonCreator constructor(
    @JsonProperty("state") val state: UserState
) : UserResponse()