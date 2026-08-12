package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class MainControllerMarkersTest {

    private fun activity(
        name: String,
        category: String,
        addressText: String,
        dateTime: LocalDateTime,
        lat: Double = 50.0,
        lng: Double = 6.0
    ) = VolunteerActivity(
        name = name,
        category = category,
        addressText = addressText,
        dateTime = dateTime,
        latitude = lat,
        longitude = lng
    )

    @Test
    fun `combines category, search and time range filters with AND semantics`() {
        val repository = mock<VolunteerActivityRepository>()
        val matching = activity(
            name = "Umweltaktion Park",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        val wrongCategory = activity(
            name = "Umweltaktion Wald",
            category = "Soziales",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        val wrongTime = activity(
            name = "Umweltaktion Abends",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 20, 0)
        )
        val wrongSearch = activity(
            name = "Seniorenbegleitung",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        whenever(repository.findAll()).thenReturn(
            listOf(matching, wrongCategory, wrongTime, wrongSearch)
        )

        val controller = MainController(repository, mock(), mock(), mock(), mock())
        val result = controller.markers(
            category = "Umwelt",
            date = null,
            timeFrom = 8,
            timeTo = 12,
            search = "Umweltaktion"
        )

        assertEquals(1, result.size)
        assertEquals("Umweltaktion Park", result[0].name)
    }

    @Test
    fun `search matches name, address or description case-insensitively`() {
        val repository = mock<VolunteerActivityRepository>()
        val byDescription = VolunteerActivity(
            name = "Projekt A",
            description = "Hilfe im TIERHEIM Köln",
            latitude = 50.0,
            longitude = 6.0
        )
        val noMatch = VolunteerActivity(
            name = "Projekt B",
            description = "Nachbarschaftshilfe",
            latitude = 50.0,
            longitude = 6.0
        )
        whenever(repository.findAll()).thenReturn(listOf(byDescription, noMatch))

        val controller = MainController(repository, mock(), mock(), mock(), mock())
        val result = controller.markers(
            category = null, date = null, timeFrom = null, timeTo = null, search = "tierheim"
        )

        assertEquals(1, result.size)
        assertEquals("Projekt A", result[0].name)
    }

    @Test
    fun `includes rating averages and provider identity for an activity`() {
        val repository = mock<VolunteerActivityRepository>()
        val activityRatingRepository = mock<ActivityRatingRepository>()
        val providerRatingRepository = mock<ProviderRatingRepository>()
        val provider = User(id = 7, email = "anbieter@example.com", passwordHash = "x", name = "Anbieter Anna", role = Role.ANBIETER)
        val rated = activity(
            name = "Bewertete Aktion",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        ).also { it.createdBy = provider }
        whenever(repository.findAll()).thenReturn(listOf(rated))
        whenever(activityRatingRepository.findAll()).thenReturn(
            listOf(
                ActivityRating(user = mock(), activity = rated, stars = 4),
                ActivityRating(user = mock(), activity = rated, stars = 2),
            )
        )
        whenever(providerRatingRepository.findAll()).thenReturn(
            listOf(ProviderRating(user = mock(), provider = provider, stars = 5))
        )

        val controller = MainController(repository, mock(), mock(), activityRatingRepository, providerRatingRepository)
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals(3.0, result[0].activityRating)
        assertEquals(2, result[0].activityRatingCount)
        assertEquals(7L, result[0].providerId)
        assertEquals("Anbieter Anna", result[0].providerName)
        assertEquals(5.0, result[0].providerRating)
        assertEquals(1, result[0].providerRatingCount)
    }

    @Test
    fun `rating fields are null and zero when an activity has no ratings or owner`() {
        val repository = mock<VolunteerActivityRepository>()
        val activityRatingRepository = mock<ActivityRatingRepository>()
        val providerRatingRepository = mock<ProviderRatingRepository>()
        val unrated = activity(
            name = "Unbewertete Aktion",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        whenever(repository.findAll()).thenReturn(listOf(unrated))
        whenever(activityRatingRepository.findAll()).thenReturn(emptyList())
        whenever(providerRatingRepository.findAll()).thenReturn(emptyList())

        val controller = MainController(repository, mock(), mock(), activityRatingRepository, providerRatingRepository)
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertNull(result[0].activityRating)
        assertEquals(0, result[0].activityRatingCount)
        assertNull(result[0].providerId)
        assertNull(result[0].providerName)
        assertNull(result[0].providerRating)
        assertEquals(0, result[0].providerRatingCount)
    }
}
