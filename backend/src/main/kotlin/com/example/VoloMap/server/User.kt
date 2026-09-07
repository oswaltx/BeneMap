package com.example.VoloMap.server

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class Role { ANBIETER, USER }

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(unique = true, nullable = false)
    var email: String,

    var passwordHash: String,

    var name: String,

    @Enumerated(EnumType.STRING)
    var role: Role,

    var photoUrl: String? = null,
    var websiteUrl: String? = null,

    // Von einem Admin manuell gesetzt (siehe AdminController), zeigt einen
    // Vertrauens-Badge im Frontend. Kein Self-Service — nur über die
    // Admin-E-Mail-Allowlist (admin.emails) änderbar.
    @Column(nullable = false, columnDefinition = "boolean default false")
    var verified: Boolean = false,

    // Geheimer Token für den abonnierbaren ICS-Kalender-Feed der eigenen Anmeldungen
    // (siehe CalendarController) — lazy erzeugt beim ersten Abruf, kein Migrations-
    // Default nötig, da eine gemeinsame Default-UUID für alle Bestandsnutzer beim
    // ALTER TABLE ein Sicherheitsproblem wäre (kollidierende Feed-URLs).
    @Column(unique = true)
    var calendarToken: String? = null,

    // Optionale URL eines externen Kalenders (z.B. Google/Outlook), aus dem
    // Aktivitäten periodisch importiert werden (siehe CalendarImportJob). Nur für
    // Anbieter sinnvoll, aber nicht rollen-eingeschränkt gespeichert.
    @Column(columnDefinition = "TEXT")
    var externalCalendarUrl: String? = null,

    // columnDefinition backfills existing rows during the ddl-auto=update ALTER TABLE —
    // without it, adding this NOT NULL column to an already-populated users table fails.
    @Column(nullable = false, columnDefinition = "boolean default false")
    var emailVerified: Boolean = false,

    var createdAt: Instant = Instant.now(),
)
