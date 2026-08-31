package com.example.VoloMap.server

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class VerifyEmailRequest(val token: String)
data class ResendVerificationRequest(@field:Email @field:Size(max = 254) val email: String)

@RestController
class EmailVerificationController(
    private val userRepository: UserRepository,
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val mailer: EmailVerificationMailer,
    private val rateLimiter: RateLimiter,
) {
    @PostMapping("/auth/verify-email")
    fun verifyEmail(@RequestBody req: VerifyEmailRequest): ResponseEntity<*> {
        val verificationToken = emailVerificationTokenRepository.findByToken(req.token)
        if (verificationToken == null || verificationToken.expiresAt.isBefore(Instant.now())) {
            return ResponseEntity.status(400).body(ErrorResponse("Link ist ungültig oder abgelaufen."))
        }

        val user = verificationToken.user
        user.emailVerified = true
        userRepository.save(user)
        emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByUser(user))

        return ResponseEntity.noContent().build<Unit>()
    }

    @PostMapping("/auth/resend-verification")
    fun resendVerification(@Valid @RequestBody req: ResendVerificationRequest): ResponseEntity<*> {
        val email = req.email.trim().lowercase()
        if (!rateLimiter.isAllowed("resend-verification:$email", 3, Duration.ofMinutes(15))) {
            return ResponseEntity.status(429)
                .body(ErrorResponse("Zu viele Anfragen. Bitte versuche es später erneut."))
        }

        val user = userRepository.findByEmail(email)
        if (user != null && !user.emailVerified) {
            emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByUser(user))
            val token = EmailVerificationToken(
                user = user,
                token = UUID.randomUUID().toString(),
                expiresAt = Instant.now().plus(Duration.ofHours(24)),
            )
            emailVerificationTokenRepository.save(token)
            mailer.send(user.email, token.token)
        }
        // Immer 200, unabhängig davon ob die E-Mail existiert oder schon verifiziert ist —
        // sonst ließe sich darüber erraten, welche Adressen registriert sind (wie bei
        // /auth/forgot-password).
        return ResponseEntity.ok().build<Unit>()
    }
}
