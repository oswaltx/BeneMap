package com.example.VoloMap.server

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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

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
        activitySignupRepository.deleteAll()
        activityRatingRepository.deleteAll()
        providerRatingRepository.deleteAll()
        activityRepository.deleteAll()
        emailVerificationTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        activitySignupRepository.deleteAll()
        activityRatingRepository.deleteAll()
        providerRatingRepository.deleteAll()
        activityRepository.deleteAll()
        emailVerificationTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun register(email: String) {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"geheim123","name":"Test","role":"USER"}""")
        ).andExpect(status().isOk)
    }

    @Test
    fun `verifying with a valid token marks the account verified and allows login`() {
        register("verify1@example.com")
        val user = userRepository.findByEmail("verify1@example.com")!!
        val token = emailVerificationTokenRepository.findByUser(user)[0].token

        mockMvc.perform(
            post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token"}""")
        ).andExpect(status().isNoContent)

        assertTrue(userRepository.findByEmail("verify1@example.com")!!.emailVerified)

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"verify1@example.com","password":"geheim123"}""")
        ).andExpect(status().isOk)
    }

    @Test
    fun `verifying deletes the token so it cannot be reused`() {
        register("verify2@example.com")
        val user = userRepository.findByEmail("verify2@example.com")!!
        val token = emailVerificationTokenRepository.findByUser(user)[0].token

        mockMvc.perform(
            post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token"}""")
        ).andExpect(status().isNoContent)

        assertNull(emailVerificationTokenRepository.findByToken(token))

        mockMvc.perform(
            post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `verifying with an invalid token is rejected`() {
        mockMvc.perform(
            post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"does-not-exist"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Link ist ungültig oder abgelaufen."))
    }

    @Test
    fun `verifying with an expired token is rejected`() {
        register("verify3@example.com")
        val user = userRepository.findByEmail("verify3@example.com")!!
        emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByUser(user))
        val expired = emailVerificationTokenRepository.save(
            EmailVerificationToken(user = user, token = "already-expired-token", expiresAt = Instant.now().minus(Duration.ofMinutes(1)))
        )

        mockMvc.perform(
            post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${expired.token}"}""")
        ).andExpect(status().isBadRequest)

        assertTrue(!userRepository.findByEmail("verify3@example.com")!!.emailVerified)
    }

    @Test
    fun `resend issues a new token and sends an email for an unverified account`() {
        register("resend1@example.com")
        val user = userRepository.findByEmail("resend1@example.com")!!
        val originalToken = emailVerificationTokenRepository.findByUser(user)[0].token

        mockMvc.perform(
            post("/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"resend1@example.com"}""")
        ).andExpect(status().isOk)

        val tokens = emailVerificationTokenRepository.findByUser(user)
        assertEquals(1, tokens.size)
        assertTrue(tokens[0].token != originalToken)
        verify(mailSender, timeout(2000).times(2)).send(any<SimpleMailMessage>())
    }

    @Test
    fun `resend for a nonexistent email still returns 200 and sends nothing extra`() {
        mockMvc.perform(
            post("/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"nosuchaccount2@example.com"}""")
        ).andExpect(status().isOk)

        assertTrue(emailVerificationTokenRepository.findAll().isEmpty())
        verify(mailSender, never()).send(any<SimpleMailMessage>())
    }

    @Test
    fun `resend for an already-verified account does not send anything`() {
        register("resend2@example.com")
        val user = userRepository.findByEmail("resend2@example.com")!!
        user.emailVerified = true
        userRepository.save(user)
        emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByUser(user))

        mockMvc.perform(
            post("/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"resend2@example.com"}""")
        ).andExpect(status().isOk)

        assertTrue(emailVerificationTokenRepository.findByUser(user).isEmpty())
    }
}
