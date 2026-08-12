package com.example.VoloMap.server

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
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
                    dateTime = activity.dateTime,
                    activityRating = activityRatings.map { it.stars }.average().takeIf { activityRatings.isNotEmpty() },
                    activityRatingCount = activityRatings.size,
                    providerId = providerId,
                    providerName = activity.createdBy?.name,
                    providerRating = providerRatings.map { it.stars }.average().takeIf { providerRatings.isNotEmpty() },
                    providerRatingCount = providerRatings.size,
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
        activity.createdBy = userRepository.findByEmail(authentication.name)

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


}
