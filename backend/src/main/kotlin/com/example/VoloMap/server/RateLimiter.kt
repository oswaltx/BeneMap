package com.example.VoloMap.server

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

// Generischer Fenster-Zähler für Abuse-Schutz auf ausgewählten Endpoints.
// Rein In-Memory, da die App aktuell nur als Einzelinstanz läuft — gleiches
// Muster wie ForgotPasswordRateLimiter, nur mit frei wählbarem Limit/Fenster
// pro Aufrufstelle statt einer festen Eskalationslogik.
//
// Ist standardmäßig aktiv, wird aber im Testprofil (rate-limit.enabled=false
// in backend/src/test/resources/application.properties) komplett abgeschaltet
// — sonst würden sich Login/Registrierung/etc. über die gesamte Testsuite
// hinweg dieselbe MockMvc-Test-IP/denselben In-Memory-Zustand teilen und
// unabhängige Tests gegenseitig mit 429 blockieren.
@Component
class RateLimiter(
    @Value("\${rate-limit.enabled:true}") private val enabled: Boolean,
) {
    private data class Window(var count: Int, var windowStart: Instant)

    private val state = ConcurrentHashMap<String, Window>()

    @Synchronized
    fun isAllowed(key: String, maxRequests: Int, window: Duration): Boolean {
        if (!enabled) return true
        val now = Instant.now()
        val existing = state[key]
        if (existing == null || Duration.between(existing.windowStart, now) >= window) {
            state[key] = Window(1, now)
            return true
        }
        if (existing.count < maxRequests) {
            existing.count++
            return true
        }
        return false
    }
}
