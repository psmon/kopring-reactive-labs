package org.example.kotlinbootreactivelabs.error


import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class LoginFailedException(reason: String) : ResponseStatusException(HttpStatus.UNAUTHORIZED ,reason)

