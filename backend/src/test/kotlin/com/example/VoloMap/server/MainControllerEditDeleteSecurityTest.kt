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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class MainControllerEditDeleteSecurityTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var activityRatingRepository: ActivityRatingRepository

    @Autowired
    lateinit var emailVerificationTokenRepository: EmailVerificationTokenRepository

    @BeforeEach
    fun cleanUp() {
        activityRatingRepository.deleteAll()
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

    private fun createActivity(session: MockHttpSession, name: String): Long {
        val result = mockMvc.perform(
            post("/add")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name"}""")
        ).andReturn()
        val body = result.response.contentAsString
        return Regex(""""id":(\d+)""").find(body)!!.groupValues[1].toLong()
    }

    @Test
    fun `unauthenticated PUT and DELETE are rejected`() {
        val session = registerAndSession("owner1@example.com", "ANBIETER")
        val id = createActivity(session, "Testaktion")

        mockMvc.perform(
            put("/activities/$id").contentType(MediaType.APPLICATION_JSON).content("""{"name":"Hack"}""")
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(delete("/activities/$id")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `USER role cannot edit or delete`() {
        val ownerSession = registerAndSession("owner2@example.com", "ANBIETER")
        val id = createActivity(ownerSession, "Testaktion")
        val userSession = registerAndSession("user1@example.com", "USER")

        mockMvc.perform(
            put("/activities/$id").session(userSession).contentType(MediaType.APPLICATION_JSON).content("""{"name":"Hack"}""")
        ).andExpect(status().isForbidden)

        mockMvc.perform(delete("/activities/$id").session(userSession)).andExpect(status().isForbidden)
    }

    @Test
    fun `a different ANBIETER cannot edit or delete someone else's activity`() {
        val ownerSession = registerAndSession("owner3@example.com", "ANBIETER")
        val id = createActivity(ownerSession, "Testaktion")
        val otherSession = registerAndSession("other1@example.com", "ANBIETER")

        mockMvc.perform(
            put("/activities/$id").session(otherSession).contentType(MediaType.APPLICATION_JSON).content("""{"name":"Hack"}""")
        ).andExpect(status().isForbidden)

        mockMvc.perform(delete("/activities/$id").session(otherSession)).andExpect(status().isForbidden)
    }

    @Test
    fun `owner can edit their own activity`() {
        val session = registerAndSession("owner4@example.com", "ANBIETER")
        val id = createActivity(session, "Alter Name")

        mockMvc.perform(
            put("/activities/$id")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Neuer Name"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.geocodingFailed").value(false))
            .andExpect(jsonPath("$.activity.name").value("Neuer Name"))

        assertEquals("Neuer Name", activityRepository.findById(id).get().name)
    }

    @Test
    fun `owner can delete their own activity even with existing ratings`() {
        val ownerSession = registerAndSession("owner5@example.com", "ANBIETER")
        val id = createActivity(ownerSession, "Zu löschen")
        val raterSession = registerAndSession("rater1@example.com", "USER")

        mockMvc.perform(
            post("/activities/$id/ratings")
                .session(raterSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":5}""")
        ).andExpect(status().isOk)

        mockMvc.perform(delete("/activities/$id").session(ownerSession))
            .andExpect(status().isNoContent)

        assertEquals(0, activityRepository.count())
        assertEquals(0, activityRatingRepository.count())
    }
}
