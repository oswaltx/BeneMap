package com.example.VoloMap.server

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.timeout
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

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

    private fun markVerified(email: String) {
        val user = userRepository.findByEmail(email)!!
        user.emailVerified = true
        userRepository.save(user)
    }

    private fun registerVerifyAndLogin(email: String, password: String, name: String, role: String): MockHttpSession {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password","name":"$name","role":"$role"}""")
        ).andExpect(status().isOk)
        markVerified(email)
        val result = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password"}""")
        ).andReturn()
        return result.request.session as MockHttpSession
    }

    @Test
    fun `registering creates an unverified account and sends a verification email, without logging in`() {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"anna@example.com","password":"geheim123","name":"Anna","role":"USER"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").exists())

        val user = userRepository.findByEmail("anna@example.com")!!
        assert(!user.emailVerified) { "new user should not be verified yet" }
        assert(emailVerificationTokenRepository.findByUser(user).size == 1)
        org.mockito.kotlin.verify(mailSender, timeout(2000)).send(any<SimpleMailMessage>())

        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `rejects registration with an already-used email`() {
        val payload = """{"email":"dup@example.com","password":"geheim123","name":"Dup","role":"USER"}"""
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk)

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isConflict)
    }

    @Test
    fun `login is rejected until the email is verified, then works`() {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ben@example.com","password":"richtig123","name":"Ben","role":"ANBIETER"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ben@example.com","password":"richtig123"}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").exists())

        markVerified("ben@example.com")

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ben@example.com","password":"falsch"}""")
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ben@example.com","password":"richtig123"}""")
        ).andExpect(status().isOk)
    }

    @Test
    fun `me reflects session state across login and logout`() {
        val session = registerVerifyAndLogin("cara@example.com", "geheim123", "Cara", "USER")

        mockMvc.perform(get("/auth/me").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Cara"))
            .andExpect(jsonPath("$.id").isNumber)

        mockMvc.perform(post("/auth/logout").session(session))
            .andExpect(status().isOk)

        mockMvc.perform(get("/auth/me").session(session))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `me without a session is unauthorized`() {
        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `owner can update their own profile photo and website`() {
        val session = registerVerifyAndLogin("dana@example.com", "geheim123", "Dana", "ANBIETER")

        mockMvc.perform(
            put("/auth/me")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"photoUrl":"https://example.com/dana.jpg","websiteUrl":"https://dana-vereint.de"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.photoUrl").value("https://example.com/dana.jpg"))
            .andExpect(jsonPath("$.websiteUrl").value("https://dana-vereint.de"))
            .andExpect(jsonPath("$.email").value("dana@example.com"))
            .andExpect(jsonPath("$.role").value("ANBIETER"))
    }

    @Test
    fun `forces a scheme onto a photo URL without one`() {
        val session = registerVerifyAndLogin("gustav@example.com", "geheim123", "Gustav", "ANBIETER")

        mockMvc.perform(
            put("/auth/me")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"photoUrl":"javascript:alert(1)"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.photoUrl").value("https://javascript:alert(1)"))
    }

    @Test
    fun `updating profile without a session is unauthorized`() {
        mockMvc.perform(
            put("/auth/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"photoUrl":"https://example.com/x.jpg"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `website URL without a scheme gets https prepended`() {
        val session = registerVerifyAndLogin("eve@example.com", "geheim123", "Eve", "ANBIETER")

        mockMvc.perform(
            put("/auth/me")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"websiteUrl":"www.eve-verein.de"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.websiteUrl").value("https://www.eve-verein.de"))
    }

    @Test
    fun `updating profile cannot smuggle in role, email, or name changes`() {
        val session = registerVerifyAndLogin("frank@example.com", "geheim123", "Frank", "ANBIETER")

        mockMvc.perform(
            put("/auth/me")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"photoUrl":"https://example.com/frank.jpg","role":"USER","email":"attacker@evil.de","name":"Attacker"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("ANBIETER"))
            .andExpect(jsonPath("$.email").value("frank@example.com"))
            .andExpect(jsonPath("$.name").value("Frank"))
            .andExpect(jsonPath("$.photoUrl").value("https://example.com/frank.jpg"))
    }
}
