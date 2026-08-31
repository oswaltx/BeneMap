package com.example.VoloMap.server

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class EmailVerificationMailer(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.from:no-reply@benemap.local}") private val fromAddress: String,
    @Value("\${app.base-url:http://localhost:5173}") private val baseUrl: String,
) {
    private val logger = LoggerFactory.getLogger(EmailVerificationMailer::class.java)

    @Async
    fun send(email: String, token: String) {
        try {
            val message = SimpleMailMessage()
            message.setFrom(fromAddress)
            message.setTo(email)
            message.setSubject("E-Mail bestätigen — Benemap")
            message.setText(
                "Hallo,\n\n" +
                    "bitte bestätige deine E-Mail-Adresse für dein Benemap-Konto, indem du auf " +
                    "den folgenden Link klickst (gültig für 24 Stunden):\n\n" +
                    "$baseUrl/verify-email?token=$token\n\n" +
                    "Falls du dich nicht bei Benemap registriert hast, kannst du diese E-Mail ignorieren."
            )
            mailSender.send(message)
        } catch (e: Exception) {
            logger.warn("Failed to send verification email to $email", e)
        }
    }
}
