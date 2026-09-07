package com.example.VoloMap.server

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class VerifyProviderRequest(val verified: Boolean)

// Minimaler Admin-Gate ohne eigene Rolle/Tabelle: eine E-Mail-Allowlist aus der
// Konfiguration (admin.emails, kommasepariert). Reicht für die einzige heutige
// Admin-Aktion (Anbieter verifizieren) — ein echtes Rollenmodell lohnt sich erst,
// wenn mehr als eine Handvoll Admin-Aktionen dazukommen.
@RestController
class AdminController(
    private val userRepository: UserRepository,
    @Value("\${admin.emails:}") private val adminEmailsRaw: String,
) {
    private val adminEmails: Set<String> by lazy {
        adminEmailsRaw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    }

    @PatchMapping("/admin/providers/{id}/verify")
    fun verifyProvider(
        @PathVariable id: Long,
        @RequestBody req: VerifyProviderRequest,
        authentication: Authentication,
    ): ResponseEntity<*> {
        if (authentication.name.lowercase() !in adminEmails) {
            return ResponseEntity.status(403).body(ErrorResponse("Nicht berechtigt."))
        }
        val provider = userRepository.findById(id).orElse(null)
        if (provider == null || provider.role != Role.ANBIETER) {
            return ResponseEntity.notFound().build<Any>()
        }
        provider.verified = req.verified
        userRepository.save(provider)
        return ResponseEntity.ok().build<Any>()
    }
}
