package com.example.VoloMap.server

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["admin.emails=admin@example.com, OTHER-ADMIN@example.com"])
class AdminControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var emailVerificationTokenRepository: EmailVerificationTokenRepository

    @BeforeEach
    fun cleanUp() {
        activityRepository.deleteAll()
        emailVerificationTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
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
    fun `admin can verify a provider`() {
        val adminSession = registerAndSession("admin@example.com", "USER")
        registerAndSession("anbieter@example.com", "ANBIETER")
        val provider = userRepository.findByEmail("anbieter@example.com")!!

        mockMvc.perform(
            patch("/admin/providers/${provider.id}/verify")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verified":true}""")
        ).andExpect(status().isOk)

        val updated = userRepository.findById(provider.id).get()
        assert(updated.verified)
    }

    @Test
    fun `admin allowlist check is case-insensitive`() {
        val adminSession = registerAndSession("other-admin@example.com", "USER")
        registerAndSession("anbieter2@example.com", "ANBIETER")
        val provider = userRepository.findByEmail("anbieter2@example.com")!!

        mockMvc.perform(
            patch("/admin/providers/${provider.id}/verify")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verified":true}""")
        ).andExpect(status().isOk)
    }

    @Test
    fun `non-admin cannot verify a provider`() {
        val nonAdminSession = registerAndSession("regular@example.com", "USER")
        registerAndSession("anbieter3@example.com", "ANBIETER")
        val provider = userRepository.findByEmail("anbieter3@example.com")!!

        mockMvc.perform(
            patch("/admin/providers/${provider.id}/verify")
                .session(nonAdminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verified":true}""")
        ).andExpect(status().isForbidden)

        val unchanged = userRepository.findById(provider.id).get()
        assert(!unchanged.verified)
    }

    @Test
    fun `verifying without a session is unauthorized`() {
        registerAndSession("anbieter4@example.com", "ANBIETER")
        val provider = userRepository.findByEmail("anbieter4@example.com")!!

        mockMvc.perform(
            patch("/admin/providers/${provider.id}/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verified":true}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `verifying a nonexistent provider returns 404`() {
        val adminSession = registerAndSession("admin@example.com", "USER")

        mockMvc.perform(
            patch("/admin/providers/999999/verify")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verified":true}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `verified provider shows up on markers`() {
        val adminSession = registerAndSession("admin@example.com", "USER")
        val providerSession = registerAndSession("anbieter5@example.com", "ANBIETER")
        val provider = userRepository.findByEmail("anbieter5@example.com")!!

        mockMvc.perform(
            post("/add")
                .session(providerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Test","latitude":50.9,"longitude":6.9}""")
        )

        mockMvc.perform(get("/markers"))
            .andExpect(jsonPath("$[0].providerVerified").value(false))

        mockMvc.perform(
            patch("/admin/providers/${provider.id}/verify")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verified":true}""")
        ).andExpect(status().isOk)

        mockMvc.perform(get("/markers"))
            .andExpect(jsonPath("$[0].providerVerified").value(true))
    }
}
