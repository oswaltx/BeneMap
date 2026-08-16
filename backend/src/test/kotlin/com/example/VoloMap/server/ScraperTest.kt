package com.example.VoloMap.server

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
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
                    <div class="field__label">Projektname</div>
                    <div class="field__item">Testprojekt</div>
                </div>
                <div class="field">
                    <div class="field__label">Adresse der Vermittlungsstelle</div>
                    <div class="field__item">Domkloster 4, Köln</div>
                </div>
            </body></html>
        """.trimIndent()
        val document = Jsoup.parse(html)

        val scraper = Scraper(repository, geocodingService)
        val activity = scraper.buildActivityFromDocument(document, "https://engagementdatenbank.stadt-koeln.de/testprojekt")

        assertNull(activity.dateTime)
        assertEquals("Testprojekt", activity.name)
        assertEquals("Domkloster 4, Köln", activity.addressText)
        assertEquals("https://engagementdatenbank.stadt-koeln.de/testprojekt", activity.sourceUrl)
        assertEquals(50.9413, activity.latitude)
        assertEquals(6.9583, activity.longitude)
    }
}
