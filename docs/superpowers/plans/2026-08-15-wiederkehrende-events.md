# Wiederkehrende Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Anbieter können beim Anlegen einer Aktivität ein Wiederholungsmuster ("alle N Tage/Wochen") angeben, wodurch automatisch mehrere unabhängige Aktivitäts-Termine bis zu 3 Monate im Voraus erzeugt werden, statt jeden Termin einzeln anlegen zu müssen.

**Architecture:** Neuer Backend-Endpunkt `POST /add-recurring`, der die Adresse einmal geokodiert und dann N `VolunteerActivity`-Zeilen mit fortlaufenden Terminen erzeugt (kein Serien-Konzept, keine neue Tabelle/Spalte). `AddActivity.svelte` bekommt eine Checkbox + zwei Zusatzfelder, die bei aktivierter Wiederholung `POST /add-recurring` statt `POST /add` aufrufen.

**Tech Stack:** Kotlin/Spring Boot (Backend), Svelte 5 legacy-style (Frontend), kein Test-Framework im Frontend (manuelle Browser-Verifikation, etablierte Konvention).

## Global Constraints

- Muster: flexibles Intervall in Tagen oder Wochen ("alle N Tage/Wochen") — kein wöchentlich/monatlich-Duo, kein volles Regelwerk.
- Anzeige: ein echter, unabhängiger `VolunteerActivity`-Eintrag pro Termin, automatisch erzeugt — keine Karten-/Listen-Sonderbehandlung.
- Löschen/Bearbeiten: nur einzeln über die bestehenden Mechanismen — kein Serien-Bulk-Delete, keine Serien-Kennung in der Datenbank.
- Horizont: fester Zeitraum von 3 Monaten ab dem Startdatum, serverseitig zusätzlich auf maximal 60 Termine gedeckelt.
- Adresse wird **einmal** geokodiert, nicht pro Termin (Nominatim-Rate-Limit: ~1,1s pro Aufruf in `GeocodingService.geocode`). Alle Termine einer Serie teilen sich dieselben Koordinaten.
- `recurrenceIntervalDays < 1` → `400 Bad Request`.
- Kein Wiederholungs-UI in `EditActivityModal.svelte` — nur beim erstmaligen Anlegen konfigurierbar.
- Keine Änderung an `PinDetailPanel.svelte`, `VolunteerList.svelte`, `Map.svelte`, am Datenmodell (`VolunteerActivity.kt`) oder an `EditActivityModal.svelte`.

---

### Task 1: Backend — `POST /add-recurring`

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddRecurringActivityTest.kt`

**Interfaces:**
- Produces: `POST /add-recurring` — Request-Body `AddRecurringActivityRequest(name: String, description: String? = null, addressText: String? = null, category: String? = null, dateTime: LocalDateTime, photoUrls: String? = null, recurrenceIntervalDays: Int)`. Response bei Erfolg: `200 OK` mit `List<VolunteerActivity>` (alle erzeugten Termine). Response bei `recurrenceIntervalDays < 1`: `400 Bad Request` mit `ErrorResponse(error: String)` (bereits definiert in `AuthController.kt`, gleiches Package, kein Import nötig).
- Consumes: `VolunteerActivityRepository.save`, `GeocodingService.geocode`, `UserRepository.findByEmail` (alle bereits im `MainController`-Konstruktor vorhanden), `normalizePhotoUrls` (bereits als private Funktion in `MainController.kt` vorhanden).

- [ ] **Step 1: Fehlschlagenden Test für die Intervall-Validierung schreiben**

Neue Datei `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddRecurringActivityTest.kt`:

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication
import java.time.LocalDateTime

class MainControllerAddRecurringActivityTest {

    private val provider = User(
        email = "anbieter@example.com",
        passwordHash = "hashed",
        name = "Anbieter Anna",
        role = Role.ANBIETER
    )

    private fun authenticationFor(email: String): Authentication {
        val authentication: Authentication = mock()
        whenever(authentication.name).thenReturn(email)
        return authentication
    }

    @Test
    fun `rejects an interval below 1 day`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Sprachcafé",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 0
        )

        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))

        assertEquals(400, result.statusCode.value())
        verify(repository, never()).save(any<VolunteerActivity>())
    }
}
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `cd backend && .\gradlew.bat test --tests "com.example.VoloMap.server.MainControllerAddRecurringActivityTest"`
Expected: FAIL — `addRecurringActivity` existiert noch nicht auf `MainController`, `AddRecurringActivityRequest` existiert noch nicht (Kompilierfehler).

- [ ] **Step 3: `AddRecurringActivityRequest`-DTO und Konstanten ergänzen**

In `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`, direkt nach der bestehenden Zeile `private const val MAX_PHOTO_URLS = 10` einfügen:

```kotlin
private const val MAX_RECURRING_OCCURRENCES = 60
private const val RECURRENCE_HORIZON_MONTHS = 3L

data class AddRecurringActivityRequest(
    val name: String,
    val description: String? = null,
    val addressText: String? = null,
    val category: String? = null,
    val dateTime: LocalDateTime,
    val photoUrls: String? = null,
    val recurrenceIntervalDays: Int,
)
```

- [ ] **Step 4: `addRecurringActivity`-Endpunkt implementieren**

In `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`, direkt nach der bestehenden `addActivity`-Methode (vor `updateActivity`) einfügen:

```kotlin
    @PostMapping("/add-recurring")
    fun addRecurringActivity(
        @RequestBody req: AddRecurringActivityRequest,
        authentication: Authentication
    ): ResponseEntity<*> {
        if (req.recurrenceIntervalDays < 1) {
            return ResponseEntity.badRequest().body(ErrorResponse("recurrenceIntervalDays muss mindestens 1 sein."))
        }

        val provider = userRepository.findByEmail(authentication.name)
        val normalizedPhotoUrls = normalizePhotoUrls(req.photoUrls)

        var latitude: Double? = null
        var longitude: Double? = null
        if (!req.addressText.isNullOrBlank()) {
            val coords = geocodingService.geocode(req.addressText)
            if (coords != null) {
                latitude = coords.first
                longitude = coords.second
            }
        }

        val horizonEnd = req.dateTime.plusMonths(RECURRENCE_HORIZON_MONTHS)
        val occurrenceDates = generateSequence(req.dateTime) { it.plusDays(req.recurrenceIntervalDays.toLong()) }
            .takeWhile { it.isBefore(horizonEnd) }
            .take(MAX_RECURRING_OCCURRENCES)
            .toList()

        val createdActivities = occurrenceDates.map { occurrenceDateTime ->
            repository.save(
                VolunteerActivity(
                    name = req.name,
                    description = req.description,
                    addressText = req.addressText,
                    category = req.category,
                    photoUrls = normalizedPhotoUrls,
                    latitude = latitude,
                    longitude = longitude,
                    dateTime = occurrenceDateTime,
                    createdBy = provider,
                )
            )
        }

        return ResponseEntity.ok(createdActivities)
    }
```

- [ ] **Step 5: Test erneut ausführen, Erfolg bestätigen**

Run: `cd backend && .\gradlew.bat test --tests "com.example.VoloMap.server.MainControllerAddRecurringActivityTest"`
Expected: PASS (1/1).

- [ ] **Step 6: Weitere Tests für Vorkommen-Berechnung, Geokodierung und `createdBy` schreiben**

An `MainControllerAddRecurringActivityTest.kt` anhängen (vor der schließenden `}` der Klasse):

```kotlin

    @Test
    fun `weekly interval produces one occurrence per week within the 3-month horizon`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Sprachcafé",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 7
        )

        @Suppress("UNCHECKED_CAST")
        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))
        val activities = result.body as List<VolunteerActivity>

        assertEquals(14, activities.size)
        assertEquals(LocalDateTime.parse("2026-08-15T10:00:00"), activities.first().dateTime)
        assertEquals(LocalDateTime.parse("2026-11-14T10:00:00"), activities.last().dateTime)
    }

    @Test
    fun `daily interval over 3 months is capped at 60 occurrences`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Tägliche Aktion",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 1
        )

        @Suppress("UNCHECKED_CAST")
        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))
        val activities = result.body as List<VolunteerActivity>

        assertEquals(60, activities.size)
    }

    @Test
    fun `geocodes the address exactly once and shares coordinates across all occurrences`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(geocodingService.geocode("Domkloster 4, Köln")).thenReturn(Pair(50.9413, 6.9583))
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Sprachcafé",
            addressText = "Domkloster 4, Köln",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 7
        )

        @Suppress("UNCHECKED_CAST")
        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))
        val activities = result.body as List<VolunteerActivity>

        verify(geocodingService, times(1)).geocode(any<String>())
        assertTrue(activities.all { it.latitude == 50.9413 && it.longitude == 6.9583 })
    }

    @Test
    fun `sets createdBy on every generated occurrence`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Sprachcafé",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 7
        )

        @Suppress("UNCHECKED_CAST")
        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))
        val activities = result.body as List<VolunteerActivity>

        assertTrue(activities.all { it.createdBy == provider })
    }

    @Test
    fun `saves occurrences without coordinates when geocoding finds nothing`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(geocodingService.geocode(any<String>())).thenReturn(null)
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Sprachcafé",
            addressText = "Nonexistent Place XYZ",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 7
        )

        @Suppress("UNCHECKED_CAST")
        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))
        val activities = result.body as List<VolunteerActivity>

        assertEquals(200, result.statusCode.value())
        assertTrue(activities.all { it.latitude == null && it.longitude == null })
    }

    @Test
    fun `does not geocode when no address is given`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Sprachcafé",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 7
        )

        @Suppress("UNCHECKED_CAST")
        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))
        val activities = result.body as List<VolunteerActivity>

        verify(geocodingService, never()).geocode(any<String>())
        assertNull(activities.first().latitude)
    }
```

- [ ] **Step 7: Alle neuen Tests ausführen, Erfolg bestätigen**

Run: `cd backend && .\gradlew.bat test --tests "com.example.VoloMap.server.MainControllerAddRecurringActivityTest"`
Expected: PASS (7/7).

- [ ] **Step 8: `SecurityConfig.kt` — `/add-recurring` absichern**

In `backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt`, die bestehende Zeile

```kotlin
                it.requestMatchers(HttpMethod.POST, "/add").hasRole("ANBIETER")
```

ersetzen durch:

```kotlin
                it.requestMatchers(HttpMethod.POST, "/add", "/add-recurring").hasRole("ANBIETER")
```

- [ ] **Step 9: Vollen Backend-Testlauf ausführen**

Run: `cd backend && .\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`, keine fehlgeschlagenen Tests.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddRecurringActivityTest.kt
git commit -m "feat: add POST /add-recurring endpoint for recurring activity series"
```

---

### Task 2: Frontend — Wiederholungs-Formularfelder in `AddActivity.svelte`

**Files:**
- Modify: `frontend/src/lib/AddActivity.svelte`

**Interfaces:**
- Consumes: `POST /add-recurring` aus Task 1 — Request-Body `{ name, description, addressText, category, dateTime, photoUrls, recurrenceIntervalDays }`, Response `VolunteerActivity[]` (Array, keine Wrapper-Response).
- Produces: nichts, das andere Tasks konsumieren (letzter Frontend-Task).

- [ ] **Step 1: State und Submit-Logik erweitern**

In `frontend/src/lib/AddActivity.svelte`, den kompletten `<script>`-Block durch folgenden ersetzen:

```svelte
<script lang="ts">
    import Link from "./Link.svelte";
    import { currentUser, authChecked, fetchWithSessionCheck } from "../auth";

    let name = "";
    let description = "";
    let addressText = "";
    let category = "";
    let dateTime = "";
    let photoUrlsText = "";
    let isRecurring = false;
    let recurrenceCount = 1;
    let recurrenceUnit: "days" | "weeks" = "weeks";

    let submitting = false;
    let statusMessage: string | null = null;
    let statusIsWarning = false;

    async function handleSubmit() {
        if (!name.trim()) {
            statusMessage = "Name ist ein Pflichtfeld.";
            statusIsWarning = true;
            return;
        }

        submitting = true;
        statusMessage = null;

        const baseBody = {
            name,
            description: description || null,
            addressText: addressText || null,
            category: category || null,
            dateTime: dateTime ? dateTime + ":00" : undefined,
            photoUrls: photoUrlsText.trim() || undefined,
        };

        const endpoint = isRecurring
            ? "http://localhost:8080/add-recurring"
            : "http://localhost:8080/add";
        const body = isRecurring
            ? {
                  ...baseBody,
                  recurrenceIntervalDays: recurrenceUnit === "weeks" ? recurrenceCount * 7 : recurrenceCount,
              }
            : baseBody;

        try {
            const res = await fetchWithSessionCheck(endpoint, {
                method: "POST",
                credentials: "include",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body),
            });

            if (!res.ok) {
                statusMessage = "Fehler beim Speichern. Bitte versuche es erneut.";
                statusIsWarning = true;
                return;
            }

            const saved = await res.json();

            if (isRecurring) {
                const activities: { latitude: number | null; longitude: number | null }[] = saved;
                const missingCoords = activities.some((a) => a.latitude == null || a.longitude == null);
                if (missingCoords) {
                    statusMessage = `${activities.length} Termine wurden gespeichert — die Adresse konnte aber nicht gefunden werden, sie erscheinen noch nicht auf der Karte.`;
                    statusIsWarning = true;
                } else {
                    statusMessage = `${activities.length} Termine wurden angelegt.`;
                    statusIsWarning = false;
                }
            } else if (saved.latitude == null || saved.longitude == null) {
                statusMessage = "Gespeichert — die Adresse konnte aber nicht gefunden werden, der Eintrag erscheint noch nicht auf der Karte.";
                statusIsWarning = true;
            } else {
                statusMessage = "Aktivität wurde gespeichert.";
                statusIsWarning = false;
            }

            name = "";
            description = "";
            addressText = "";
            category = "";
            dateTime = "";
            photoUrlsText = "";
            isRecurring = false;
            recurrenceCount = 1;
            recurrenceUnit = "weeks";
        } catch (e) {
            statusMessage = "Server nicht erreichbar. Bitte versuche es später erneut.";
            statusIsWarning = true;
        } finally {
            submitting = false;
        }
    }
</script>
```

- [ ] **Step 2: Formularfelder ergänzen**

In `frontend/src/lib/AddActivity.svelte`, die bestehende Markup-Stelle

```svelte
            <label>
                Foto-URLs (eine pro Zeile)
                <textarea bind:value={photoUrlsText} rows="3" placeholder={"https://...\nhttps://..."}></textarea>
            </label>

            <button type="submit" disabled={submitting}>
```

ersetzen durch:

```svelte
            <label>
                Foto-URLs (eine pro Zeile)
                <textarea bind:value={photoUrlsText} rows="3" placeholder={"https://...\nhttps://..."}></textarea>
            </label>

            <label class="checkbox-label">
                <input type="checkbox" bind:checked={isRecurring} />
                Wiederholt sich
            </label>

            {#if isRecurring}
                <div class="recurrence-fields">
                    <label>
                        Alle
                        <input type="number" min="1" bind:value={recurrenceCount} />
                    </label>
                    <label>
                        Einheit
                        <select bind:value={recurrenceUnit}>
                            <option value="days">Tage</option>
                            <option value="weeks">Wochen</option>
                        </select>
                    </label>
                </div>
            {/if}

            <button type="submit" disabled={submitting}>
```

- [ ] **Step 3: CSS für die neuen Felder ergänzen**

In `frontend/src/lib/AddActivity.svelte`, direkt nach dem bestehenden `label { ... }`-Block im `<style>`-Bereich einfügen:

```css
    .checkbox-label {
        flex-direction: row;
        align-items: center;
        gap: 8px;
    }

    .recurrence-fields {
        display: flex;
        gap: 12px;
    }

    .recurrence-fields label {
        flex: 1;
    }
```

- [ ] **Step 4: `svelte-check` laufen lassen**

Run: `cd frontend && npx svelte-check --tsconfig ./tsconfig.json`
Expected: keine neuen Fehler (die 2 bereits bestehenden `esrap`-Fehler in `node_modules` sind vorbestehend).

- [ ] **Step 5: Manuell verifizieren**

Backend (`cd backend && .\gradlew.bat bootRun`) und Frontend (`cd frontend && npm run dev`) starten. Als eingeloggter Anbieter auf `/add`:
- Formular ohne aktivierte Checkbox absenden → Verhalten identisch zu vorher (ein Eintrag, Meldung "Aktivität wurde gespeichert.").
- Checkbox "Wiederholt sich" aktivieren → die beiden Zusatzfelder ("Alle", "Einheit") erscheinen.
- Mit "Alle: 2", "Einheit: Wochen", einer echten Adresse und einem Startdatum absenden → Erfolgsmeldung nennt die Anzahl erzeugter Termine (z. B. "6 Termine wurden angelegt." für ein 3-Monats-Fenster bei 2-Wochen-Intervall); auf der Karte erscheint für jeden Termin ein eigener Punkt (bzw. bei identischer Adresse ein Cluster-Pin mit der entsprechenden Anzahl, siehe Feature "Mehrere Aktivitäten an einem Ort").
- Einen der erzeugten Termine über die bestehende Bearbeiten/Löschen-Funktion einzeln ändern/löschen → funktioniert unverändert wie bei jeder anderen Aktivität.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/AddActivity.svelte
git commit -m "feat: add recurrence fields to AddActivity form"
```

---

### Task 3: End-to-End-Verifikation

**Files:** keine Code-Änderungen — reine Verifikation, ggf. kleine Nacharbeiten falls Abweichungen gefunden werden.

**Interfaces:**
- Consumes: das vollständige Feature aus Task 1 + Task 2.
- Produces: nichts (Verifikationsergebnis wird im Ledger festgehalten).

- [ ] **Step 1: Sauberen Zustand herstellen**

Backend und Frontend laufen. Als eingeloggter Anbieter bereit, neue Aktivitäten anzulegen.

- [ ] **Step 2: Regressionscheck — normales Anlegen**

Eine einzelne Aktivität ohne aktivierte Wiederholung anlegen — Verhalten, Meldungen und Kartenanzeige exakt wie vor dieser Änderung.

- [ ] **Step 3: Wiederholende Serie — Tages-Intervall**

Eine Serie mit "Alle: 3", "Einheit: Tage" anlegen. Erwartete Anzahl: `⌊92 Tage / 3⌋ + 1 = 31` Termine (92 Tage bis zum 3-Monats-Horizont, siehe Task 1). Prüfen: Erfolgsmeldung nennt 31, alle 31 Termine sind einzeln in der Liste (Bottom-Sheet aufklappen) sichtbar, jeweils 3 Tage auseinander.

- [ ] **Step 4: Wiederholende Serie — Wochen-Intervall und 60er-Deckel**

Eine Serie mit "Alle: 1", "Einheit: Tage" (täglich) anlegen. Erwartete Anzahl: 60 (durch den serverseitigen Deckel begrenzt, nicht die vollen ~92 möglichen Tage). Prüfen: Erfolgsmeldung nennt 60.

- [ ] **Step 5: Einzelne Bearbeitung/Löschung eines generierten Termins**

Einen der in Step 3 erzeugten Termine über den bestehenden "Bearbeiten"-Button umbenennen — nur dieser eine Termin ändert sich, die übrigen 30 bleiben unverändert. Einen anderen der erzeugten Termine löschen — nur dieser verschwindet, die übrigen bleiben.

- [ ] **Step 6: Konsole/Netzwerk prüfen**

Keine neuen Fehler in der Browser-Konsole oder fehlgeschlagene Requests, die durch diese Änderung verursacht wurden.

- [ ] **Step 7: Ledger-Eintrag**

Ergebnis der Verifikation im SDD-Ledger festhalten (Status, ggf. gefundene und behobene Abweichungen).
