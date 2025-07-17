package com.example.actorconcurrency.model

import org.apache.pekko.actor.typed.ActorRef

sealed class HelloCommand

data class Hello(
    val message: String,
    val replyTo: ActorRef<HelloCommand>? = null
) : HelloCommand()

data class HelloResponse(
    val message: String
) : HelloCommand()