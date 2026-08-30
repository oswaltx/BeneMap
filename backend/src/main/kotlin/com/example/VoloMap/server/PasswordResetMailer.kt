package com.example.VoloMap.server

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class PasswordResetMailer(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.from:no-reply@benemap.local}") private val fromAddress: String,
) {
    private val logger = LoggerFactory.getLogger(PasswordResetMailer::class.java)

    @Async
    fun send(email: String, token: String) {
        try {
            val message = SimpleMailMessage()
            message.setFrom(fromAddress)
            message.setTo(email)
            message.setSubject("Passwort zurücksetzen — Benemap")
            message.setText(
                "Hallo,\n\n" +
                    "du hast angefragt, dein Passwort für Benemap zurückzusetzen. " +
                    "Klicke auf den folgenden Link, um ein neues Passwort zu setzen " +
                    "(gültig für 30 Minuten):\n\n" +
                    "http://localhost:5173/reset-password?token=$token\n\n" +
                    "Falls du das nicht warst, kannst du diese E-Mail ignorieren."
            )
            mailSender.send(message)
        } catch (e: Exception) {
            logger.warn("Failed to send password reset email to $email", e)
        }
    }
}
