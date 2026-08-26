package com.example.VoloMap.server

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.random.Random

private val ENGAGEMENT_CATEGORIES = mapOf(
    476 to "Bildung",
    517 to "Familie & Nachbarschaft",
    302 to "Flüchtlingshilfe",
    310 to "Hausaufgabenbetreuung",
    468 to "Kultur",
    518 to "Leben im Alter",
    516 to "LGBTQ",
    275 to "Obdachlosigkeit",
    251 to "Patenschaften",
    464 to "Soziales",
    467 to "Sport und Bewegung",
    425 to "Tierhilfe",
    303 to "Übersetzen / Dolmetschen",
    475 to "Umwelt, Natur und Tierschutz",
    276 to "Vereinsarbeit",
    382 to "Verkauf",
)

// Harte Obergrenze als Absicherung gegen Endlos-Pagination (z.B. falls die
// "keine Ergebnisse mehr"-Erkennung in einem Randfall versagt). 50 Seiten x
// ~20 Treffer/Seite = 1000 mögliche Ergebnisse pro Kategorie, weit mehr als
// jedes realistische limitPerCategory je bräuchte.
private const val MAX_PAGES_PER_CATEGORY = 50

@Component
class Scraper(
    private val repository: VolunteerActivityRepository,
    private val geocodingService: GeocodingService
) {
    fun getDocument(url: String): Document {
        return Jsoup.connect(url)
            .userAgent("VoloMap-Scraper/1.0 (TH Köln; david_ari_ikerimma.oswalt@smail.th-koeln.de)")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "de-DE,de;q=0.9")
            .header("Referer", "https://engagementdatenbank.stadt-koeln.de")
            .timeout(10000)
            .get()
    }

    fun scrapeWebsite(url: String, pageString: String?, category: String, limit: Int = Int.MAX_VALUE) {
        var count = 0

        fun scrapeWithLimit(document: Document): Int {
            val titleLinks = document.select("div.views-field-title a")
            var foundOnPage = 0
            for (link in titleLinks) {
                if (count >= limit) return foundOnPage
                val name = link.text()
                val href = link.attr("href").removePrefix("/index.php")
                val fullUrl = "https://engagementdatenbank.stadt-koeln.de$href"
                if (href.isEmpty()) continue
                println("Scraping: $fullUrl")
                scrapeEhrenamtDetails(name, fullUrl, category)
                count++
                foundOnPage++
            }
            return foundOnPage
        }

        scrapeWithLimit(getDocument(url))

        if (pageString == null || count >= limit) return

        var page = 1
        while (count < limit && page <= MAX_PAGES_PER_CATEGORY) {
            val newUrl = url.replace("page=0", "page=$page")
            try {
                println("Scraping page $page")
                val found = scrapeWithLimit(getDocument(newUrl))
                if (found == 0) {
                    println("No more results (empty page)")
                    break
                }
                page++
            } catch (e: Exception) {
                println("No more pages")
                break
            }
        }
    }

    fun scrapeAllCategories(limitPerCategory: Int) {
        for ((id, category) in ENGAGEMENT_CATEGORIES) {
            val url = "https://engagementdatenbank.stadt-koeln.de/ergebnisse?fulltext=&id=&area_of_activity=$id&target_group=All&postal_code=&page=0"
            println("Scraping category: $category ($id)")
            try {
                scrapeWebsite(url, "page", category, limitPerCategory)
            } catch (e: Exception) {
                println("Failed to scrape category $category: ${e.message}")
            }
        }
    }

    fun scrapeEhrenamtDetails(name: String, url: String, category: String) {
        // Skip if already in DB
        if (repository.existsBySourceUrl(url)) {
            println("Skipping (already exists): $url")
            return
        }

        val document = getDocument(url)
        Thread.sleep(500) // Höflichkeitspause gegenüber der Stadt-Webseite
        val activity = buildActivityFromDocument(document, url, name, category)

        repository.save(activity)
        println("Saved: ${activity.name} (lat=${activity.latitude}, lng=${activity.longitude})")
    }

    fun buildActivityFromDocument(document: Document, url: String, name: String, category: String): VolunteerActivity {
        val fields = document.select("div.field")
        val data = mutableMapOf<String, String>()

        fields.forEach { field ->
            val label = field.select("div.field__label").text()
            val items = field.select("div.field__item")
            val value = items.joinToString(", ") { item ->
                val link = item.select("a")
                when {
                    link.isEmpty() -> item.text()
                    link.attr("href").startsWith("mailto:") -> item.text()
                    link.attr("href").startsWith("http") -> link.attr("href")
                    else -> item.text()
                }
            }
            if (label.isNotEmpty() && value.isNotEmpty()) {
                data[label] = value
            }
        }

        // Einsatzort (tatsächlicher Ort der Tätigkeit) ist genauer als die
        // Adresse der Vermittlungsstelle (Vereinsbüro) und wird bevorzugt.
        //
        // Einsatzort nur bevorzugen, wenn es wie eine echte Adresse aussieht (enthält eine
        // Ziffer, z. B. Postleitzahl oder Hausnummer) — sonst steht dort teils nur "Deutschland"
        // (ganz ohne Ort), was schlechter geokodiert als die Vermittlungsstelle-Adresse.
        val einsatzort = data["Einsatzort"]
        val vermittlungsstelleAdresse = data["Adresse der Vermittlungsstelle"]
        val address = if (einsatzort != null && einsatzort.any { it.isDigit() }) einsatzort else vermittlungsstelleAdresse
        val coords = address?.let { geocodingService.geocode(it) }
        println("Gefundene Felder: ${data.keys}")

        // dateTime bleibt null: die Kölner Engagementdatenbank führt für
        // diese Angebote keine Termine, ein "jetzt"-Zeitstempel wäre irreführend.
        return VolunteerActivity(
            name = name,
            description = data["Beschreibung"],
            addressText = address,
            sourceUrl = url,
            category = category,
            latitude = coords?.first,
            longitude = coords?.second,
            dateTime = null
        )
    }

    fun fakeScraper(limit: Int) {
        val names = listOf(
            "Nachbarschaftshilfe",
            "Umweltaktion",
            "Seniorenbegleitung",
            "Hausaufgabenhilfe",
            "Kleidertausch",
            "Gemeinschaftsprojekt"
        )

        val descriptions = listOf(
            "Engagement für die lokale Gemeinschaft",
            "Unterstützung für ein soziales Projekt",
            "Mithelfen bei einer gemeinnützigen Aktion",
            "Freiwillige Unterstützung im Stadtteil",
            "Praktische Hilfe für einen guten Zweck"
        )

        val addresses = listOf(
            "Köln Innenstadt",
            "Köln Ehrenfeld",
            "Köln Nippes",
            "Köln Sülz",
            "Köln Lindenthal",
            "Köln Mülheim"
        )

        val categories = listOf("Unbekannt", "Umwelthilfe", "Ehre", "Soziales", "Bildung", "Nachbarschaft")

        // Grob im Umfeld von Köln
        val cologneCenterLat = 50.9375
        val cologneCenterLng = 6.9603
        val maxOffset = 0.08 // ca. einige Kilometer um Köln herum

        while (repository.count() < limit) {
            val activity = VolunteerActivity(
                name = names.random(),
                description = descriptions.random(),
                addressText = addresses.random(),
                category = categories.random(),
                latitude = cologneCenterLat + Random.nextDouble(-maxOffset, maxOffset),
                longitude = cologneCenterLng + Random.nextDouble(-maxOffset, maxOffset),
                dateTime = LocalDateTime.now().plusHours(Random.nextInt(0, 24 * 7).toLong())
            )

            repository.save(activity)
        }
    }
}
