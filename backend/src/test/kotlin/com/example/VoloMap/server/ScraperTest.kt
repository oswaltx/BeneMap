package com.example.VoloMap.server

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ScraperTest {

    @Test
    fun `built activity has no dateTime since the source tracks no appointment`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        whenever(geocodingService.geocode(any())).thenReturn(Pair(50.9413, 6.9583))

        val html = """
            <html><body>
                <div class="field">
                    <div class="field__label">Adresse der Vermittlungsstelle</div>
                    <div class="field__item">Domkloster 4, Köln</div>
                </div>
            </body></html>
        """.trimIndent()
        val document = Jsoup.parse(html)

        val scraper = Scraper(repository, geocodingService)
        val activity = scraper.buildActivityFromDocument(
            document, "https://engagementdatenbank.stadt-koeln.de/testprojekt", "Testprojekt", "Bildung"
        )

        assertNull(activity.dateTime)
        assertEquals("Testprojekt", activity.name)
        assertEquals("Bildung", activity.category)
        assertEquals("Domkloster 4, Köln", activity.addressText)
        assertEquals("https://engagementdatenbank.stadt-koeln.de/testprojekt", activity.sourceUrl)
        assertEquals(50.9413, activity.latitude)
        assertEquals(6.9583, activity.longitude)
    }

    @Test
    fun `prefers Einsatzort over Adresse der Vermittlungsstelle when both are present`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        whenever(geocodingService.geocode("Hohe Straße 12, 51149 Köln")).thenReturn(Pair(50.9, 6.95))

        val html = """
            <html><body>
                <div class="field">
                    <div class="field__label">Adresse der Vermittlungsstelle</div>
                    <div class="field__item">Clemensstraße 7, 50676 Köln</div>
                </div>
                <div class="field">
                    <div class="field__label">Einsatzort</div>
                    <div class="field__item">Hohe Straße 12, 51149 Köln</div>
                </div>
            </body></html>
        """.trimIndent()
        val document = Jsoup.parse(html)

        val scraper = Scraper(repository, geocodingService)
        val activity = scraper.buildActivityFromDocument(
            document, "https://engagementdatenbank.stadt-koeln.de/testprojekt", "Testprojekt", "Bildung"
        )

        assertEquals("Hohe Straße 12, 51149 Köln", activity.addressText)
        assertEquals(50.9, activity.latitude)
        assertEquals(6.95, activity.longitude)
        verify(geocodingService, never()).geocode("Clemensstraße 7, 50676 Köln")
    }

    @Test
    fun `falls back to Adresse der Vermittlungsstelle when Einsatzort is missing`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        whenever(geocodingService.geocode("Domkloster 4, Köln")).thenReturn(Pair(50.9413, 6.9583))

        val html = """
            <html><body>
                <div class="field">
                    <div class="field__label">Adresse der Vermittlungsstelle</div>
                    <div class="field__item">Domkloster 4, Köln</div>
                </div>
            </body></html>
        """.trimIndent()
        val document = Jsoup.parse(html)

        val scraper = Scraper(repository, geocodingService)
        val activity = scraper.buildActivityFromDocument(
            document, "https://engagementdatenbank.stadt-koeln.de/testprojekt", "Testprojekt", "Bildung"
        )

        assertEquals("Domkloster 4, Köln", activity.addressText)
        assertEquals(50.9413, activity.latitude)
    }
}
