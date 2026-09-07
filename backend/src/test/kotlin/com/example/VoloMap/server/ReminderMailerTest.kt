package com.example.VoloMap.server

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.Properties

@SpringBootTest
class ReminderMailerTest {

    @Autowired
    lateinit var reminderMailer: ReminderMailer

    @MockitoBean
    lateinit var mailSender: JavaMailSender

    @Test
    fun `HTML-escapes activity name and address before embedding them in the email`() {
        whenever(mailSender.createMimeMessage()).thenReturn(MimeMessage(Session.getInstance(Properties())))

        reminderMailer.send(
            "victim@example.com",
            "<a href=\"https://evil.example\">Klick hier</a>",
            LocalDateTime.now().plusDays(1),
            "<script>alert(1)</script>",
        )

        val captor = ArgumentCaptor.forClass(MimeMessage::class.java)
        verify(mailSender, timeout(2000)).send(captor.capture())

        val output = ByteArrayOutputStream()
        captor.value.writeTo(output)
        val raw = output.toString("UTF-8")

        assertTrue(raw.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), "address text must be HTML-escaped in the mail")
        assertTrue(raw.contains("&lt;a href=&quot;https://evil.example&quot;&gt;"), "activity name must be HTML-escaped in the mail")
    }
}
