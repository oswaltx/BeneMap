package com.example.VoloMap.server

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

data class SignupEntry(val name: String, val email: String)

data class SignupStatusResponse(
    val count: Int,
    val maxParticipants: Int?,
    val signedUp: Boolean,
    val participants: List<SignupEntry>,
)

@RestController
class SignupController(
    private val activityRepository: VolunteerActivityRepository,
    private val userRepository: UserRepository,
    private val activitySignupRepository: ActivitySignupRepository,
) {

    @PostMapping("/activities/{id}/signup")
    fun signUp(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<*> {
        val activity = activityRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
        val user = userRepository.findByEmail(authentication.name)!!

        if (activitySignupRepository.findByUserAndActivity(user, activity) != null) {
            return ResponseEntity.ok().build<Any>()
        }

        val max = activity.maxParticipants
        if (max != null && activitySignupRepository.countByActivity(activity) >= max) {
            return ResponseEntity.status(409).body(ErrorResponse("Diese Aktivität ist bereits ausgebucht."))
        }

        activitySignupRepository.save(ActivitySignup(user = user, activity = activity))
        return ResponseEntity.ok().build<Any>()
    }

    @DeleteMapping("/activities/{id}/signup")
    fun withdraw(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val activity = activityRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val user = userRepository.findByEmail(authentication.name)!!

        activitySignupRepository.findByUserAndActivity(user, activity)?.let {
            activitySignupRepository.delete(it)
        }
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/activities/{id}/signups")
    fun getSignups(
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<SignupStatusResponse> {
        val activity = activityRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val me = authentication?.let { userRepository.findByEmail(it.name) }
        val signedUp = me?.let { activitySignupRepository.findByUserAndActivity(it, activity) != null } ?: false
        val isOwner = me != null && activity.createdBy?.id == me.id

        val signups = activitySignupRepository.findByActivity(activity)
        val participants = if (isOwner) signups.map { SignupEntry(it.user.name, it.user.email) } else emptyList()

        return ResponseEntity.ok(
            SignupStatusResponse(
                count = signups.size,
                maxParticipants = activity.maxParticipants,
                signedUp = signedUp,
                participants = participants,
            )
        )
    }
}
