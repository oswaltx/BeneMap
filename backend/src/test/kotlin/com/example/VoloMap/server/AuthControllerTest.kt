package com.example.VoloMap.server

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
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @BeforeEach
    fun cleanUp() {
        userRepository.deleteAll()
    }

    @Test
    fun `registers a new user and returns its profile`() {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"anna@example.com","password":"geheim123","name":"Anna","role":"USER"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("anna@example.com"))
            .andExpect(jsonPath("$.name").value("Anna"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.id").isNumber)
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
    fun `logs in with correct credentials and rejects wrong password`() {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ben@example.com","password":"richtig123","name":"Ben","role":"ANBIETER"}""")
        ).andExpect(status().isOk)

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
        val register = mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"cara@example.com","password":"geheim123","name":"Cara","role":"USER"}""")
        ).andReturn()
        val session = register.request.session as MockHttpSession

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
}
