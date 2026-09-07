package com.example.VoloMap.server

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime

// Läuft stündlich und verschickt Erinnerungsmails für Aktivitäten, die in
// 23–24 Stunden beginnen — ein 1h-Fenster passt genau zum stündlichen Takt,
// ohne Aktivitäten zu verpassen oder (dank reminderSentAt) doppelt anzuschreiben.
@Component
class ReminderJob(
    private val activitySignupRepository: ActivitySignupRepository,
    private val reminderMailer: ReminderMailer,
) {

    @Scheduled(fixedRate = 60 * 60 * 1000)
    fun sendUpcomingActivityReminders() {
        val now = LocalDateTime.now()
        val dueSignups = activitySignupRepository.findByReminderSentAtIsNullAndActivity_DateTimeBetween(
            now.plusHours(23),
            now.plusHours(24),
        )

        for (signup in dueSignups) {
            reminderMailer.send(
                signup.user.email,
                signup.activity.name,
                signup.activity.dateTime!!,
                signup.activity.addressText,
            )
            signup.reminderSentAt = Instant.now()
            activitySignupRepository.save(signup)
        }
    }
}
