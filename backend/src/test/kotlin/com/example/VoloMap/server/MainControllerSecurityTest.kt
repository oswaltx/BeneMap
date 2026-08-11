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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class MainControllerSecurityTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @BeforeEach
    fun cleanUp() {
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
    fun `unauthenticated POST add is rejected`() {
        mockMvc.perform(
            post("/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Testaktion"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `logged-in USER cannot add an activity`() {
        val session = registerAndSession("user@example.com", "USER")
        mockMvc.perform(
            post("/add")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Testaktion"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `logged-in ANBIETER can add an activity, becomes its owner, and no password hash leaks`() {
        val session = registerAndSession("anbieter@example.com", "ANBIETER")
        mockMvc.perform(
            post("/add")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Testaktion"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Testaktion"))
            .andExpect(jsonPath("$.createdBy").doesNotExist())

        val saved = activityRepository.findAll().first()
        assertEquals("anbieter@example.com", saved.createdBy?.email)
    }
}
