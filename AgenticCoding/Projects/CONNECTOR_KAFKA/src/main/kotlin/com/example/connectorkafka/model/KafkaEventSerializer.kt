package com.example.connectorkafka.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer

class KafkaEventSerializer : Serializer<KafkaEvent> {
    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
    }

    override fun serialize(topic: String?, data: KafkaEvent?): ByteArray? {
        return data?.let {
            objectMapper.writeValueAsBytes(it)
        }
    }

    override fun close() {}
}

class KafkaEventDeserializer : Deserializer<KafkaEvent> {
    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
    }

    override fun deserialize(topic: String?, data: ByteArray?): KafkaEvent? {
        return data?.let {
            objectMapper.readValue<KafkaEvent>(it)
        }
    }

    override fun close() {}
}