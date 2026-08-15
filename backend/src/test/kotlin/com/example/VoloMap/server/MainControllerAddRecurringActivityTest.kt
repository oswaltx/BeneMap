package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication
import java.time.LocalDateTime

class MainControllerAddRecurringActivityTest {

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
    fun `rejects an interval below 1 day`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Sprachcafé",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 0
        )

        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))

        assertEquals(400, result.statusCode.value())
        verify(repository, never()).save(any<VolunteerActivity>())
    }

    @Test
    fun `weekly interval produces one occurrence per week within the 3-month horizon`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Sprachcafé",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 7
        )

        @Suppress("UNCHECKED_CAST")
        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))
        val activities = result.body as List<VolunteerActivity>

        assertEquals(14, activities.size)
        assertEquals(LocalDateTime.parse("2026-08-15T10:00:00"), activities.first().dateTime)
        assertEquals(LocalDateTime.parse("2026-11-14T10:00:00"), activities.last().dateTime)
    }

    @Test
    fun `daily interval over 3 months is capped at 60 occurrences`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Tägliche Aktion",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 1
        )

        @Suppress("UNCHECKED_CAST")
        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))
        val activities = result.body as List<VolunteerActivity>

        assertEquals(60, activities.size)
    }

    @Test
    fun `geocodes the address exactly once and shares coordinates across all occurrences`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(geocodingService.geocode("Domkloster 4, Köln")).thenReturn(Pair(50.9413, 6.9583))
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Sprachcafé",
            addressText = "Domkloster 4, Köln",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 7
        )

        @Suppress("UNCHECKED_CAST")
        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))
        val activities = result.body as List<VolunteerActivity>

        verify(geocodingService, times(1)).geocode(any<String>())
        assertTrue(activities.all { it.latitude == 50.9413 && it.longitude == 6.9583 })
    }

    @Test
    fun `sets createdBy on every generated occurrence`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Sprachcafé",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 7
        )

        @Suppress("UNCHECKED_CAST")
        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))
        val activities = result.body as List<VolunteerActivity>

        assertTrue(activities.all { it.createdBy == provider })
    }

    @Test
    fun `saves occurrences without coordinates when geocoding finds nothing`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(geocodingService.geocode(any<String>())).thenReturn(null)
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Sprachcafé",
            addressText = "Nonexistent Place XYZ",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 7
        )

        @Suppress("UNCHECKED_CAST")
        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))
        val activities = result.body as List<VolunteerActivity>

        assertEquals(200, result.statusCode.value())
        assertTrue(activities.all { it.latitude == null && it.longitude == null })
    }

    @Test
    fun `does not geocode when no address is given`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Sprachcafé",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 7
        )

        @Suppress("UNCHECKED_CAST")
        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))
        val activities = result.body as List<VolunteerActivity>

        verify(geocodingService, never()).geocode(any<String>())
        assertNull(activities.first().latitude)
    }
}
