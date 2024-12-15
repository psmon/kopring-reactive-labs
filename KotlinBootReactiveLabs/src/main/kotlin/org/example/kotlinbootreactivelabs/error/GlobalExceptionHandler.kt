package org.example.kotlinbootreactivelabs.error

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono


//@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(exchange: ServerWebExchange, ex: ResponseStatusException): Mono<Void> {
        logger.error("Error occurred: ${ex.message}", ex)
        exchange.response.statusCode = ex.statusCode
        return exchange.response.setComplete()
    }

    @ExceptionHandler(Exception::class)
    fun handleException(exchange: ServerWebExchange, ex: Exception): Mono<Void> {
        logger.error("Unexpected error occurred: ${ex.message}", ex)
        exchange.response.statusCode = HttpStatus.INTERNAL_SERVER_ERROR
        return exchange.response.setComplete()
    }
}