package com.example.VoloMap.server

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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

    @Test
    fun `unauthenticated POST add is rejected`() {
        mockMvc.perform(
            post("/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Testaktion"}""")
        ).andExpect(status().isUnauthorized)
    }
}
