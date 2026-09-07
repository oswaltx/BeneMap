package com.example.VoloMap.server

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
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
class FavoriteControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var favoriteRepository: FavoriteRepository

    @Autowired
    lateinit var emailVerificationTokenRepository: EmailVerificationTokenRepository

    @BeforeEach
    fun cleanUp() {
        favoriteRepository.deleteAll()
        activityRepository.deleteAll()
        emailVerificationTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        favoriteRepository.deleteAll()
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

    private fun createActivity(name: String = "Testaktion"): Long =
        activityRepository.save(VolunteerActivity(name = name)).id

    @Test
    fun `favoriting an activity makes it show up in favorites ids`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        val activityId = createActivity()

        mockMvc.perform(post("/activities/$activityId/favorite").session(session))
            .andExpect(status().isOk)

        mockMvc.perform(get("/favorites/ids").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0]").value(activityId))
    }

    @Test
    fun `favoriting twice is a no-op, not a duplicate`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        val activityId = createActivity()

        mockMvc.perform(post("/activities/$activityId/favorite").session(session)).andExpect(status().isOk)
        mockMvc.perform(post("/activities/$activityId/favorite").session(session)).andExpect(status().isOk)

        mockMvc.perform(get("/favorites/ids").session(session))
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `unfavoriting removes it from favorites ids`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        val activityId = createActivity()
        mockMvc.perform(post("/activities/$activityId/favorite").session(session)).andExpect(status().isOk)

        mockMvc.perform(delete("/activities/$activityId/favorite").session(session))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/favorites/ids").session(session))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `unfavoriting without a prior favorite is a no-op`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        val activityId = createActivity()

        mockMvc.perform(delete("/activities/$activityId/favorite").session(session))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `favoriting a nonexistent activity returns 404`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        mockMvc.perform(post("/activities/999999/favorite").session(session))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `favoriting without a session is rejected`() {
        val activityId = createActivity()
        mockMvc.perform(post("/activities/$activityId/favorite"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `favorites ids without a session is rejected`() {
        mockMvc.perform(get("/favorites/ids"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `favorites are per user`() {
        val activityId = createActivity()
        val sessionA = registerAndSession("a@example.com", "USER")
        val sessionB = registerAndSession("b@example.com", "USER")

        mockMvc.perform(post("/activities/$activityId/favorite").session(sessionA)).andExpect(status().isOk)

        mockMvc.perform(get("/favorites/ids").session(sessionA))
            .andExpect(jsonPath("$.length()").value(1))
        mockMvc.perform(get("/favorites/ids").session(sessionB))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `deleting an activity also removes it from everyone's favorites`() {
        val ownerSession = registerAndSession("anbieter@example.com", "ANBIETER")
        val owner = userRepository.findByEmail("anbieter@example.com")!!
        val activityId = activityRepository.save(VolunteerActivity(name = "Testaktion", createdBy = owner)).id

        val volunteerSession = registerAndSession("volunteer@example.com", "USER")
        mockMvc.perform(post("/activities/$activityId/favorite").session(volunteerSession)).andExpect(status().isOk)

        mockMvc.perform(delete("/activities/$activityId").session(ownerSession))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/favorites/ids").session(volunteerSession))
            .andExpect(jsonPath("$.length()").value(0))
    }
}
