package com.example.VoloMap.server

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

// Aktivitäten ohne durationHours zählen mit dieser Pauschale — die meisten
// Ehrenamts-Einsätze dauern mindestens so lange, und 0 würde sie fälschlich
// gar nicht zum Gesamtwert beitragen lassen.
const val DEFAULT_ACTIVITY_DURATION_HOURS = 2.0

data class HoursResponse(val totalHours: Double, val completedActivityCount: Int)

@RestController
class HoursController(
    private val userRepository: UserRepository,
    private val activitySignupRepository: ActivitySignupRepository,
    private val certificateService: CertificateService,
) {

    @GetMapping("/auth/me/hours")
    fun myHours(authentication: Authentication): ResponseEntity<HoursResponse> {
        val user = userRepository.findByEmail(authentication.name)!!
        return ResponseEntity.ok(computeHours(user))
    }

    @GetMapping("/auth/me/certificate.pdf")
    fun myCertificate(authentication: Authentication): ResponseEntity<ByteArray> {
        val user = userRepository.findByEmail(authentication.name)!!
        val hours = computeHours(user)
        val pdf = certificateService.generate(user.name, hours.totalHours, hours.completedActivityCount)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ehrenamt-zertifikat.pdf\"")
            .body(pdf)
    }

    // Nur bereits stattgefundene Aktivitäten zählen — für zukünftige Anmeldungen
    // wurde noch keine Zeit geleistet. Ohne dateTime (z.B. gescrapte städtische
    // Angebote) lässt sich "stattgefunden" nicht feststellen, daher ausgeschlossen.
    private fun computeHours(user: User): HoursResponse {
        val now = LocalDateTime.now()
        val completed = activitySignupRepository.findByUser(user)
            .filter { it.activity.dateTime != null && it.activity.dateTime!!.isBefore(now) }
        val total = completed.sumOf { it.activity.durationHours ?: DEFAULT_ACTIVITY_DURATION_HOURS }
        return HoursResponse(totalHours = total, completedActivityCount = completed.size)
    }
}
