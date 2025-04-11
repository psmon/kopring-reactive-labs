package org.example.kotlinbootreactivelabs.controller.auth

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.example.kotlinbootreactivelabs.error.LoginFailedException
import org.example.kotlinbootreactivelabs.service.AuthResponse
import org.example.kotlinbootreactivelabs.service.SimpleAuthService
import org.example.kotlinbootreactivelabs.service.TokenClaims
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth Controller")
class AuthController(private val authService: SimpleAuthService) {

    @PostMapping("/login")
    suspend fun login(
        @RequestParam id: String,
        @RequestParam password: String,
        @RequestParam identifier: String,
        @RequestParam nick: String,
        @RequestParam authType: String
    ): AuthResponse {
        return authService.authenticate(id, password, identifier, nick, authType)
            ?: throw LoginFailedException("Login failed")
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/validate-token")
    suspend fun validateToken(exchange: ServerWebExchange): TokenClaims {
        val authorizationHeader = exchange.request.headers.getFirst("Authorization")
        val token = authorizationHeader?.removePrefix("Bearer ")?.trim()
            ?: throw IllegalArgumentException("Missing or invalid Authorization header")
        return authService.getClaimsFromToken(token)
    }
}