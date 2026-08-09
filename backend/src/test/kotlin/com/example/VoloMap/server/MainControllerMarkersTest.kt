package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
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

        val controller = MainController(repository, mock())
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

        val controller = MainController(repository, mock())
        val result = controller.markers(
            category = null, date = null, timeFrom = null, timeTo = null, search = "tierheim"
        )

        assertEquals(1, result.size)
        assertEquals("Projekt A", result[0].name)
    }
}
