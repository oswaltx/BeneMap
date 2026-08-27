package com.example.VoloMap.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
    fun `falls back to Adresse der Vermittlungsstelle when Einsatzort is just the bare country name`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        whenever(geocodingService.geocode("Clemensstraße 7, 50676 Köln")).thenReturn(Pair(50.9, 6.95))

        val html = """
            <html><body>
                <div class="field">
                    <div class="field__label">Adresse der Vermittlungsstelle</div>
                    <div class="field__item">Clemensstraße 7, 50676 Köln</div>
                </div>
                <div class="field">
                    <div class="field__label">Einsatzort</div>
                    <div class="field__item">Deutschland</div>
                </div>
            </body></html>
        """.trimIndent()
        val document = Jsoup.parse(html)

        val scraper = Scraper(repository, geocodingService)
        val activity = scraper.buildActivityFromDocument(
            document, "https://engagementdatenbank.stadt-koeln.de/testprojekt", "Testprojekt", "Bildung"
        )

        assertEquals("Clemensstraße 7, 50676 Köln", activity.addressText)
        assertEquals(50.9, activity.latitude)
        assertEquals(6.95, activity.longitude)
        verify(geocodingService, never()).geocode("Deutschland")
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

    @Test
    fun `collapses a duplicated postal code in the address text`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        whenever(geocodingService.geocode(any())).thenReturn(Pair(50.93, 6.98))

        // Reproduces the real Köln site's own markup glitch: the postal-code span and
        // the locality span both contain "50679", so a plain text() concatenation
        // yields "50679 50679 Köln" verbatim.
        val html = """
            <html><body>
                <div class="field">
                    <div class="field__label">Adresse der Vermittlungsstelle</div>
                    <div class="field__item">Gebrüder-Coblenz-Str. 10 50679 50679 Köln Deutschland</div>
                </div>
            </body></html>
        """.trimIndent()
        val document = Jsoup.parse(html)

        val scraper = Scraper(repository, geocodingService)
        val activity = scraper.buildActivityFromDocument(
            document, "https://engagementdatenbank.stadt-koeln.de/testprojekt", "Testprojekt", "Bildung"
        )

        assertEquals("Gebrüder-Coblenz-Str. 10 50679 Köln Deutschland", activity.addressText)
        verify(geocodingService).geocode("Gebrüder-Coblenz-Str. 10 50679 Köln Deutschland")
    }

    @Test
    fun `extracts Vermittlungsstelle contact fields from the detail page`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        whenever(geocodingService.geocode(any())).thenReturn(Pair(50.9, 6.95))

        val html = """
            <html><body>
                <div class="field">
                    <div class="field__label">Adresse der Vermittlungsstelle</div>
                    <div class="field__item">Domkloster 4, Köln</div>
                </div>
                <div class="field">
                    <div class="field__label">Name der Vermittlungsstelle</div>
                    <div class="field__item">Ceno &amp; Die Paten e.V.</div>
                </div>
                <div class="field">
                    <div class="field__label">Homepage der Vermittlungsstelle</div>
                    <div class="field__item"><a href="https://www.ceno-koeln.de/">https://www.ceno-koeln.de/</a></div>
                </div>
                <div class="field">
                    <div class="field__label">E-Mail der Vermittlungsstelle</div>
                    <div class="field__item"><a href="mailto:est@ceno-koeln.de">est@ceno-koeln.de</a></div>
                </div>
                <div class="field">
                    <div class="field__label">Telefonnummer der Vermittlungsstelle</div>
                    <div class="field__item">0221 1234567</div>
                </div>
            </body></html>
        """.trimIndent()
        val document = Jsoup.parse(html)

        val scraper = Scraper(repository, geocodingService)
        val activity = scraper.buildActivityFromDocument(
            document, "https://engagementdatenbank.stadt-koeln.de/testprojekt", "Testprojekt", "Bildung"
        )

        assertEquals("Ceno & Die Paten e.V.", activity.sourceContactName)
        assertEquals("https://www.ceno-koeln.de/", activity.sourceContactWebsite)
        assertEquals("est@ceno-koeln.de", activity.sourceContactEmail)
        assertEquals("0221 1234567", activity.sourceContactPhone)
    }

    @Test
    fun `contact fields are individually null when the detail page omits them`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        whenever(geocodingService.geocode(any())).thenReturn(Pair(50.9, 6.95))

        val html = """
            <html><body>
                <div class="field">
                    <div class="field__label">Adresse der Vermittlungsstelle</div>
                    <div class="field__item">Domkloster 4, Köln</div>
                </div>
                <div class="field">
                    <div class="field__label">Name der Vermittlungsstelle</div>
                    <div class="field__item">Ceno &amp; Die Paten e.V.</div>
                </div>
            </body></html>
        """.trimIndent()
        val document = Jsoup.parse(html)

        val scraper = Scraper(repository, geocodingService)
        val activity = scraper.buildActivityFromDocument(
            document, "https://engagementdatenbank.stadt-koeln.de/testprojekt", "Testprojekt", "Bildung"
        )

        assertEquals("Ceno & Die Paten e.V.", activity.sourceContactName)
        assertNull(activity.sourceContactWebsite)
        assertNull(activity.sourceContactEmail)
        assertNull(activity.sourceContactPhone)
    }

    @Test
    fun `pagination stops once a page returns zero results instead of running away`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        // Detail pages are never actually fetched here: pretend every listing we
        // encounter is already known, so scrapeEhrenamtDetails short-circuits before
        // it would try a real network request against the (hardcoded) live domain.
        whenever(repository.existsBySourceUrl(any())).thenReturn(true)

        val twoEntriesHtml = """
            <html><body>
                <div class="views-row">
                    <div class="views-field-title"><a href="/index.php/angebot-a">Angebot A</a></div>
                </div>
                <div class="views-row">
                    <div class="views-field-title"><a href="/index.php/angebot-b">Angebot B</a></div>
                </div>
            </body></html>
        """.trimIndent()

        val noResultsHtml = """
            <html><body>
                <div class="messages">Es wurden keine Ehrenamtsangebote gefunden.</div>
            </body></html>
        """.trimIndent()

        // Records how many times each "page=N" value was requested, so we can prove
        // the pagination loop stopped right after the first empty page instead of
        // continuing to fetch page=2, page=3, ... forever (the real-world bug fetched
        // ~18,700 pages against the live Köln site before this fix).
        val requestsByPage = ConcurrentHashMap<String, AtomicInteger>()

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ergebnisse") { exchange: HttpExchange ->
            try {
                val query = exchange.requestURI.query ?: ""
                val page = query.substringAfter("page=", "0").substringBefore("&")
                requestsByPage.computeIfAbsent(page) { AtomicInteger(0) }.incrementAndGet()

                val body = if (page == "1") noResultsHtml else twoEntriesHtml
                val bytes = body.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            } finally {
                exchange.close()
            }
        }
        server.start()

        try {
            val port = server.address.port
            val serverBaseUrl = "http://127.0.0.1:$port"
            // page=0 is the true first page on the real site; the fixed pagination
            // loop replaces this "page=0" marker with page=1, page=2, ... in turn.
            val startUrl = "$serverBaseUrl/ergebnisse?fulltext=&page=0"

            // baseUrl is pointed at this same local server (not the real, hardcoded
            // engagementdatenbank.stadt-koeln.de) so this test's isolation from the
            // live site is structural, rather than depending solely on the
            // existsBySourceUrl mock above never being accidentally removed.
            val scraper = Scraper(repository, geocodingService, baseUrl = serverBaseUrl)
            scraper.scrapeWebsite(startUrl, "page", "TestCategory", limit = 100)

            assertEquals(1, requestsByPage["0"]?.get(), "first page should be fetched exactly once")
            assertEquals(
                1, requestsByPage["1"]?.get(),
                "the empty page should be fetched exactly once, to discover there are no more results"
            )
            assertNull(requestsByPage["2"], "pagination must stop after the empty page, not continue to page=2")
            assertNull(requestsByPage["3"], "pagination must not run away past the empty page")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a failing detail page does not prevent other listings on the same page from being saved`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        // No listing is already known, so every one of them reaches a real (local) fetch.
        whenever(repository.existsBySourceUrl(any())).thenReturn(false)

        val listPageHtml = """
            <html><body>
                <div class="views-row">
                    <div class="views-field-title"><a href="/index.php/angebot-a">Angebot A</a></div>
                </div>
                <div class="views-row">
                    <div class="views-field-title"><a href="/index.php/angebot-fail">Angebot Fail</a></div>
                </div>
                <div class="views-row">
                    <div class="views-field-title"><a href="/index.php/angebot-b">Angebot B</a></div>
                </div>
            </body></html>
        """.trimIndent()

        val detailPageHtml = "<html><body></body></html>"

        fun HttpExchange.respondOk(body: String) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            sendResponseHeaders(200, bytes.size.toLong())
            responseBody.write(bytes)
            close()
        }

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ergebnisse") { exchange: HttpExchange -> exchange.respondOk(listPageHtml) }
        server.createContext("/angebot-a") { exchange: HttpExchange -> exchange.respondOk(detailPageHtml) }
        server.createContext("/angebot-b") { exchange: HttpExchange -> exchange.respondOk(detailPageHtml) }
        server.createContext("/angebot-fail") { exchange: HttpExchange ->
            // No response body for a 500: simulates a genuinely broken detail page.
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.start()

        try {
            val port = server.address.port
            val serverBaseUrl = "http://127.0.0.1:$port"
            val startUrl = "$serverBaseUrl/ergebnisse?fulltext=&page=0"

            val scraper = Scraper(repository, geocodingService, baseUrl = serverBaseUrl)
            // pageString = null: this test targets the per-listing isolation on a single
            // page (Finding 1), not the multi-page pagination loop covered separately above.
            scraper.scrapeWebsite(startUrl, pageString = null, category = "TestCategory", limit = 100)

            // Angebot A and Angebot B must both be saved even though Angebot Fail's
            // detail-page fetch failed (500) in between them.
            verify(repository, times(2)).save(any<VolunteerActivity>())
        } finally {
            server.stop(0)
        }
    }
}
