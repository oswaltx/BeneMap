package com.example.VoloMap.server

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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

    @Autowired
    lateinit var emailVerificationTokenRepository: EmailVerificationTokenRepository

    // This class never creates activities itself, but @SpringBootTest classes share one
    // in-memory database across the whole suite run — a preceding class that left
    // activities behind (referencing users via createdBy) would otherwise block
    // userRepository.deleteAll() below with a FK violation.
    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var activityRatingRepository: ActivityRatingRepository

    @Autowired
    lateinit var providerRatingRepository: ProviderRatingRepository

    @Autowired
    lateinit var activitySignupRepository: ActivitySignupRepository

    @MockitoBean
    lateinit var mailSender: JavaMailSender

    @BeforeEach
    fun cleanUp() {
        // The mailer builds a real MimeMessage via mailSender.createMimeMessage() — on a
        // mock that returns null unless stubbed, which would NPE before send() is reached.
        whenever(mailSender.createMimeMessage()).thenReturn(MimeMessage(Session.getInstance(java.util.Properties())))
        activitySignupRepository.deleteAll()
        activityRatingRepository.deleteAll()
        providerRatingRepository.deleteAll()
        activityRepository.deleteAll()
        passwordResetTokenRepository.deleteAll()
        emailVerificationTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        activitySignupRepository.deleteAll()
        activityRatingRepository.deleteAll()
        providerRatingRepository.deleteAll()
        activityRepository.deleteAll()
        passwordResetTokenRepository.deleteAll()
        emailVerificationTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun register(email: String): MockHttpSession {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"geheim123","name":"Test","role":"USER"}""")
        )
        val user = userRepository.findByEmail(email)!!
        user.emailVerified = true
        userRepository.save(user)
        val result = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"geheim123"}""")
        ).andReturn()
        return result.request.session as MockHttpSession
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
        // 2 invocations: one verification email from register(), one password-reset email here.
        verify(mailSender, timeout(2000).times(2)).send(any<MimeMessage>())
    }

    @Test
    fun `requesting a reset for a nonexistent email still returns 200 and sends nothing`() {
        mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"nosuchaccount1@example.com"}""")
        ).andExpect(status().isOk)

        assertTrue(passwordResetTokenRepository.findAll().isEmpty())
        verify(mailSender, never()).send(any<MimeMessage>())
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

    @Test
    fun `full flow - request reset, use token, login with new password, old password stops working`() {
        register("fullflow1@example.com")
        val user = userRepository.findByEmail("fullflow1@example.com")!!

        mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"fullflow1@example.com"}""")
        ).andExpect(status().isOk)

        val resetToken = passwordResetTokenRepository.findByUser(user)[0].token

        mockMvc.perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$resetToken","newPassword":"neuesPasswort456"}""")
        ).andExpect(status().isNoContent)

        assertNull(passwordResetTokenRepository.findByToken(resetToken))

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"fullflow1@example.com","password":"geheim123"}""")
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"fullflow1@example.com","password":"neuesPasswort456"}""")
        ).andExpect(status().isOk)
    }

    @Test
    fun `reset with an invalid token is rejected`() {
        mockMvc.perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"does-not-exist","newPassword":"neuesPasswort456"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error").value("Link ist ungültig oder abgelaufen."))
    }

    @Test
    fun `reset with an expired token is rejected`() {
        register("expiredtoken1@example.com")
        val user = userRepository.findByEmail("expiredtoken1@example.com")!!
        val expired = passwordResetTokenRepository.save(
            PasswordResetToken(user = user, token = "already-expired-token", expiresAt = Instant.now().minus(Duration.ofMinutes(1)))
        )

        mockMvc.perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${expired.token}","newPassword":"neuesPasswort456"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `resetting the password invalidates every other active session of the account`() {
        val firstSession = register("resetsessions1@example.com")
        val secondLoginResult = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"resetsessions1@example.com","password":"geheim123"}""")
        ).andReturn()
        val secondSession = secondLoginResult.request.session as MockHttpSession

        mockMvc.perform(get("/auth/me").session(firstSession)).andExpect(status().isOk)
        mockMvc.perform(get("/auth/me").session(secondSession)).andExpect(status().isOk)

        val user = userRepository.findByEmail("resetsessions1@example.com")!!
        val token = passwordResetTokenRepository.save(
            PasswordResetToken(user = user, token = "session-invalidation-token", expiresAt = Instant.now().plus(Duration.ofMinutes(30)))
        )

        mockMvc.perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${token.token}","newPassword":"neuesPasswort456"}""")
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/auth/me").session(firstSession)).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/auth/me").session(secondSession)).andExpect(status().isUnauthorized)
    }
}
