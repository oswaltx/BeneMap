package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ActivityCategoriesTest {

    @Test
    fun `activity categories match the sixteen scraper categories plus Sonstiges in order`() {
        val expected = listOf(
            "Bildung",
            "Familie & Nachbarschaft",
            "Flüchtlingshilfe",
            "Hausaufgabenbetreuung",
            "Kultur",
            "Leben im Alter",
            "LGBTQ",
            "Obdachlosigkeit",
            "Patenschaften",
            "Soziales",
            "Sport und Bewegung",
            "Tierhilfe",
            "Übersetzen / Dolmetschen",
            "Umwelt, Natur und Tierschutz",
            "Vereinsarbeit",
            "Verkauf",
            "Sonstiges",
        )
        assertEquals(expected, ACTIVITY_CATEGORIES)
    }
}
