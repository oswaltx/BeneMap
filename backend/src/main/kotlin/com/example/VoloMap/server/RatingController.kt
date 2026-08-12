package com.example.VoloMap.server

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class RatingRequest(
    @field:Min(1) @field:Max(5) val stars: Int,
    @field:Size(max = 1000) val comment: String? = null
)

data class RatingEntry(
    val userName: String,
    val stars: Int,
    val comment: String?,
    val createdAt: Instant
)

data class RatingListResponse(
    val average: Double?,
    val count: Int,
    val ratings: List<RatingEntry>,
    val myRating: RatingEntry?
)

@RestController
class RatingController(
    private val activityRepository: VolunteerActivityRepository,
    private val userRepository: UserRepository,
    private val activityRatingRepository: ActivityRatingRepository,
    private val providerRatingRepository: ProviderRatingRepository,
) {

    @PostMapping("/activities/{id}/ratings")
    fun rateActivity(
        @PathVariable id: Long,
        @Valid @RequestBody req: RatingRequest,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val activity = activityRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val user = userRepository.findByEmail(authentication.name)!!
        val existing = activityRatingRepository.findByUserAndActivity(user, activity)
        if (existing != null) {
            existing.stars = req.stars
            existing.comment = req.comment
            activityRatingRepository.save(existing)
        } else {
            activityRatingRepository.save(
                ActivityRating(user = user, activity = activity, stars = req.stars, comment = req.comment)
            )
        }
        return ResponseEntity.ok().build()
    }

    @GetMapping("/activities/{id}/ratings")
    fun getActivityRatings(
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<RatingListResponse> {
        val activity = activityRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val ratings = activityRatingRepository.findByActivity(activity)
            .map { RatingEntry(it.user.name, it.stars, it.comment, it.createdAt) }
        val me = authentication?.let { userRepository.findByEmail(it.name) }
        val myRating = me?.let { activityRatingRepository.findByUserAndActivity(it, activity) }
            ?.let { RatingEntry(it.user.name, it.stars, it.comment, it.createdAt) }
        return ResponseEntity.ok(
            RatingListResponse(
                average = ratings.map { it.stars }.average().takeIf { ratings.isNotEmpty() },
                count = ratings.size,
                ratings = ratings,
                myRating = myRating
            )
        )
    }

    @PostMapping("/providers/{id}/ratings")
    fun rateProvider(
        @PathVariable id: Long,
        @Valid @RequestBody req: RatingRequest,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val provider = userRepository.findById(id).orElse(null)
        if (provider == null || provider.role != Role.ANBIETER) {
            return ResponseEntity.notFound().build()
        }
        val user = userRepository.findByEmail(authentication.name)!!
        val existing = providerRatingRepository.findByUserAndProvider(user, provider)
        if (existing != null) {
            existing.stars = req.stars
            existing.comment = req.comment
            providerRatingRepository.save(existing)
        } else {
            providerRatingRepository.save(
                ProviderRating(user = user, provider = provider, stars = req.stars, comment = req.comment)
            )
        }
        return ResponseEntity.ok().build()
    }

    @GetMapping("/providers/{id}/ratings")
    fun getProviderRatings(
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<RatingListResponse> {
        val provider = userRepository.findById(id).orElse(null)
        if (provider == null || provider.role != Role.ANBIETER) {
            return ResponseEntity.notFound().build()
        }
        val ratings = providerRatingRepository.findByProvider(provider)
            .map { RatingEntry(it.user.name, it.stars, it.comment, it.createdAt) }
        val me = authentication?.let { userRepository.findByEmail(it.name) }
        val myRating = me?.let { providerRatingRepository.findByUserAndProvider(it, provider) }
            ?.let { RatingEntry(it.user.name, it.stars, it.comment, it.createdAt) }
        return ResponseEntity.ok(
            RatingListResponse(
                average = ratings.map { it.stars }.average().takeIf { ratings.isNotEmpty() },
                count = ratings.size,
                ratings = ratings,
                myRating = myRating
            )
        )
    }
}
