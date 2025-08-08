package com.example.pekkohttp.marshalling

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.pekko.http.javadsl.marshalling.Marshaller
import org.apache.pekko.http.javadsl.model.ContentTypes
import org.apache.pekko.http.javadsl.model.HttpEntities
import org.apache.pekko.http.javadsl.model.RequestEntity
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller
import org.apache.pekko.http.javadsl.model.HttpEntity

object JsonSupport {
    
    val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }
    
    inline fun <reified T> marshaller(): Marshaller<T, RequestEntity> {
        return Marshaller.withFixedContentType(ContentTypes.APPLICATION_JSON) { value ->
            val json = objectMapper.writeValueAsString(value)
            HttpEntities.create(ContentTypes.APPLICATION_JSON, json)
        }
    }
    
    inline fun <reified T> unmarshaller(): Unmarshaller<HttpEntity, T> {
        return Unmarshaller.entityToString().thenApply { json ->
            objectMapper.readValue(json, T::class.java)
        }
    }
    
    fun <T> unmarshallerFor(clazz: Class<T>): Unmarshaller<HttpEntity, T> {
        return Unmarshaller.entityToString().thenApply { json ->
            objectMapper.readValue(json, clazz)
        }
    }
    
    fun <T> marshallerFor(clazz: Class<T>): Marshaller<T, RequestEntity> {
        return Marshaller.withFixedContentType(ContentTypes.APPLICATION_JSON) { value ->
            val json = objectMapper.writeValueAsString(value)
            HttpEntities.create(ContentTypes.APPLICATION_JSON, json)
        }
    }
}