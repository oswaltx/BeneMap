package com.example.VoloMap.server

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RegisterRequest(
    @field:Email val email: String,
    @field:Size(min = 8, max = 72) val password: String,
    @field:NotBlank val name: String,
    val role: Role
)
data class LoginRequest(val email: String, val password: String)
data class UserResponse(
    val id: Long,
    val email: String,
    val name: String,
    val role: Role,
    val photoUrl: String? = null,
    val websiteUrl: String? = null,
)
data class UpdateProfileRequest(val photoUrl: String? = null, val websiteUrl: String? = null)
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
        @Valid @RequestBody req: RegisterRequest,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<*> {
        val email = req.email.trim().lowercase()
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(409).body(ErrorResponse("E-Mail bereits registriert."))
        }
        userRepository.save(
            User(
                email = email,
                passwordHash = passwordEncoder.encode(req.password)!!,
                name = req.name,
                role = req.role
            )
        )
        establishSession(email, req.password, request, response)
        val user = userRepository.findByEmail(email)!!
        return ResponseEntity.ok(UserResponse(user.id, user.email, user.name, user.role, user.photoUrl, user.websiteUrl))
    }

    @PostMapping("/login")
    fun login(
        @RequestBody req: LoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<*> {
        val email = req.email.trim().lowercase()
        try {
            establishSession(email, req.password, request, response)
        } catch (e: AuthenticationException) {
            return ResponseEntity.status(401).body(ErrorResponse("E-Mail oder Passwort falsch."))
        }
        val user = userRepository.findByEmail(email)!!
        return ResponseEntity.ok(UserResponse(user.id, user.email, user.name, user.role, user.photoUrl, user.websiteUrl))
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
        return ResponseEntity.ok(UserResponse(user.id, user.email, user.name, user.role, user.photoUrl, user.websiteUrl))
    }

    @PutMapping("/me")
    fun updateProfile(
        @RequestBody req: UpdateProfileRequest,
        authentication: Authentication
    ): ResponseEntity<UserResponse> {
        val user = userRepository.findByEmail(authentication.name)!!
        user.photoUrl = req.photoUrl?.trim()?.ifBlank { null }
        user.websiteUrl = req.websiteUrl?.trim()?.ifBlank { null }
        userRepository.save(user)
        return ResponseEntity.ok(UserResponse(user.id, user.email, user.name, user.role, user.photoUrl, user.websiteUrl))
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
        if (request.getSession(false) != null) {
            request.changeSessionId()
        }
        securityContextRepository.saveContext(context, request, response)
    }
}
