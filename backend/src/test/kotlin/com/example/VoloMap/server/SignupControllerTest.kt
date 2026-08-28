package com.example.VoloMap.server

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class SignupControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var activitySignupRepository: ActivitySignupRepository

    @BeforeEach
    fun cleanUp() {
        activitySignupRepository.deleteAll()
        activityRepository.deleteAll()
        userRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        activitySignupRepository.deleteAll()
    }

    private fun registerAndSession(email: String, role: String): MockHttpSession {
        val result = mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"geheim123","name":"Test","role":"$role"}""")
        ).andReturn()
        return result.request.session as MockHttpSession
    }

    private fun createActivity(
        name: String = "Testaktion",
        maxParticipants: Int? = null,
        createdBy: User? = null,
    ): Long =
        activityRepository.save(
            VolunteerActivity(name = name, maxParticipants = maxParticipants, createdBy = createdBy)
        ).id

    @Test
    fun `signs up and count reflects it`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        val activityId = createActivity()

        mockMvc.perform(post("/activities/$activityId/signup").session(session))
            .andExpect(status().isOk)

        mockMvc.perform(get("/activities/$activityId/signups").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.signedUp").value(true))
    }

    @Test
    fun `signing up twice is a no-op, not a duplicate`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        val activityId = createActivity()

        mockMvc.perform(post("/activities/$activityId/signup").session(session)).andExpect(status().isOk)
        mockMvc.perform(post("/activities/$activityId/signup").session(session)).andExpect(status().isOk)

        mockMvc.perform(get("/activities/$activityId/signups").session(session))
            .andExpect(jsonPath("$.count").value(1))
    }

    @Test
    fun `withdraws a signup`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        val activityId = createActivity()
        mockMvc.perform(post("/activities/$activityId/signup").session(session)).andExpect(status().isOk)

        mockMvc.perform(delete("/activities/$activityId/signup").session(session))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/activities/$activityId/signups").session(session))
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.signedUp").value(false))
    }

    @Test
    fun `withdrawing without a prior signup is a no-op`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        val activityId = createActivity()

        mockMvc.perform(delete("/activities/$activityId/signup").session(session))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `signup is rejected once maxParticipants is reached`() {
        val activityId = createActivity(maxParticipants = 1)
        val sessionA = registerAndSession("a@example.com", "USER")
        val sessionB = registerAndSession("b@example.com", "USER")

        mockMvc.perform(post("/activities/$activityId/signup").session(sessionA))
            .andExpect(status().isOk)

        mockMvc.perform(post("/activities/$activityId/signup").session(sessionB))
            .andExpect(status().isConflict)

        mockMvc.perform(get("/activities/$activityId/signups"))
            .andExpect(jsonPath("$.count").value(1))
    }

    @Test
    fun `signing up without a session is rejected`() {
        val activityId = createActivity()
        mockMvc.perform(post("/activities/$activityId/signup"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `signing up as ANBIETER is rejected`() {
        val session = registerAndSession("anbieter@example.com", "ANBIETER")
        val activityId = createActivity()
        mockMvc.perform(post("/activities/$activityId/signup").session(session))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `signing up for a nonexistent activity returns 404`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        mockMvc.perform(post("/activities/999999/signup").session(session))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `participants list is only visible to the activity's own provider`() {
        val ownerSession = registerAndSession("anbieter@example.com", "ANBIETER")
        val owner = userRepository.findByEmail("anbieter@example.com")!!
        val activityId = createActivity(createdBy = owner)

        val volunteerSession = registerAndSession("volunteer@example.com", "USER")
        mockMvc.perform(post("/activities/$activityId/signup").session(volunteerSession))
            .andExpect(status().isOk)

        mockMvc.perform(get("/activities/$activityId/signups").session(ownerSession))
            .andExpect(jsonPath("$.participants[0].name").value("Test"))
            .andExpect(jsonPath("$.participants[0].email").value("volunteer@example.com"))

        mockMvc.perform(get("/activities/$activityId/signups").session(volunteerSession))
            .andExpect(jsonPath("$.participants.length()").value(0))

        mockMvc.perform(get("/activities/$activityId/signups"))
            .andExpect(jsonPath("$.participants.length()").value(0))
    }
}
