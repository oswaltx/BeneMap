package com.example.VoloMap.server

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class PasswordResetMailer(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.from:no-reply@benemap.local}") private val fromAddress: String,
    @Value("\${app.base-url:http://localhost:5173}") private val baseUrl: String,
) {
    private val logger = LoggerFactory.getLogger(PasswordResetMailer::class.java)

    @Async
    fun send(email: String, token: String) {
        try {
            val link = "$baseUrl/reset-password?token=$token"
            val mimeMessage = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(mimeMessage, true, "UTF-8")
            helper.setFrom(fromAddress)
            helper.setTo(email)
            helper.setSubject("Passwort zurücksetzen — Benemap")
            helper.setText(
                "Hallo,\n\n" +
                    "du hast angefragt, dein Passwort für Benemap zurückzusetzen. " +
                    "Klicke auf den folgenden Link, um ein neues Passwort zu setzen " +
                    "(gültig für 30 Minuten):\n\n" +
                    "$link\n\n" +
                    "Falls du das nicht warst, kannst du diese E-Mail ignorieren.",
                "<p>Hallo,</p>" +
                    "<p>du hast angefragt, dein Passwort für Benemap zurückzusetzen. Klicke auf den " +
                    "folgenden Link, um ein neues Passwort zu setzen (gültig für 30 Minuten):</p>" +
                    "<p><a href=\"$link\">$link</a></p>" +
                    "<p>Falls du das nicht warst, kannst du diese E-Mail ignorieren.</p>"
            )
            mailSender.send(mimeMessage)
        } catch (e: Exception) {
            logger.warn("Failed to send password reset email to $email", e)
        }
    }
}
