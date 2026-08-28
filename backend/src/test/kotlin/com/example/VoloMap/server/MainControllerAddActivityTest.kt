package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication

class MainControllerAddActivityTest {

    private val provider = User(
        email = "anbieter@example.com",
        passwordHash = "hashed",
        name = "Anbieter Anna",
        role = Role.ANBIETER
    )

    private fun authenticationFor(email: String): Authentication {
        val authentication: Authentication = mock()
        whenever(authentication.name).thenReturn(email)
        return authentication
    }

    @Test
    fun `geocodes address when coordinates are missing`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(geocodingService.geocode("Domkloster 4, Köln")).thenReturn(Pair(50.9413, 6.9583))
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock(), mock())
        val activity = VolunteerActivity(name = "Test", addressText = "Domkloster 4, Köln")

        val result = controller.addActivity(activity, authenticationFor(provider.email))

        assertEquals(50.9413, result.body?.latitude)
        assertEquals(6.9583, result.body?.longitude)
        assertEquals(provider, result.body?.createdBy)
        verify(geocodingService).geocode("Domkloster 4, Köln")
    }

    @Test
    fun `saves activity without coordinates when geocoding finds nothing`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(geocodingService.geocode(any<String>())).thenReturn(null)
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock(), mock())
        val activity = VolunteerActivity(name = "Test", addressText = "Nonexistent Place XYZ")

        val result = controller.addActivity(activity, authenticationFor(provider.email))

        assertEquals(200, result.statusCode.value())
        assertNull(result.body?.latitude)
        assertNull(result.body?.longitude)
    }

    @Test
    fun `does not call geocoding when coordinates are already set`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock(), mock())
        val activity = VolunteerActivity(name = "Test", latitude = 1.0, longitude = 2.0)

        controller.addActivity(activity, authenticationFor(provider.email))

        verify(geocodingService, org.mockito.kotlin.never()).geocode(any<String>())
    }

    @Test
    fun `normalizes and caps photo URLs when adding an activity`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock(), mock())
        val rawUrls = (1..12).joinToString("\n") { "https://example.com/photo$it.jpg" }
        val activity = VolunteerActivity(name = "Test", latitude = 1.0, longitude = 2.0, photoUrls = rawUrls)

        val result = controller.addActivity(activity, authenticationFor(provider.email))

        val storedLines = result.body?.photoUrls?.lines() ?: emptyList()
        assertEquals(10, storedLines.size)
        assertEquals("https://example.com/photo1.jpg", storedLines.first())
    }

    @Test
    fun `blank photo URLs field is stored as null`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock(), mock())
        val activity = VolunteerActivity(name = "Test", latitude = 1.0, longitude = 2.0, photoUrls = "   \n  \n")

        val result = controller.addActivity(activity, authenticationFor(provider.email))

        assertNull(result.body?.photoUrls)
    }

    @Test
    fun `clears a client-supplied sourceUrl`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock(), mock())
        val activity = VolunteerActivity(
            name = "Test",
            latitude = 1.0,
            longitude = 2.0,
            sourceUrl = "javascript:alert(1)"
        )

        val result = controller.addActivity(activity, authenticationFor(provider.email))

        assertNull(result.body?.sourceUrl)
    }

    @Test
    fun `clears client-supplied Vermittlungsstelle contact fields`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock(), mock())
        val activity = VolunteerActivity(
            name = "Test",
            latitude = 1.0,
            longitude = 2.0,
            sourceContactName = "Fake Stadt Köln",
            sourceContactWebsite = "https://evil.example",
            sourceContactEmail = "fake@evil.example",
            sourceContactPhone = "000"
        )

        val result = controller.addActivity(activity, authenticationFor(provider.email))

        assertNull(result.body?.sourceContactName)
        assertNull(result.body?.sourceContactWebsite)
        assertNull(result.body?.sourceContactEmail)
        assertNull(result.body?.sourceContactPhone)
    }
}
