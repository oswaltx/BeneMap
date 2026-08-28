package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

        val controller = MainController(repository, mock(), mock(), mock(), mock(), mock())
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

        val controller = MainController(repository, mock(), mock(), mock(), mock(), mock())
        val result = controller.markers(
            category = null, date = null, timeFrom = null, timeTo = null, search = "tierheim"
        )

        assertEquals(1, result.size)
        assertEquals("Projekt A", result[0].name)
    }

    @Test
    fun `includes rating averages and provider identity for an activity`() {
        val repository = mock<VolunteerActivityRepository>()
        val activityRatingRepository = mock<ActivityRatingRepository>()
        val providerRatingRepository = mock<ProviderRatingRepository>()
        val provider = User(id = 7, email = "anbieter@example.com", passwordHash = "x", name = "Anbieter Anna", role = Role.ANBIETER)
        val rated = activity(
            name = "Bewertete Aktion",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        ).also { it.createdBy = provider }
        whenever(repository.findAll()).thenReturn(listOf(rated))
        whenever(activityRatingRepository.findAll()).thenReturn(
            listOf(
                ActivityRating(user = mock(), activity = rated, stars = 4),
                ActivityRating(user = mock(), activity = rated, stars = 2),
            )
        )
        whenever(providerRatingRepository.findAll()).thenReturn(
            listOf(ProviderRating(user = mock(), provider = provider, stars = 5))
        )

        val controller = MainController(repository, mock(), mock(), activityRatingRepository, providerRatingRepository, mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals(3.0, result[0].activityRating)
        assertEquals(2, result[0].activityRatingCount)
        assertEquals(7L, result[0].providerId)
        assertEquals("Anbieter Anna", result[0].providerName)
        assertEquals(5.0, result[0].providerRating)
        assertEquals(1, result[0].providerRatingCount)
    }

    @Test
    fun `rating fields are null and zero when an activity has no ratings or owner`() {
        val repository = mock<VolunteerActivityRepository>()
        val activityRatingRepository = mock<ActivityRatingRepository>()
        val providerRatingRepository = mock<ProviderRatingRepository>()
        val unrated = activity(
            name = "Unbewertete Aktion",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        whenever(repository.findAll()).thenReturn(listOf(unrated))
        whenever(activityRatingRepository.findAll()).thenReturn(emptyList())
        whenever(providerRatingRepository.findAll()).thenReturn(emptyList())

        val controller = MainController(repository, mock(), mock(), activityRatingRepository, providerRatingRepository, mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertNull(result[0].activityRating)
        assertEquals(0, result[0].activityRatingCount)
        assertNull(result[0].providerId)
        assertNull(result[0].providerName)
        assertNull(result[0].providerRating)
        assertEquals(0, result[0].providerRatingCount)
    }

    @Test
    fun `parses stored photo URLs into a list, trimming and dropping blank lines`() {
        val repository = mock<VolunteerActivityRepository>()
        val withPhotos = activity(
            name = "Mit Fotos",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        ).also { it.photoUrls = "https://example.com/a.jpg\n  \nhttps://example.com/b.jpg  \n" }
        whenever(repository.findAll()).thenReturn(listOf(withPhotos))

        val controller = MainController(repository, mock(), mock(), mock(), mock(), mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals(listOf("https://example.com/a.jpg", "https://example.com/b.jpg"), result[0].photoUrls)
    }

    @Test
    fun `photo URLs list is empty when the activity has none`() {
        val repository = mock<VolunteerActivityRepository>()
        val noPhotos = activity(
            name = "Ohne Fotos",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        whenever(repository.findAll()).thenReturn(listOf(noPhotos))

        val controller = MainController(repository, mock(), mock(), mock(), mock(), mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals(emptyList<String>(), result[0].photoUrls)
    }

    @Test
    fun `includes provider photo and website when set`() {
        val repository = mock<VolunteerActivityRepository>()
        val activityRatingRepository = mock<ActivityRatingRepository>()
        val providerRatingRepository = mock<ProviderRatingRepository>()
        val provider = User(
            id = 7, email = "anbieter@example.com", passwordHash = "x", name = "Anbieter Anna",
            role = Role.ANBIETER, photoUrl = "https://example.com/anna.jpg", websiteUrl = "https://anna-verein.de"
        )
        val rated = activity(
            name = "Bewertete Aktion",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        ).also { it.createdBy = provider }
        whenever(repository.findAll()).thenReturn(listOf(rated))
        whenever(activityRatingRepository.findAll()).thenReturn(emptyList())
        whenever(providerRatingRepository.findAll()).thenReturn(emptyList())

        val controller = MainController(repository, mock(), mock(), activityRatingRepository, providerRatingRepository, mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals("https://example.com/anna.jpg", result[0].providerPhotoUrl)
        assertEquals("https://anna-verein.de", result[0].providerWebsiteUrl)
    }

    @Test
    fun `provider photo and website are null without an owner`() {
        val repository = mock<VolunteerActivityRepository>()
        val activityRatingRepository = mock<ActivityRatingRepository>()
        val providerRatingRepository = mock<ProviderRatingRepository>()
        val unrated = activity(
            name = "Unbewertete Aktion",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        whenever(repository.findAll()).thenReturn(listOf(unrated))
        whenever(activityRatingRepository.findAll()).thenReturn(emptyList())
        whenever(providerRatingRepository.findAll()).thenReturn(emptyList())

        val controller = MainController(repository, mock(), mock(), activityRatingRepository, providerRatingRepository, mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertNull(result[0].providerPhotoUrl)
        assertNull(result[0].providerWebsiteUrl)
    }

    @Test
    fun `includes signupCount and maxParticipants for an activity`() {
        val repository = mock<VolunteerActivityRepository>()
        val activitySignupRepository = mock<ActivitySignupRepository>()
        val provider = User(id = 7, email = "anbieter@example.com", passwordHash = "x", name = "Anbieter Anna", role = Role.ANBIETER)
        val withLimit = activity(
            name = "Begrenzte Aktion",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        ).also { it.createdBy = provider; it.maxParticipants = 5 }
        whenever(repository.findAll()).thenReturn(listOf(withLimit))
        whenever(activitySignupRepository.findAll()).thenReturn(
            listOf(
                ActivitySignup(user = mock(), activity = withLimit),
                ActivitySignup(user = mock(), activity = withLimit),
            )
        )

        val controller = MainController(repository, mock(), mock(), mock(), mock(), activitySignupRepository)
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals(2, result[0].signupCount)
        assertEquals(5, result[0].maxParticipants)
    }

    @Test
    fun `signupCount is zero and maxParticipants is null without any signups or limit`() {
        val repository = mock<VolunteerActivityRepository>()
        val activitySignupRepository = mock<ActivitySignupRepository>()
        val unlimited = activity(
            name = "Offene Aktion",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        whenever(repository.findAll()).thenReturn(listOf(unlimited))
        whenever(activitySignupRepository.findAll()).thenReturn(emptyList())

        val controller = MainController(repository, mock(), mock(), mock(), mock(), activitySignupRepository)
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals(0, result[0].signupCount)
        assertNull(result[0].maxParticipants)
    }

    @Test
    fun `includes sourceUrl when the activity was scraped`() {
        val repository = mock<VolunteerActivityRepository>()
        val scraped = activity(
            name = "Gescraptes Angebot",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        ).also { it.sourceUrl = "https://engagementdatenbank.stadt-koeln.de/testprojekt" }
        whenever(repository.findAll()).thenReturn(listOf(scraped))

        val controller = MainController(repository, mock(), mock(), mock(), mock(), mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals("https://engagementdatenbank.stadt-koeln.de/testprojekt", result[0].sourceUrl)
    }

    @Test
    fun `sourceUrl is null for an activity added through the normal form`() {
        val repository = mock<VolunteerActivityRepository>()
        val ownActivity = activity(
            name = "Eigene Aktivität",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        whenever(repository.findAll()).thenReturn(listOf(ownActivity))

        val controller = MainController(repository, mock(), mock(), mock(), mock(), mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertNull(result[0].sourceUrl)
    }

    @Test
    fun `includes Vermittlungsstelle contact fields when the activity was scraped`() {
        val repository = mock<VolunteerActivityRepository>()
        val scraped = activity(
            name = "Gescraptes Angebot",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        ).also {
            it.sourceContactName = "Ceno & Die Paten e.V."
            it.sourceContactWebsite = "https://www.ceno-koeln.de/"
            it.sourceContactEmail = "est@ceno-koeln.de"
            it.sourceContactPhone = "0221 1234567"
        }
        whenever(repository.findAll()).thenReturn(listOf(scraped))

        val controller = MainController(repository, mock(), mock(), mock(), mock(), mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals("Ceno & Die Paten e.V.", result[0].sourceContactName)
        assertEquals("https://www.ceno-koeln.de/", result[0].sourceContactWebsite)
        assertEquals("est@ceno-koeln.de", result[0].sourceContactEmail)
        assertEquals("0221 1234567", result[0].sourceContactPhone)
    }

    @Test
    fun `Vermittlungsstelle contact fields are null for an activity added through the normal form`() {
        val repository = mock<VolunteerActivityRepository>()
        val ownActivity = activity(
            name = "Eigene Aktivität",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        whenever(repository.findAll()).thenReturn(listOf(ownActivity))

        val controller = MainController(repository, mock(), mock(), mock(), mock(), mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertNull(result[0].sourceContactName)
        assertNull(result[0].sourceContactWebsite)
        assertNull(result[0].sourceContactEmail)
        assertNull(result[0].sourceContactPhone)
    }

    @Test
    fun `dateTime is null when an activity has no scheduled appointment`() {
        val repository = mock<VolunteerActivityRepository>()
        val undated = VolunteerActivity(
            name = "Ohne Termin",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            latitude = 50.0,
            longitude = 6.0,
            dateTime = null
        )
        whenever(repository.findAll()).thenReturn(listOf(undated))

        val controller = MainController(repository, mock(), mock(), mock(), mock(), mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertNull(result[0].dateTime)
    }

    @Test
    fun `undated activity is excluded when a specific date filter is set`() {
        val repository = mock<VolunteerActivityRepository>()
        val undated = VolunteerActivity(
            name = "Ohne Termin",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            latitude = 50.0,
            longitude = 6.0,
            dateTime = null
        )
        whenever(repository.findAll()).thenReturn(listOf(undated))

        val controller = MainController(repository, mock(), mock(), mock(), mock(), mock())
        val result = controller.markers(
            category = null, date = "2026-08-10", timeFrom = null, timeTo = null, search = null
        )

        assertEquals(0, result.size)
    }

    @Test
    fun `undated activity is excluded when a time range filter is set`() {
        val repository = mock<VolunteerActivityRepository>()
        val undated = VolunteerActivity(
            name = "Ohne Termin",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            latitude = 50.0,
            longitude = 6.0,
            dateTime = null
        )
        whenever(repository.findAll()).thenReturn(listOf(undated))

        val controller = MainController(repository, mock(), mock(), mock(), mock(), mock())
        val result = controller.markers(
            category = null, date = null, timeFrom = 8, timeTo = 12, search = null
        )

        assertEquals(0, result.size)
    }
}
