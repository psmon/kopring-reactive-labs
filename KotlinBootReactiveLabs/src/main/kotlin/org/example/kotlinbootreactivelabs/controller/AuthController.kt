package org.example.kotlinbootreactivelabs.controller

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.example.kotlinbootreactivelabs.error.LoginFailedException
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import org.example.kotlinbootreactivelabs.service.SimpleAuthServiceAuthService
import org.example.kotlinbootreactivelabs.service.AuthResponse
import org.example.kotlinbootreactivelabs.service.TokenClaims
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth Controller")
class AuthController(private val authService: SimpleAuthServiceAuthService) {

    @PostMapping("/login")
    fun login(@RequestParam id: String, @RequestParam password: String, @RequestParam identifier: String, @RequestParam nick: String, @RequestParam authType: String): Mono<AuthResponse> {
        return Mono.fromCallable {
            authService.authenticate(id, password, identifier, nick, authType)
                ?: throw LoginFailedException("Login failed")
        }
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/validate-token")
    fun validateToken(exchange: ServerWebExchange): Mono<TokenClaims> {
        val authorizationHeader = exchange.request.headers.getFirst("Authorization")
        val token = authorizationHeader?.removePrefix("Bearer ")?.trim()
            ?: throw IllegalArgumentException("Missing or invalid Authorization header")
        return Mono.fromCallable {
            authService.getClaimsFromToken(token)
        }
    }
}