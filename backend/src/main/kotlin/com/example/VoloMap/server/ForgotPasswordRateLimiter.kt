package com.example.VoloMap.server

import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

// Verzögert wiederholte Passwort-Reset-Anfragen für dieselbe E-Mail-Adresse,
// unabhängig davon, ob dazu ein Konto existiert (sonst ließe sich über das
// Zeitverhalten erraten, welche Adressen registriert sind). Rein In-Memory,
// da die App aktuell nur als Einzelinstanz läuft.
@Component
class ForgotPasswordRateLimiter {
    private data class State(val count: Int, val nextAllowedAt: Instant)

    private val state = ConcurrentHashMap<String, State>()

    // Gibt null zurück, wenn die Anfrage erlaubt ist (und zeichnet sie auf),
    // sonst die Anzahl Sekunden, die der Aufrufer noch warten muss.
    @Synchronized
    fun checkAndRecord(email: String): Long? {
        val now = Instant.now()
        val existing = state[email]
        if (existing != null && now.isBefore(existing.nextAllowedAt)) {
            return Duration.between(now, existing.nextAllowedAt).seconds + 1
        }
        val newCount = (existing?.count ?: 0) + 1
        val cooldown = if (newCount == 1) Duration.ofSeconds(60) else Duration.ofMinutes(5)
        state[email] = State(newCount, now.plus(cooldown))
        return null
    }
}
