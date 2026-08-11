package com.example.VoloMap.server

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RegisterRequest(val email: String, val password: String, val name: String, val role: Role)
data class LoginRequest(val email: String, val password: String)
data class UserResponse(val email: String, val name: String, val role: Role)
data class ErrorResponse(val error: String)

@RestController
@RequestMapping("/auth")
class AuthController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val securityContextRepository: SecurityContextRepository
) {

    @PostMapping("/register")
    fun register(
        @RequestBody req: RegisterRequest,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<*> {
        if (userRepository.existsByEmail(req.email)) {
            return ResponseEntity.status(409).body(ErrorResponse("E-Mail bereits registriert."))
        }
        userRepository.save(
            User(
                email = req.email,
                passwordHash = passwordEncoder.encode(req.password)!!,
                name = req.name,
                role = req.role
            )
        )
        establishSession(req.email, req.password, request, response)
        val user = userRepository.findByEmail(req.email)!!
        return ResponseEntity.ok(UserResponse(user.email, user.name, user.role))
    }

    @PostMapping("/login")
    fun login(
        @RequestBody req: LoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<*> {
        try {
            establishSession(req.email, req.password, request, response)
        } catch (e: AuthenticationException) {
            return ResponseEntity.status(401).body(ErrorResponse("E-Mail oder Passwort falsch."))
        }
        val user = userRepository.findByEmail(req.email)!!
        return ResponseEntity.ok(UserResponse(user.email, user.name, user.role))
    }

    @PostMapping("/logout")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ): ResponseEntity<Void> {
        SecurityContextLogoutHandler().logout(request, response, authentication)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/me")
    fun me(authentication: Authentication): ResponseEntity<UserResponse> {
        val user = userRepository.findByEmail(authentication.name)!!
        return ResponseEntity.ok(UserResponse(user.email, user.name, user.role))
    }

    private fun establishSession(
        email: String,
        password: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val authRequest = UsernamePasswordAuthenticationToken(email, password)
        val authResult = authenticationManager.authenticate(authRequest)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authResult
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)
    }
}
