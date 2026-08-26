# Scraper-Überarbeitung Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Scraper für die Kölner Engagementdatenbank importiert echte Namen (statt "Unbekannt"), echte Kategorien (statt Zufall) und geokodiert den tatsächlichen Einsatzort (statt der Vermittlungsstelle-Adresse) — und lässt sich über ein Kommandozeilen-Flag gezielt wiederholt anstoßen.

**Architecture:** Der Name wird direkt aus der Ergebnisliste übernommen (`div.views-field-title a` liefert Titel + Detail-URL in einem Element). Die Kategorie wird nicht mehr aus der Detailseite geparst (existiert dort nicht), sondern aus dem Suchparameter `area_of_activity` abgeleitet, indem pro echter Kategorie-ID einzeln gescraped wird. `Einsatzort` wird gegenüber `Adresse der Vermittlungsstelle` bevorzugt geokodiert. Ein neues `--scrape`-Kommandozeilen-Flag in `VoloMapApp.kt` löst einen echten Lauf aus, ohne Quellcode ändern zu müssen.

**Tech Stack:** Kotlin/Spring Boot (Backend), Jsoup für HTML-Parsing, JUnit5 + Mockito-Kotlin für Tests (bestehende Konventionen, kein neues Tooling).

## Global Constraints

- Bereits importierte Angebote (`existsBySourceUrl`) werden weiterhin einfach übersprungen — kein Update bei Änderungen.
- Kein neuer öffentlicher HTTP-Endpunkt, keine neue Rolle — der Scraper bleibt über ein Kommandozeilen-Flag (`--scrape`) beim Programmstart ausgelöst.
- Ein Fehler beim Scrapen einer einzelnen Kategorie darf die übrigen Kategorien nicht verhindern.
- `fakeScraper()` (Mock-Daten für die lokale Entwicklung) bleibt unverändert.
- `dateTime` bleibt weiterhin `null` für gescrapte Aktivitäten (bereits bestehendes, unverändertes Verhalten aus der vorherigen Überarbeitung — nicht Teil dieses Plans, nur zur Einordnung).
- Kurze Pause (`Thread.sleep(500)`) zwischen Detailseiten-Abrufen, zusätzlich zur bestehenden Geokodierungs-Pause in `GeocodingService`.

---

### Task 1: `Scraper.kt` — korrekter Name, echte Kategorie, Einsatzort-Präferenz, `--scrape`-Flag

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/Scraper.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/VoloMapApp.kt`
- Modify: `backend/src/test/kotlin/com/example/VoloMap/server/ScraperTest.kt`

**Interfaces:**
- Produces: `Scraper.buildActivityFromDocument(document: Document, url: String, name: String, category: String): VolunteerActivity` (Signaturänderung — `name`/`category` sind jetzt Parameter statt geparste Felder). `Scraper.scrapeEhrenamtDetails(name: String, url: String, category: String)` (Signaturänderung, analog). `Scraper.scrapeWebsite(url: String, pageString: String?, category: String, limit: Int = Int.MAX_VALUE)` (neuer `category`-Parameter). `Scraper.scrapeAllCategories(limitPerCategory: Int)` (neue Methode, iteriert über alle 17 echten Kategorien). Die bisherige, nirgends aufgerufene Methode `scrapeEhrenamtLinks(document: Document)` entfällt ersatzlos (toter Code, dessen einziger Aufruf der jetzt geänderten `scrapeEhrenamtDetails`-Signatur widerspräche).
- Consumes: nichts aus anderen Tasks dieses Plans (letzter/einziger Implementierungs-Task).

- [ ] **Step 1: Fehlschlagende Tests für die neue Signatur schreiben**

`backend/src/test/kotlin/com/example/VoloMap/server/ScraperTest.kt` komplett ersetzen durch:

```kotlin
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
```

- [ ] **Step 2: Tests ausführen, Fehlschlag bestätigen**

Run: `cd backend && .\gradlew.bat test --tests "com.example.VoloMap.server.ScraperTest"`
Expected: FAIL — `buildActivityFromDocument` hat noch die alte 2-Parameter-Signatur (Kompilierfehler).

- [ ] **Step 3: `Scraper.kt` komplett ersetzen**

Die komplette Datei `backend/src/main/kotlin/com/example/VoloMap/server/Scraper.kt` ersetzen durch:

```kotlin
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

        fun scrapeWithLimit(document: Document) {
            val titleLinks = document.select("div.views-field-title a")
            for (link in titleLinks) {
                if (count >= limit) return
                val name = link.text()
                val href = link.attr("href").removePrefix("/index.php")
                val fullUrl = "https://engagementdatenbank.stadt-koeln.de$href"
                if (href.isEmpty()) continue
                println("Scraping: $fullUrl")
                scrapeEhrenamtDetails(name, fullUrl, category)
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

    fun scrapeAllCategories(limitPerCategory: Int) {
        for ((id, category) in ENGAGEMENT_CATEGORIES) {
            val url = "https://engagementdatenbank.stadt-koeln.de/ergebnisse?fulltext=&id=&area_of_activity=$id&target_group=All&postal_code=&page=1"
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
        val address = data["Einsatzort"] ?: data["Adresse der Vermittlungsstelle"]
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
```

- [ ] **Step 4: Tests erneut ausführen, Erfolg bestätigen**

Run: `cd backend && .\gradlew.bat test --tests "com.example.VoloMap.server.ScraperTest"`
Expected: PASS (3/3).

- [ ] **Step 5: `VoloMapApp.kt` — `--scrape`-Flag ergänzen**

Die bestehende Datei `backend/src/main/kotlin/com/example/VoloMap/VoloMapApp.kt` komplett ersetzen durch:

```kotlin
package com.example.VoloMap

import com.example.VoloMap.server.Scraper
import com.example.VoloMap.server.VolunteerActivityRepository
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VoloMapApp

fun main(args: Array<String>) {
    val context = runApplication<VoloMapApp>(*args)

    val repository = context.getBean(VolunteerActivityRepository::class.java)
    if (repository.count() == 0L) {
        val scraper = context.getBean(Scraper::class.java)
        scraper.fakeScraper(30)
    }

    if (args.contains("--scrape")) {
        val scraper = context.getBean(Scraper::class.java)
        scraper.scrapeAllCategories(20)
    }
}
```

- [ ] **Step 6: Vollen Backend-Testlauf ausführen**

Run: `cd backend && .\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`, keine fehlgeschlagenen Tests (die bereits bestehenden Tests für `MainController`/`AuthController`/etc. sind von dieser Änderung nicht betroffen, da nur `Scraper.kt`/`VoloMapApp.kt` geändert wurden).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/Scraper.kt backend/src/main/kotlin/com/example/VoloMap/VoloMapApp.kt backend/src/test/kotlin/com/example/VoloMap/server/ScraperTest.kt
git commit -m "feat: rewrite scraper to extract real name/category and prefer Einsatzort"
```

---

### Task 2: Echter Scraper-Lauf gegen die Kölner Engagementdatenbank — Verifikation

**Files:** keine Code-Änderungen — reine Verifikation gegen die echte Webseite.

**Interfaces:**
- Consumes: das vollständige Feature aus Task 1.
- Produces: nichts (Verifikationsergebnis wird im Ledger festgehalten).

- [ ] **Step 1: Lokale Dev-Datenbank zurücksetzen (optional, für einen sauberen Vergleich)**

Falls die lokale `data/`-Datenbank bereits Altlasten aus vorherigen Testläufen enthält (z. B. Einträge mit `sourceUrl` von `wawagogo.com` oder falsch benannte "Unbekannt"-Einträge vom alten Scraper), zum Vergleich optional zurücksetzen: `rm -rf backend/data` — reine lokale Testdaten, nicht versioniert.

- [ ] **Step 2: Backend mit `--scrape`-Flag starten**

Run: `cd backend && .\gradlew.bat bootRun --args='--scrape'`

Konsolenausgabe beobachten: für jede der 16 Kategorien sollte "Scraping category: ..." erscheinen, gefolgt von "Scraping: https://engagementdatenbank.stadt-koeln.de/..." und "Saved: <echter Name> (lat=..., lng=...)" pro importiertem Angebot — kein "Unbekannt" mehr als Name.

- [ ] **Step 3: Stichprobe über `/markers` prüfen**

Nach dem Lauf: `fetch('http://localhost:8080/markers').then(r => r.json())` (Browser-Konsole oder `javascript_tool`) und für mehrere Einträge mit `sourceUrl` prüfen:
- `name` ist ein echter, sinnvoller Titel (kein "Unbekannt").
- `category` entspricht einer der 16 echten Kategorien (kein Zufallswert aus der alten `listOf("Unbekannt", "Umwelthilfe", "Ehre")`-Liste).
- Mehrere Einträge unterschiedlicher Vermittlungsstellen haben unterschiedliche, plausible Koordinaten (nicht mehr alle identisch auf einer Bürofassade wie beim vorherigen Lauf mit 5 Einträgen an "Clemensstraße 7").

- [ ] **Step 4: Wiederholbarkeit prüfen**

Backend stoppen, `.\gradlew.bat bootRun --args='--scrape'` ein zweites Mal ausführen. Konsolenausgabe sollte für bereits importierte Angebote "Skipping (already exists): ..." zeigen, keine Duplikate in `/markers`.

- [ ] **Step 5: Ledger-Eintrag**

Ergebnis der Verifikation festhalten (Anzahl importierter Aktivitäten, Stichproben-Beispiele für Name/Kategorie/Standort, ob Wiederholbarkeit funktioniert).
