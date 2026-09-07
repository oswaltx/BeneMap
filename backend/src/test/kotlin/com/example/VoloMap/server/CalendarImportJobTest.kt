package com.example.VoloMap.server

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.net.InetAddress
import java.net.InetSocketAddress

@SpringBootTest
class CalendarImportJobTest {

    @Autowired
    lateinit var calendarImportJob: CalendarImportJob

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var emailVerificationTokenRepository: EmailVerificationTokenRepository

    @BeforeEach
    fun cleanUp() {
        activityRepository.deleteAll()
        emailVerificationTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        activityRepository.deleteAll()
        emailVerificationTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun newProvider(email: String) = userRepository.save(
        User(email = email, passwordHash = "x", name = "Anbieter", role = Role.ANBIETER)
    )

    private fun ics(vararg events: String): String =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Test//Test//EN\r\n" +
            events.joinToString("") +
            "END:VCALENDAR\r\n"

    private fun event(uid: String, summary: String, start: String, end: String? = null, location: String? = null): String {
        val lines = mutableListOf("BEGIN:VEVENT", "UID:$uid", "DTSTART:$start")
        end?.let { lines.add("DTEND:$it") }
        lines.add("SUMMARY:$summary")
        location?.let { lines.add("LOCATION:$it") }
        lines.add("END:VEVENT")
        return lines.joinToString("\r\n") + "\r\n"
    }

    @Test
    fun `imports a new event as an activity`() {
        val provider = newProvider("provider1@example.com")
        val icsText = ics(event("uid-1@example.com", "Externer Termin", "20270601T090000Z", "20270601T110000Z"))

        calendarImportJob.syncFromIcsText(provider, icsText)

        val activities = activityRepository.findByCreatedBy(provider)
        assertEquals(1, activities.size)
        assertEquals("Externer Termin", activities[0].name)
        assertEquals("uid-1@example.com", activities[0].externalCalendarUid)
        assertEquals(2.0, activities[0].durationHours)
    }

    @Test
    fun `re-syncing the same uid updates instead of duplicating`() {
        val provider = newProvider("provider2@example.com")
        calendarImportJob.syncFromIcsText(
            provider,
            ics(event("uid-2@example.com", "Alter Titel", "20270601T090000Z"))
        )
        calendarImportJob.syncFromIcsText(
            provider,
            ics(event("uid-2@example.com", "Neuer Titel", "20270601T090000Z"))
        )

        val activities = activityRepository.findByCreatedBy(provider)
        assertEquals(1, activities.size)
        assertEquals("Neuer Titel", activities[0].name)
    }

    @Test
    fun `event removed from the feed is deleted locally`() {
        val provider = newProvider("provider3@example.com")
        calendarImportJob.syncFromIcsText(
            provider,
            ics(event("uid-3@example.com", "Wird gelöscht", "20270601T090000Z"))
        )
        assertEquals(1, activityRepository.findByCreatedBy(provider).size)

        calendarImportJob.syncFromIcsText(provider, ics())

        assertTrue(activityRepository.findByCreatedBy(provider).isEmpty())
    }

    @Test
    fun `manually created activities are never touched by the import`() {
        val provider = newProvider("provider4@example.com")
        activityRepository.save(VolunteerActivity(name = "Manuell angelegt", createdBy = provider))

        calendarImportJob.syncFromIcsText(provider, ics())

        val activities = activityRepository.findByCreatedBy(provider)
        assertEquals(1, activities.size)
        assertEquals("Manuell angelegt", activities[0].name)
        assertNull(activities[0].externalCalendarUid)
    }

    @Test
    fun `rejects loopback, link-local and private-network calendar URLs`() {
        assertFalse(calendarImportJob.isSafeExternalUrl("http://127.0.0.1/cal.ics"))
        assertFalse(calendarImportJob.isSafeExternalUrl("http://localhost/cal.ics"))
        assertFalse(calendarImportJob.isSafeExternalUrl("http://169.254.169.254/latest/meta-data/"))
        assertFalse(calendarImportJob.isSafeExternalUrl("http://10.0.0.5/cal.ics"))
        assertFalse(calendarImportJob.isSafeExternalUrl("http://192.168.1.1/cal.ics"))
        assertFalse(calendarImportJob.isSafeExternalUrl("ftp://example.com/cal.ics"))
        assertFalse(calendarImportJob.isSafeExternalUrl("not a url"))
    }

    @Test
    fun `accepts an ordinary public https calendar URL`() {
        // An IP literal avoids depending on real DNS resolution being available in CI/sandboxes.
        assertTrue(calendarImportJob.isSafeExternalUrl("https://8.8.8.8/basic.ics"))
    }

    @Test
    fun `importAllProviderCalendars never fetches a provider-supplied loopback URL`() {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        var wasHit = false
        server.createContext("/cal.ics") { exchange ->
            wasHit = true
            val body = ics(event("uid-ssrf@example.com", "Sollte nie ankommen", "20270601T090000Z")).toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val provider = newProvider("ssrf-provider@example.com")
            provider.externalCalendarUrl = "http://127.0.0.1:${server.address.port}/cal.ics"
            userRepository.save(provider)

            calendarImportJob.importAllProviderCalendars()

            assertFalse(wasHit, "the SSRF guard should have refused the request before it was ever sent")
            assertTrue(activityRepository.findByCreatedBy(provider).isEmpty())
        } finally {
            server.stop(0)
        }
    }
}
