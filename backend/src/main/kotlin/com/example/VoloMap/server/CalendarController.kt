package com.example.VoloMap.server

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CalendarTokenResponse(val token: String)

@RestController
class CalendarController(
    private val userRepository: UserRepository,
    private val activityRepository: VolunteerActivityRepository,
    private val activitySignupRepository: ActivitySignupRepository,
    private val calendarService: CalendarService,
) {

    @GetMapping("/auth/me/calendar-token")
    fun myCalendarToken(authentication: Authentication): ResponseEntity<CalendarTokenResponse> {
        val user = userRepository.findByEmail(authentication.name)!!
        return ResponseEntity.ok(CalendarTokenResponse(tokenFor(user)))
    }

    @PostMapping("/auth/me/calendar-token/regenerate")
    fun regenerateCalendarToken(authentication: Authentication): ResponseEntity<CalendarTokenResponse> {
        val user = userRepository.findByEmail(authentication.name)!!
        user.calendarToken = UUID.randomUUID().toString()
        userRepository.save(user)
        return ResponseEntity.ok(CalendarTokenResponse(user.calendarToken!!))
    }

    // Öffentlich (kein Login) — Kalender-Apps können beim Abonnieren keine
    // Session-Cookies mitschicken, der geheime Token in der URL selbst ist die
    // Zugriffskontrolle. Nur die eigenen Anmeldungen des Token-Inhabers, nie
    // fremde Daten.
    @GetMapping("/calendar/{token}.ics")
    fun signupsCalendar(@PathVariable token: String): ResponseEntity<String> {
        val user = userRepository.findByCalendarToken(token) ?: return ResponseEntity.notFound().build()
        val activities = activitySignupRepository.findByUser(user).map { it.activity }
        val ics = calendarService.buildIcs(activities)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/calendar"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"benemap-anmeldungen.ics\"")
            .body(ics)
    }

    // Öffentlich wie /markers — ein Anbieter-Kalender enthält keine Daten, die nicht
    // ohnehin schon über /markers für jeden einsehbar sind. Kein Token nötig, damit
    // ein Anbieter den Link einfach direkt in seinem eigenen Kalender abonnieren kann.
    @GetMapping("/providers/{id}/calendar.ics")
    fun providerCalendar(@PathVariable id: Long): ResponseEntity<String> {
        val provider = userRepository.findById(id).orElse(null)
        if (provider == null || provider.role != Role.ANBIETER) {
            return ResponseEntity.notFound().build()
        }
        val activities = activityRepository.findByCreatedBy(provider)
        val ics = calendarService.buildIcs(activities)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/calendar"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"benemap-anbieter-$id.ics\"")
            .body(ics)
    }

    private fun tokenFor(user: User): String {
        if (user.calendarToken == null) {
            user.calendarToken = UUID.randomUUID().toString()
            userRepository.save(user)
        }
        return user.calendarToken!!
    }
}
