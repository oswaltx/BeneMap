package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication
import java.time.LocalDateTime
import java.util.Optional

class MainControllerEditActivityTest {

    private val owner = User(
        id = 1,
        email = "owner@example.com",
        passwordHash = "hashed",
        name = "Owner",
        role = Role.ANBIETER
    )

    private val otherAnbieter = User(
        id = 2,
        email = "other@example.com",
        passwordHash = "hashed",
        name = "Other",
        role = Role.ANBIETER
    )

    private fun authenticationFor(email: String): Authentication {
        val authentication: Authentication = mock()
        whenever(authentication.name).thenReturn(email)
        return authentication
    }

    @Test
    fun `owner can update name without touching coordinates when address is unchanged`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val activityRatingRepository: ActivityRatingRepository = mock()
        val existing = VolunteerActivity(
            id = 5, name = "Alt", addressText = "Domkloster 4, Köln",
            latitude = 50.9413, longitude = 6.9583, createdBy = owner
        )
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, activityRatingRepository, mock())
        val req = UpdateActivityRequest(name = "Neu", addressText = "Domkloster 4, Köln")

        val result = controller.updateActivity(5, req, authenticationFor(owner.email))

        assertEquals(200, result.statusCode.value())
        verify(geocodingService, never()).geocode(any<String>())
        val body = result.body as UpdateActivityResponse
        assertEquals(50.9413, body.activity.latitude)
        assertEquals(false, body.geocodingFailed)
    }

    @Test
    fun `re-geocodes when address changes`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val existing = VolunteerActivity(
            id = 5, name = "Alt", addressText = "Alte Adresse",
            latitude = 1.0, longitude = 2.0, createdBy = owner
        )
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(geocodingService.geocode("Neue Adresse")).thenReturn(Pair(50.0, 6.0))
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = UpdateActivityRequest(name = "Alt", addressText = "Neue Adresse")

        val result = controller.updateActivity(5, req, authenticationFor(owner.email))

        val body = result.body as UpdateActivityResponse
        assertEquals(50.0, body.activity.latitude)
        assertEquals(6.0, body.activity.longitude)
        assertEquals(false, body.geocodingFailed)
    }

    @Test
    fun `keeps old coordinates when new address cannot be geocoded`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val existing = VolunteerActivity(
            id = 5, name = "Alt", addressText = "Alte Adresse",
            latitude = 1.0, longitude = 2.0, createdBy = owner
        )
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(geocodingService.geocode(any<String>())).thenReturn(null)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = UpdateActivityRequest(name = "Alt", addressText = "Nicht auffindbar")

        val result = controller.updateActivity(5, req, authenticationFor(owner.email))

        val body = result.body as UpdateActivityResponse
        assertEquals(1.0, body.activity.latitude)
        assertEquals(2.0, body.activity.longitude)
        assertEquals(true, body.geocodingFailed)
    }

    @Test
    fun `rejects update from a non-owner with 403`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val existing = VolunteerActivity(id = 5, name = "Alt", createdBy = owner)
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(otherAnbieter.email)).thenReturn(otherAnbieter)

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = UpdateActivityRequest(name = "Gehackt")

        val result = controller.updateActivity(5, req, authenticationFor(otherAnbieter.email))

        assertEquals(403, result.statusCode.value())
        verify(repository, never()).save(any<VolunteerActivity>())
    }

    @Test
    fun `returns 404 for a non-existent activity`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(repository.findById(999)).thenReturn(Optional.empty())

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = UpdateActivityRequest(name = "Egal")

        val result = controller.updateActivity(999, req, authenticationFor(owner.email))

        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `omitting dateTime keeps the existing value unchanged`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val originalDateTime = LocalDateTime.of(2026, 1, 1, 10, 0)
        val existing = VolunteerActivity(id = 5, name = "Alt", dateTime = originalDateTime, createdBy = owner)
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = UpdateActivityRequest(name = "Alt", dateTime = null)

        val result = controller.updateActivity(5, req, authenticationFor(owner.email))

        assertEquals(originalDateTime, (result.body as UpdateActivityResponse).activity.dateTime)
    }
}
