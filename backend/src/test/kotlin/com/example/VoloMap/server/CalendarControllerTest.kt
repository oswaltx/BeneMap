package com.example.VoloMap.server

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class CalendarControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var activitySignupRepository: ActivitySignupRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var emailVerificationTokenRepository: EmailVerificationTokenRepository

    @BeforeEach
    fun cleanUp() {
        activitySignupRepository.deleteAll()
        activityRepository.deleteAll()
        emailVerificationTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        activitySignupRepository.deleteAll()
        activityRepository.deleteAll()
        emailVerificationTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun registerAndSession(email: String, role: String): MockHttpSession {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"geheim123","name":"Test","role":"$role"}""")
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
    fun `calendar token is generated lazily and stays stable`() {
        val session = registerAndSession("volunteer@example.com", "USER")

        val first = mockMvc.perform(get("/auth/me/calendar-token").session(session))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val second = mockMvc.perform(get("/auth/me/calendar-token").session(session))
            .andReturn().response.contentAsString

        org.junit.jupiter.api.Assertions.assertEquals(first, second)
    }

    @Test
    fun `regenerate produces a different token`() {
        val session = registerAndSession("volunteer2@example.com", "USER")
        val before = mockMvc.perform(get("/auth/me/calendar-token").session(session))
            .andReturn().response.contentAsString

        val after = mockMvc.perform(post("/auth/me/calendar-token/regenerate").session(session))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertNotEquals(before, after)
    }

    @Test
    fun `ics feed contains only the token owner's signed-up activities`() {
        val session = registerAndSession("volunteer3@example.com", "USER")
        val user = userRepository.findByEmail("volunteer3@example.com")!!
        val activity = activityRepository.save(
            VolunteerActivity(name = "Meine Aktion", dateTime = LocalDateTime.now().plusDays(1))
        )
        activitySignupRepository.save(ActivitySignup(user = user, activity = activity))

        val tokenJson = mockMvc.perform(get("/auth/me/calendar-token").session(session))
            .andReturn().response.contentAsString
        val token = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(tokenJson)!!.groupValues[1]

        val ics = mockMvc.perform(get("/calendar/$token.ics"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        org.junit.jupiter.api.Assertions.assertTrue(ics.contains("Meine Aktion"))
    }

    @Test
    fun `ics feed for an unknown token returns 404`() {
        mockMvc.perform(get("/calendar/does-not-exist.ics"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `token endpoint without a session is unauthorized`() {
        mockMvc.perform(get("/auth/me/calendar-token"))
            .andExpect(status().isUnauthorized)
    }
}
