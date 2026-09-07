package com.example.VoloMap.server

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class ReminderMailer(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.from:no-reply@benemap.local}") private val fromAddress: String,
) {
    private val logger = LoggerFactory.getLogger(ReminderMailer::class.java)
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'")

    @Async
    fun send(email: String, activityName: String, dateTime: LocalDateTime, addressText: String?) {
        try {
            val whenText = dateTime.format(formatter)
            val whereText = addressText?.let { " in $it" } ?: ""
            val mimeMessage = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(mimeMessage, true, "UTF-8")
            helper.setFrom(fromAddress)
            helper.setTo(email)
            helper.setSubject("Erinnerung: $activityName morgen")
            helper.setText(
                "Hallo,\n\n" +
                    "kurze Erinnerung: \"$activityName\" findet morgen am $whenText$whereText statt.\n\n" +
                    "Bis dahin!",
                "<p>Hallo,</p>" +
                    "<p>kurze Erinnerung: <strong>$activityName</strong> findet morgen am $whenText$whereText statt.</p>" +
                    "<p>Bis dahin!</p>"
            )
            mailSender.send(mimeMessage)
        } catch (e: Exception) {
            logger.warn("Failed to send reminder email to $email", e)
        }
    }
}
