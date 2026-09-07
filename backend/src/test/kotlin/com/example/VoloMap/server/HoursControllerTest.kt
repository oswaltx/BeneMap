package com.example.VoloMap.server

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class HoursControllerTest {

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
    fun `sums durationHours across completed signups, defaulting when unset`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        val user = userRepository.findByEmail("volunteer@example.com")!!

        val withDuration = activityRepository.save(
            VolunteerActivity(name = "Mit Dauer", dateTime = LocalDateTime.now().minusDays(1), durationHours = 3.5)
        )
        val withoutDuration = activityRepository.save(
            VolunteerActivity(name = "Ohne Dauer", dateTime = LocalDateTime.now().minusDays(2), durationHours = null)
        )
        activitySignupRepository.save(ActivitySignup(user = user, activity = withDuration))
        activitySignupRepository.save(ActivitySignup(user = user, activity = withoutDuration))

        mockMvc.perform(get("/auth/me/hours").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalHours").value(3.5 + DEFAULT_ACTIVITY_DURATION_HOURS))
            .andExpect(jsonPath("$.completedActivityCount").value(2))
    }

    @Test
    fun `future signups do not count towards hours`() {
        val session = registerAndSession("volunteer2@example.com", "USER")
        val user = userRepository.findByEmail("volunteer2@example.com")!!

        val future = activityRepository.save(
            VolunteerActivity(name = "Zukunft", dateTime = LocalDateTime.now().plusDays(5), durationHours = 4.0)
        )
        activitySignupRepository.save(ActivitySignup(user = user, activity = future))

        mockMvc.perform(get("/auth/me/hours").session(session))
            .andExpect(jsonPath("$.totalHours").value(0.0))
            .andExpect(jsonPath("$.completedActivityCount").value(0))
    }

    @Test
    fun `undated activities do not count towards hours`() {
        val session = registerAndSession("volunteer3@example.com", "USER")
        val user = userRepository.findByEmail("volunteer3@example.com")!!

        val undated = activityRepository.save(VolunteerActivity(name = "Ohne Termin", dateTime = null))
        activitySignupRepository.save(ActivitySignup(user = user, activity = undated))

        mockMvc.perform(get("/auth/me/hours").session(session))
            .andExpect(jsonPath("$.totalHours").value(0.0))
            .andExpect(jsonPath("$.completedActivityCount").value(0))
    }

    @Test
    fun `hours without a session is unauthorized`() {
        mockMvc.perform(get("/auth/me/hours"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `certificate is a valid PDF`() {
        val session = registerAndSession("volunteer4@example.com", "USER")

        val result = mockMvc.perform(get("/auth/me/certificate.pdf").session(session))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/pdf"))
            .andReturn()

        val bytes = result.response.contentAsByteArray
        assertTrue(bytes.size > 4)
        assertEquals("%PDF", String(bytes, 0, 4, Charsets.US_ASCII))
    }

    @Test
    fun `certificate without a session is unauthorized`() {
        mockMvc.perform(get("/auth/me/certificate.pdf"))
            .andExpect(status().isUnauthorized)
    }
}
