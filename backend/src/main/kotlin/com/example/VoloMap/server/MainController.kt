package com.example.VoloMap.server

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
class MainController(
    private val repository: VolunteerActivityRepository,
    private val geocodingService: GeocodingService,
    private val userRepository: UserRepository,
    private val activityRatingRepository: ActivityRatingRepository,
    private val providerRatingRepository: ProviderRatingRepository,
) {

    @GetMapping("/")
    fun index() = "Hello World!"


    @GetMapping("/markers")
    fun markers(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) date: String?, // format: YYYY-MM-DD
        @RequestParam(required = false) timeFrom: Int?,
        @RequestParam(required = false) timeTo: Int?,
        @RequestParam(required = false) search: String?,
    ): List<Marker> {
        val filterDate = date?.let { LocalDate.parse(it) }
        val searchText = search?.trim()?.lowercase()

        val activityRatingsByActivityId = activityRatingRepository.findAll().groupBy { it.activity.id }
        val providerRatingsByProviderId = providerRatingRepository.findAll().groupBy { it.provider.id }

        return repository.findAll()
            .filter { category == null || it.category == category }
            .filter { it.latitude != null && it.longitude != null }
            .map { activity ->
                val activityRatings = activityRatingsByActivityId[activity.id].orEmpty()
                val providerId = activity.createdBy?.id
                val providerRatings = providerId?.let { providerRatingsByProviderId[it] }.orEmpty()
                Marker(
                    id = activity.id,
                    lat = activity.latitude!!,
                    lng = activity.longitude!!,
                    name = activity.name,
                    address = activity.addressText ?: "",
                    category = activity.category ?: "",
                    description = activity.description ?: "",
                    photoUrls = parsePhotoUrls(activity.photoUrls),
                    dateTime = activity.dateTime,
                    activityRating = activityRatings.map { it.stars }.average().takeIf { activityRatings.isNotEmpty() },
                    activityRatingCount = activityRatings.size,
                    providerId = providerId,
                    providerName = activity.createdBy?.name,
                    providerPhotoUrl = activity.createdBy?.photoUrl,
                    providerWebsiteUrl = activity.createdBy?.websiteUrl,
                    providerRating = providerRatings.map { it.stars }.average().takeIf { providerRatings.isNotEmpty() },
                    providerRatingCount = providerRatings.size,
                    sourceUrl = activity.sourceUrl,
                    sourceContactName = activity.sourceContactName,
                    sourceContactWebsite = activity.sourceContactWebsite,
                    sourceContactEmail = activity.sourceContactEmail,
                    sourceContactPhone = activity.sourceContactPhone,
                )
            }
            .filter { filterDate == null || it.dateTime?.toLocalDate() == filterDate }
            .filter { timeFrom == null || (it.dateTime?.hour ?: 0) >= timeFrom }
            .filter { timeTo == null || (it.dateTime?.hour ?: 0) < timeTo }
            .filter {
                searchText == null ||
                        it.name.lowercase().contains(searchText) ||
                        it.address.lowercase().contains(searchText) ||
                        it.description.lowercase().contains(searchText)
            }
    }
    @GetMapping("/categories")
    fun categories(): List<String> {
        return repository.findAll()
            .mapNotNull { it.category }
            .distinct()
            .sorted()
    }
    @PostMapping("/add")
    fun addActivity(
        @RequestBody activity: VolunteerActivity,
        authentication: Authentication
    ): ResponseEntity<VolunteerActivity> {
        activity.id = 0
        activity.sourceUrl = null
        activity.sourceContactName = null
        activity.sourceContactWebsite = null
        activity.sourceContactEmail = null
        activity.sourceContactPhone = null
        activity.createdBy = userRepository.findByEmail(authentication.name)
        activity.photoUrls = normalizePhotoUrls(activity.photoUrls)

        if (activity.latitude == null && activity.longitude == null && !activity.addressText.isNullOrBlank()) {
            val coords = geocodingService.geocode(activity.addressText!!)
            if (coords != null) {
                activity.latitude = coords.first
                activity.longitude = coords.second
            }
        }
        val savedActivity = repository.save(activity)
        return ResponseEntity.ok(savedActivity)
    }

    @PostMapping("/add-recurring")
    fun addRecurringActivity(
        @RequestBody req: AddRecurringActivityRequest,
        authentication: Authentication
    ): ResponseEntity<*> {
        if (req.recurrenceIntervalDays < 1) {
            return ResponseEntity.badRequest().body(ErrorResponse("recurrenceIntervalDays muss mindestens 1 sein."))
        }

        val provider = userRepository.findByEmail(authentication.name)
        val normalizedPhotoUrls = normalizePhotoUrls(req.photoUrls)

        var latitude: Double? = null
        var longitude: Double? = null
        if (!req.addressText.isNullOrBlank()) {
            val coords = geocodingService.geocode(req.addressText)
            if (coords != null) {
                latitude = coords.first
                longitude = coords.second
            }
        }

        val horizonEnd = req.dateTime.plusMonths(RECURRENCE_HORIZON_MONTHS)
        val occurrenceDates = generateSequence(req.dateTime) { it.plusDays(req.recurrenceIntervalDays.toLong()) }
            .takeWhile { it.isBefore(horizonEnd) }
            .take(MAX_RECURRING_OCCURRENCES)
            .toList()

        val createdActivities = occurrenceDates.map { occurrenceDateTime ->
            repository.save(
                VolunteerActivity(
                    name = req.name,
                    description = req.description,
                    addressText = req.addressText,
                    category = req.category,
                    photoUrls = normalizedPhotoUrls,
                    latitude = latitude,
                    longitude = longitude,
                    dateTime = occurrenceDateTime,
                    createdBy = provider,
                )
            )
        }

        return ResponseEntity.ok(createdActivities)
    }

    @PutMapping("/activities/{id}")
    fun updateActivity(
        @PathVariable id: Long,
        @RequestBody req: UpdateActivityRequest,
        authentication: Authentication
    ): ResponseEntity<*> {
        val activity = repository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
        val user = userRepository.findByEmail(authentication.name)
        if (activity.createdBy?.id != user?.id) {
            return ResponseEntity.status(403).build<Any>()
        }

        activity.name = req.name
        activity.description = req.description
        activity.category = req.category
        activity.photoUrls = normalizePhotoUrls(req.photoUrls)
        if (req.dateTime != null) {
            activity.dateTime = req.dateTime
        }

        var geocodingFailed = false
        val addressChanged = req.addressText != activity.addressText
        if (addressChanged) {
            activity.addressText = req.addressText
            if (!req.addressText.isNullOrBlank()) {
                val coords = geocodingService.geocode(req.addressText)
                if (coords != null) {
                    activity.latitude = coords.first
                    activity.longitude = coords.second
                } else {
                    // Keep the previous coordinates (no pin loss); the frontend needs an
                    // explicit signal here since latitude/longitude alone can't distinguish
                    // "unchanged because nothing changed" from "unchanged because geocoding failed".
                    geocodingFailed = true
                }
            } else {
                activity.latitude = null
                activity.longitude = null
            }
        }

        val saved = repository.save(activity)
        return ResponseEntity.ok(UpdateActivityResponse(saved, geocodingFailed))
    }

    @Transactional
    @DeleteMapping("/activities/{id}")
    fun deleteActivity(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val activity = repository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val user = userRepository.findByEmail(authentication.name)
        if (activity.createdBy?.id != user?.id) {
            return ResponseEntity.status(403).build()
        }
        activityRatingRepository.deleteAll(activityRatingRepository.findByActivity(activity))
        repository.delete(activity)
        return ResponseEntity.noContent().build()
    }

}

data class UpdateActivityResponse(
    val activity: VolunteerActivity,
    val geocodingFailed: Boolean,
)

/**
 * PUT /activities/{id} request body. `description`/`addressText`/`category`/`photoUrls` use
 * full-replace semantics (omitted means "clear this field"); `dateTime` uses merge
 * semantics (omitted means "leave the existing value unchanged"). The frontend
 * always sends every field, so this asymmetry has no live effect today — but a
 * future API consumer sending a partial body needs to know about it.
 */
data class UpdateActivityRequest(
    val name: String,
    val description: String? = null,
    val addressText: String? = null,
    val category: String? = null,
    val dateTime: LocalDateTime? = null,
    val photoUrls: String? = null,
)

private const val MAX_PHOTO_URLS = 10
private const val MAX_RECURRING_OCCURRENCES = 60
private const val RECURRENCE_HORIZON_MONTHS = 3L

data class AddRecurringActivityRequest(
    val name: String,
    val description: String? = null,
    val addressText: String? = null,
    val category: String? = null,
    val dateTime: LocalDateTime,
    val photoUrls: String? = null,
    val recurrenceIntervalDays: Int,
)

private fun parsePhotoUrls(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_PHOTO_URLS)
}

private fun normalizePhotoUrls(raw: String?): String? {
    val parsed = parsePhotoUrls(raw)
    return if (parsed.isEmpty()) null else parsed.joinToString("\n")
}
