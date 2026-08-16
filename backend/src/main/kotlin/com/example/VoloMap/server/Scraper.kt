package com.example.VoloMap.server

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.random.Random

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

    fun scrapeWebsite(url: String, pageString: String?, limit: Int = Int.MAX_VALUE) {
        var count = 0

        fun scrapeWithLimit(document: Document) {
            val links = document.select("a.btn.btn-primary")
            for (link in links) {
                if (count >= limit) return
                val href = link.attr("href").removePrefix("/index.php")
                val fullUrl = "https://engagementdatenbank.stadt-koeln.de$href"
                if (href.isEmpty()) continue
                println("Scraping: $fullUrl")
                scrapeEhrenamtDetails(fullUrl)
                count++
            }
        }

        scrapeWithLimit(getDocument(url))

        if (pageString == null || count >= limit) return

        var page = 2
        while (count < limit) {
            val newUrl = url.replace("page=1", "page=$page")
            try {
                println("Scraping page $page")
                scrapeWithLimit(getDocument(newUrl))
                page++
            } catch (e: Exception) {
                println("No more pages")
                break
            }
        }
    }

    fun scrapeEhrenamtLinks(document: Document) {
        val links = document.select("a.btn.btn-primary")
        links.forEach { link ->
            val href = link.attr("href").removePrefix("/index.php")
            val fullUrl = "https://engagementdatenbank.stadt-koeln.de$href"
            if (href.isEmpty()) return@forEach
            println("Scraping: $fullUrl")
            scrapeEhrenamtDetails(fullUrl)
        }
    }

    fun scrapeEhrenamtDetails(url: String) {
        // Skip if already in DB
        if (repository.existsBySourceUrl(url)) {
            println("Skipping (already exists): $url")
            return
        }

        val document = getDocument(url)
        val activity = buildActivityFromDocument(document, url)

        repository.save(activity)
        println("Saved: ${activity.name} (lat=${activity.latitude}, lng=${activity.longitude})")
    }

    fun buildActivityFromDocument(document: Document, url: String): VolunteerActivity {
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

        val coords = data["Adresse der Vermittlungsstelle"]?.let {
            geocodingService.geocode(it)
        }
        println("Gefundene Felder: ${data.keys}")

        // dateTime bleibt null: die Kölner Engagementdatenbank führt für
        // diese Angebote keine Termine, ein "jetzt"-Zeitstempel wäre irreführend.
        return VolunteerActivity(
            name = data["Projektname"] ?: "Unbekannt",
            description = data["Beschreibung"],
            addressText = data["Adresse der Vermittlungsstelle"],
            sourceUrl = url,
            category = data["Tätigkeitsbereich"] ?: listOf("Unbekannt", "Umwelthilfe", "Ehre")[ Random.nextInt(0, 2)],
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
                    sourceUrl = "https://wawagogo.com/${Random.nextInt(100000, 999999)}",
                    category = categories.random(),
                    latitude = cologneCenterLat + Random.nextDouble(-maxOffset, maxOffset),
                    longitude = cologneCenterLng + Random.nextDouble(-maxOffset, maxOffset),
                    dateTime = LocalDateTime.now().plusHours(Random.nextInt(0, 24*7).toLong())
                )

                repository.save(activity)
            }
        }
}