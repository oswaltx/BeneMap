package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class RatingControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var activityRatingRepository: ActivityRatingRepository

    @Autowired
    lateinit var providerRatingRepository: ProviderRatingRepository

    @BeforeEach
    fun cleanUp() {
        activityRatingRepository.deleteAll()
        providerRatingRepository.deleteAll()
        activityRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun registerAndSession(email: String, role: String): MockHttpSession {
        val result = mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"geheim123","name":"Test","role":"$role"}""")
        ).andReturn()
        return result.request.session as MockHttpSession
    }

    private fun createActivity(name: String = "Testaktion"): Long =
        activityRepository.save(VolunteerActivity(name = name)).id

    @Test
    fun `posts an activity rating and returns it via GET`() {
        val session = registerAndSession("rater@example.com", "USER")
        val activityId = createActivity()

        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":4,"comment":"Gut organisiert"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(get("/activities/$activityId/ratings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.average").value(4.0))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.ratings[0].userName").value("Test"))
            .andExpect(jsonPath("$.ratings[0].stars").value(4))
            .andExpect(jsonPath("$.ratings[0].comment").value("Gut organisiert"))
    }

    @Test
    fun `re-rating the same activity updates instead of duplicating`() {
        val session = registerAndSession("rater@example.com", "USER")
        val activityId = createActivity()

        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":2,"comment":"Erster Eindruck"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":5,"comment":"Nach Nachdenken doch super"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(get("/activities/$activityId/ratings"))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.average").value(5.0))
            .andExpect(jsonPath("$.ratings[0].comment").value("Nach Nachdenken doch super"))
    }

    @Test
    fun `rating an activity without a session is rejected`() {
        val activityId = createActivity()
        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":3}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `rating an activity as ANBIETER is rejected`() {
        val session = registerAndSession("anbieter@example.com", "ANBIETER")
        val activityId = createActivity()
        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":3}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `rating a nonexistent activity returns 404`() {
        val session = registerAndSession("rater@example.com", "USER")
        mockMvc.perform(
            post("/activities/999999/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":3}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `stars outside 1-5 is rejected`() {
        val session = registerAndSession("rater@example.com", "USER")
        val activityId = createActivity()
        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":6}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `GET includes myRating only for the logged-in user's own rating`() {
        val activityId = createActivity()
        val sessionA = registerAndSession("a@example.com", "USER")
        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":5}""")
        ).andExpect(status().isOk)

        mockMvc.perform(get("/activities/$activityId/ratings").session(sessionA))
            .andExpect(jsonPath("$.myRating.stars").value(5))

        mockMvc.perform(get("/activities/$activityId/ratings"))
            .andExpect(jsonPath("$.myRating").doesNotExist())
    }

    @Test
    fun `posts a provider rating and returns it via GET`() {
        val session = registerAndSession("rater@example.com", "USER")
        registerAndSession("anbieter@example.com", "ANBIETER")
        val providerId = userRepository.findByEmail("anbieter@example.com")!!.id

        mockMvc.perform(
            post("/providers/$providerId/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":3,"comment":"Zuverlässig"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(get("/providers/$providerId/ratings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.average").value(3.0))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.ratings[0].comment").value("Zuverlässig"))
    }

    @Test
    fun `rating a non-provider user as provider returns 404`() {
        val session = registerAndSession("rater@example.com", "USER")
        registerAndSession("otheruser@example.com", "USER")
        val notAProvider = userRepository.findByEmail("otheruser@example.com")!!.id

        mockMvc.perform(
            post("/providers/$notAProvider/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":3}""")
        ).andExpect(status().isNotFound)
    }
}
