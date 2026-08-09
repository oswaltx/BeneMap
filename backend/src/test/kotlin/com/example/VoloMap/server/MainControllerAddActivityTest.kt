package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MainControllerAddActivityTest {

    @Test
    fun `geocodes address when coordinates are missing`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        whenever(geocodingService.geocode("Domkloster 4, Köln")).thenReturn(Pair(50.9413, 6.9583))
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService)
        val activity = VolunteerActivity(name = "Test", addressText = "Domkloster 4, Köln")

        val result = controller.addActivity(activity)

        assertEquals(50.9413, result.body?.latitude)
        assertEquals(6.9583, result.body?.longitude)
        verify(geocodingService).geocode("Domkloster 4, Köln")
    }

    @Test
    fun `saves activity without coordinates when geocoding finds nothing`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        whenever(geocodingService.geocode(any<String>())).thenReturn(null)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService)
        val activity = VolunteerActivity(name = "Test", addressText = "Nonexistent Place XYZ")

        val result = controller.addActivity(activity)

        assertEquals(200, result.statusCode.value())
        assertNull(result.body?.latitude)
        assertNull(result.body?.longitude)
    }

    @Test
    fun `does not call geocoding when coordinates are already set`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService)
        val activity = VolunteerActivity(name = "Test", latitude = 1.0, longitude = 2.0)

        controller.addActivity(activity)

        verify(geocodingService, org.mockito.kotlin.never()).geocode(any<String>())
    }
}
