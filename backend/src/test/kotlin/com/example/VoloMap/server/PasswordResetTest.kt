package com.example.VoloMap.server

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var passwordResetTokenRepository: PasswordResetTokenRepository

    @MockitoBean
    lateinit var mailSender: JavaMailSender

    @BeforeEach
    fun cleanUp() {
        passwordResetTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        passwordResetTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun register(email: String) {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"geheim123","name":"Test","role":"USER"}""")
        )
    }

    @Test
    fun `requesting a reset for an existing user creates a token and sends an email`() {
        register("resetreq1@example.com")
        val user = userRepository.findByEmail("resetreq1@example.com")!!

        mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"resetreq1@example.com"}""")
        ).andExpect(status().isOk)

        val tokens = passwordResetTokenRepository.findByUser(user)
        assertEquals(1, tokens.size)
        verify(mailSender).send(any<SimpleMailMessage>())
    }

    @Test
    fun `requesting a reset for a nonexistent email still returns 200 and sends nothing`() {
        mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"nosuchaccount1@example.com"}""")
        ).andExpect(status().isOk)

        assertTrue(passwordResetTokenRepository.findAll().isEmpty())
        verify(mailSender, never()).send(any<SimpleMailMessage>())
    }

    @Test
    fun `a second request within 60 seconds is rate-limited`() {
        register("ratelimit1@example.com")

        mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ratelimit1@example.com"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ratelimit1@example.com"}""")
        ).andExpect(status().`is`(429))
    }

    @Test
    fun `requesting again replaces a previously issued token`() {
        register("replacetoken1@example.com")
        val user = userRepository.findByEmail("replacetoken1@example.com")!!
        val staleToken = passwordResetTokenRepository.save(
            PasswordResetToken(user = user, token = "stale-token-value", expiresAt = Instant.now().plus(Duration.ofMinutes(30)))
        )

        mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"replacetoken1@example.com"}""")
        ).andExpect(status().isOk)

        val tokens = passwordResetTokenRepository.findByUser(user)
        assertEquals(1, tokens.size)
        assertTrue(tokens[0].id != staleToken.id)
    }
}
