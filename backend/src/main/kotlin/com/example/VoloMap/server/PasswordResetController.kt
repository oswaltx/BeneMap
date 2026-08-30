package com.example.VoloMap.server

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ForgotPasswordRequest(@field:Email @field:Size(max = 254) val email: String)
data class ResetPasswordRequest(val token: String, @field:Size(min = 8, max = 72) val newPassword: String)

@RestController
class PasswordResetController(
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val mailer: PasswordResetMailer,
    private val rateLimiter: ForgotPasswordRateLimiter,
    private val sessionRegistry: SessionRegistry,
) {
    @PostMapping("/auth/forgot-password")
    fun forgotPassword(@Valid @RequestBody req: ForgotPasswordRequest): ResponseEntity<*> {
        val email = req.email.trim().lowercase()
        val waitSeconds = rateLimiter.checkAndRecord(email)
        if (waitSeconds != null) {
            return ResponseEntity.status(429)
                .body(ErrorResponse("Bitte warte noch $waitSeconds Sekunden, bevor du es erneut versuchst."))
        }

        val user = userRepository.findByEmail(email)
        if (user != null) {
            passwordResetTokenRepository.deleteAll(passwordResetTokenRepository.findByUser(user))
            val token = PasswordResetToken(
                user = user,
                token = UUID.randomUUID().toString(),
                expiresAt = Instant.now().plus(Duration.ofMinutes(30)),
            )
            passwordResetTokenRepository.save(token)
            mailer.send(user.email, token.token)
        }
        return ResponseEntity.ok().build<Unit>()
    }

    @Transactional
    @PostMapping("/auth/reset-password")
    fun resetPassword(@Valid @RequestBody req: ResetPasswordRequest): ResponseEntity<*> {
        val resetToken = passwordResetTokenRepository.findByToken(req.token)
        if (resetToken == null || resetToken.expiresAt.isBefore(Instant.now())) {
            return ResponseEntity.status(400).body(ErrorResponse("Link ist ungültig oder abgelaufen."))
        }

        val user = resetToken.user
        user.passwordHash = passwordEncoder.encode(req.newPassword)!!
        userRepository.save(user)
        passwordResetTokenRepository.delete(resetToken)

        sessionRegistry.allPrincipals
            .filterIsInstance<UserDetails>()
            .filter { it.username == user.email }
            .forEach { principal ->
                sessionRegistry.getAllSessions(principal, false).forEach { it.expireNow() }
            }

        return ResponseEntity.noContent().build<Unit>()
    }
}
