package com.example.persistdurablecluster.serialization

import com.fasterxml.jackson.annotation.JsonIgnore
import org.apache.pekko.actor.typed.ActorRef

abstract class ActorRefMixin {
    @JsonIgnore
    abstract fun getReplyTo(): ActorRef<*>?
}