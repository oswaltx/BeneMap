package com.example.VoloMap.server

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
class AccountDeletionTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var activityRatingRepository: ActivityRatingRepository

    @Autowired
    lateinit var providerRatingRepository: ProviderRatingRepository

    @Autowired
    lateinit var activitySignupRepository: ActivitySignupRepository

    @BeforeEach
    fun cleanUp() {
        activitySignupRepository.deleteAll()
        activityRatingRepository.deleteAll()
        providerRatingRepository.deleteAll()
        activityRepository.deleteAll()
        userRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        activitySignupRepository.deleteAll()
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

    @Test
    fun `wrong password returns 401 and deletes nothing`() {
        val session = registerAndSession("wrongpw@example.com", "USER")

        mockMvc.perform(
            delete("/auth/me")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"falschesPasswort"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Passwort ist falsch."))

        assertTrue(userRepository.existsByEmail("wrongpw@example.com"))
    }

    @Test
    fun `deleting a USER account removes their own ratings and signups`() {
        val providerSession = registerAndSession("provider1@example.com", "ANBIETER")
        val provider = userRepository.findByEmail("provider1@example.com")!!
        val activityId = activityRepository.save(
            VolunteerActivity(name = "Testaktion", createdBy = provider)
        ).id

        val userSession = registerAndSession("volunteer1@example.com", "USER")
        val user = userRepository.findByEmail("volunteer1@example.com")!!

        mockMvc.perform(post("/activities/$activityId/signup").session(userSession))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":5}""")
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/providers/${provider.id}/ratings")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":4}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            delete("/auth/me")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"geheim123"}""")
        ).andExpect(status().isNoContent)

        assertTrue(activitySignupRepository.findByUser(user).isEmpty())
        assertTrue(activityRatingRepository.findByUser(user).isEmpty())
        assertTrue(providerRatingRepository.findByUser(user).isEmpty())
        assertNull(userRepository.findByEmail("volunteer1@example.com"))
        // The activity and the provider's own account are untouched by a USER's deletion
        assertTrue(activityRepository.findById(activityId).isPresent)
        assertTrue(userRepository.existsByEmail("provider1@example.com"))
    }

    @Test
    fun `deleting an ANBIETER account removes their activities, dependent data, and received provider ratings`() {
        val providerSession = registerAndSession("provider2@example.com", "ANBIETER")
        val provider = userRepository.findByEmail("provider2@example.com")!!
        val activityId = activityRepository.save(
            VolunteerActivity(name = "Testaktion 2", createdBy = provider)
        ).id

        val userSession = registerAndSession("volunteer2@example.com", "USER")

        mockMvc.perform(post("/activities/$activityId/signup").session(userSession))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":5}""")
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/providers/${provider.id}/ratings")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":3}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            delete("/auth/me")
                .session(providerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"geheim123"}""")
        ).andExpect(status().isNoContent)

        assertTrue(activityRepository.findById(activityId).isEmpty)
        assertNull(userRepository.findByEmail("provider2@example.com"))
        // Dependent rows tied to the deleted activity/provider are gone too
        assertTrue(activitySignupRepository.findAll().isEmpty())
        assertTrue(activityRatingRepository.findAll().isEmpty())
        assertTrue(providerRatingRepository.findAll().isEmpty())
        // The rating user's own account is untouched by the provider's deletion
        assertTrue(userRepository.existsByEmail("volunteer2@example.com"))
    }

    @Test
    fun `deletion invalidates the session`() {
        val session = registerAndSession("invalidate@example.com", "USER")

        mockMvc.perform(
            delete("/auth/me")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"geheim123"}""")
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/auth/me").session(session))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deletion-impact reflects the caller's own activity count`() {
        val providerSession = registerAndSession("provider3@example.com", "ANBIETER")
        val provider = userRepository.findByEmail("provider3@example.com")!!

        mockMvc.perform(get("/auth/me/deletion-impact").session(providerSession))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activityCount").value(0))

        activityRepository.save(VolunteerActivity(name = "A", createdBy = provider))
        activityRepository.save(VolunteerActivity(name = "B", createdBy = provider))

        mockMvc.perform(get("/auth/me/deletion-impact").session(providerSession))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activityCount").value(2))

        val userSession = registerAndSession("volunteer3@example.com", "USER")
        mockMvc.perform(get("/auth/me/deletion-impact").session(userSession))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activityCount").value(0))
    }
}
