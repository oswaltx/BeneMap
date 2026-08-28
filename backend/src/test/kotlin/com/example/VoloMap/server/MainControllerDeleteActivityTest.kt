package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication
import java.util.Optional

class MainControllerDeleteActivityTest {

    private val owner = User(id = 1, email = "owner@example.com", passwordHash = "hashed", name = "Owner", role = Role.ANBIETER)
    private val otherAnbieter = User(id = 2, email = "other@example.com", passwordHash = "hashed", name = "Other", role = Role.ANBIETER)

    private fun authenticationFor(email: String): Authentication {
        val authentication: Authentication = mock()
        whenever(authentication.name).thenReturn(email)
        return authentication
    }

    @Test
    fun `owner can delete their activity, ratings are removed first`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val activityRatingRepository: ActivityRatingRepository = mock()
        val existing = VolunteerActivity(id = 5, name = "Alt", createdBy = owner)
        val ratings = listOf(
            ActivityRating(id = 10, user = owner, activity = existing, stars = 5)
        )
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(activityRatingRepository.findByActivity(existing)).thenReturn(ratings)

        val controller = MainController(repository, geocodingService, userRepository, activityRatingRepository, mock(), mock())
        val result = controller.deleteActivity(5, authenticationFor(owner.email))

        assertEquals(204, result.statusCode.value())
        verify(activityRatingRepository).deleteAll(ratings)
        verify(repository).delete(existing)
    }

    @Test
    fun `owner can delete their activity, signups are removed first`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val activitySignupRepository: ActivitySignupRepository = mock()
        val existing = VolunteerActivity(id = 5, name = "Alt", createdBy = owner)
        val signups = listOf(
            ActivitySignup(id = 10, user = owner, activity = existing)
        )
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(activitySignupRepository.findByActivity(existing)).thenReturn(signups)

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock(), activitySignupRepository)
        val result = controller.deleteActivity(5, authenticationFor(owner.email))

        assertEquals(204, result.statusCode.value())
        verify(activitySignupRepository).deleteAll(signups)
        verify(repository).delete(existing)
    }

    @Test
    fun `rejects delete from a non-owner with 403`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val existing = VolunteerActivity(id = 5, name = "Alt", createdBy = owner)
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(otherAnbieter.email)).thenReturn(otherAnbieter)

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock(), mock())
        val result = controller.deleteActivity(5, authenticationFor(otherAnbieter.email))

        assertEquals(403, result.statusCode.value())
        verify(repository, never()).delete(any<VolunteerActivity>())
    }

    @Test
    fun `returns 404 for a non-existent activity`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(repository.findById(999)).thenReturn(Optional.empty())

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock(), mock())
        val result = controller.deleteActivity(999, authenticationFor(owner.email))

        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `rejects delete of an activity with no owner (scraped data) with 403`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val existing = VolunteerActivity(id = 5, name = "Gescrapt", createdBy = null)
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock(), mock())
        val result = controller.deleteActivity(5, authenticationFor(owner.email))

        assertEquals(403, result.statusCode.value())
        verify(repository, never()).delete(any<VolunteerActivity>())
    }
}
