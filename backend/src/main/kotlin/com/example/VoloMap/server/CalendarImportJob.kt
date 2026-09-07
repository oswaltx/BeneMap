package com.example.VoloMap.server

import biweekly.Biweekly
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

// Läuft alle 30 Minuten und synchronisiert für jeden Anbieter mit gesetzter
// externalCalendarUrl dessen externen Kalender in BeneMap-Aktivitäten — Termine
// werden anhand ihrer ICS-UID wiedererkannt (Update statt Duplikat) und gelöscht,
// sobald sie aus dem externen Kalender verschwinden.
@Component
class CalendarImportJob(
    private val userRepository: UserRepository,
    private val activityRepository: VolunteerActivityRepository,
    private val activityRatingRepository: ActivityRatingRepository,
    private val activitySignupRepository: ActivitySignupRepository,
    private val favoriteRepository: FavoriteRepository,
    private val geocodingService: GeocodingService,
) {
    private val logger = LoggerFactory.getLogger(CalendarImportJob::class.java)
    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    @Scheduled(fixedRate = 30 * 60 * 1000)
    fun importAllProviderCalendars() {
        val providers = userRepository.findAll()
            .filter { it.role == Role.ANBIETER && !it.externalCalendarUrl.isNullOrBlank() }
        for (provider in providers) {
            try {
                importFor(provider)
            } catch (e: Exception) {
                logger.warn("Failed to import external calendar for ${provider.email}", e)
            }
        }
    }

    private fun importFor(provider: User) {
        val url = provider.externalCalendarUrl ?: return
        val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            logger.warn("External calendar fetch for ${provider.email} returned ${response.statusCode()}")
            return
        }
        syncFromIcsText(provider, response.body())
    }

    /** Parses `icsText` and upserts/removes the given provider's imported activities to match it. Separated from `importFor` so tests can feed ICS text directly instead of standing up a real HTTP server. */
    fun syncFromIcsText(provider: User, icsText: String) {
        val calendar = Biweekly.parse(icsText).first() ?: return
        val seenUids = mutableSetOf<String>()

        for (event in calendar.events) {
            val uid = event.uid?.value ?: continue
            val dtstart = event.dateStart?.value ?: continue
            seenUids.add(uid)

            val zone = ZoneId.systemDefault()
            val dateTime = LocalDateTime.ofInstant(dtstart.toInstant(), zone)
            val name = event.summary?.value ?: "Unbenannter Termin"
            val addressText = event.location?.value
            val description = event.description?.value
            val durationHours = event.dateEnd?.value?.let { end ->
                ((end.time - dtstart.time) / 60000.0 / 60).takeIf { it > 0 }
            }

            val existing = activityRepository.findByCreatedByAndExternalCalendarUid(provider, uid)
            if (existing != null) {
                val addressChanged = existing.addressText != addressText
                existing.name = name
                existing.description = description
                existing.dateTime = dateTime
                existing.durationHours = durationHours
                if (addressChanged) {
                    existing.addressText = addressText
                    applyCoordinates(existing, addressText)
                }
                activityRepository.save(existing)
            } else {
                val activity = VolunteerActivity(
                    name = name,
                    description = description,
                    addressText = addressText,
                    dateTime = dateTime,
                    durationHours = durationHours,
                    createdBy = provider,
                    externalCalendarUid = uid,
                )
                applyCoordinates(activity, addressText)
                activityRepository.save(activity)
            }
        }

        val staleActivities = activityRepository.findByCreatedByAndExternalCalendarUidIsNotNull(provider)
            .filter { it.externalCalendarUid !in seenUids }
        for (activity in staleActivities) {
            activityRatingRepository.deleteAll(activityRatingRepository.findByActivity(activity))
            activitySignupRepository.deleteAll(activitySignupRepository.findByActivity(activity))
            favoriteRepository.deleteByActivity(activity)
            activityRepository.delete(activity)
        }
    }

    private fun applyCoordinates(activity: VolunteerActivity, addressText: String?) {
        if (addressText.isNullOrBlank()) {
            activity.latitude = null
            activity.longitude = null
            return
        }
        val coords = geocodingService.geocode(addressText)
        activity.latitude = coords?.first
        activity.longitude = coords?.second
    }
}
