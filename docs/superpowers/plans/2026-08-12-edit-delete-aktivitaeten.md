# Edit/Delete-Aktivitäten Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Anbieter können ihre eigenen Aktivitäten bearbeiten und löschen — bisher ist `/add` der einzige schreibende Endpunkt.

**Architecture:** Zwei neue Backend-Endpunkte (`PUT`/`DELETE /activities/{id}`) mit Rollen- plus Ownership-Prüfung. `UserResponse` bekommt ein `id`-Feld, damit das Frontend Eigentümerschaft erkennen kann. Frontend: eine neue `EditActivityModal.svelte`-Komponente (Overlay wie `RatingModal`) plus eine kleine geteilte `deleteActivity()`-Hilfsfunktion, beide von `PinDetailPanel.svelte` und `VolunteerList.svelte` genutzt — dort erscheinen "Bearbeiten"/"Löschen"-Buttons nur für den Eigentümer.

**Tech Stack:** Kotlin/Spring Boot 4.0.3 Backend (JUnit 5, Mockito-Kotlin für Unit-Tests, `@SpringBootTest`+`MockMvc` für Integrationstests — siehe `MainControllerAddActivityTest.kt`/`MainControllerSecurityTest.kt` als Vorbild). Svelte 5 Legacy-Stil Frontend (`<script lang="ts">`, `createEventDispatcher`, plain `let`/`$:`).

> **Nachträgliche Korrektur (nach Task 6 / finaler Review):** Dieser Plan war in sich widersprüchlich — Task 3/4/5s Code (Zeilen 648, 987, 1091) lässt `on:saved` das Modal sofort schließen, während Task 6s Verifikations-Checkliste ein manuelles Schließen voraussetzt ("nach Schließen zeigt Karte/Panel den neuen Namen"). Commit `ab7f2ff` hat das zugunsten des manuellen Schließens aufgelöst (das Modal bleibt offen, bis der Nutzer selbst auf × klickt) — sonst wäre die Erfolgs-/Warnmeldung nie sichtbar. Siehe Ledger für Details.

## Global Constraints

- Ownership-Erkennung: `activity.createdBy?.id == aktueller User.id` — serverseitig im Controller geprüft (Spring Security prüft nur die Rolle `ANBIETER`, nicht den Objektbesitz), sonst `403 Forbidden`.
- Löschen kaskadiert: zuerst alle `ActivityRating`-Zeilen zur Aktivität löschen, dann die Aktivität selbst (Pflicht-Fremdschlüssel ohne DB-seitiges `ON DELETE CASCADE`). `ProviderRating` bleibt unberührt (hängt am Anbieter, nicht an der Aktivität).
- Bearbeiten-Formular hat exakt dieselben Felder wie "Aktivität hinzufügen": Name, Beschreibung, Adresse, Kategorie, Datum/Uhrzeit. Kein `isActive`-Umschalter (kommt erst mit wiederkehrenden Events, eigenes künftiges Feature).
- Geocodierung bei Bearbeiten läuft nur neu, wenn sich `addressText` gegenüber dem gespeicherten Wert geändert hat. Schlägt sie fehl, bleiben die alten Koordinaten erhalten.
- UI-Ort: sowohl `PinDetailPanel.svelte` als auch `VolunteerList.svelte` zeigen die Buttons — deckt auch mobile Nutzung ab (kein `PinDetailPanel` unterhalb 1024px).
- Löschen-Bestätigung über natives `confirm()`, kein eigenes Dialog-Component.

---

### Task 1: `UserResponse` bekommt `id`

**Context:** Damit das Frontend beim Rendern einer Aktivität weiß "gehört mir", muss es die eigene User-ID kennen. Aktuell liefert `/auth/me` (und `/auth/register`, `/auth/login`) nur `email`/`name`/`role`.

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/AuthController.kt`
- Modify: `backend/src/test/kotlin/com/example/VoloMap/server/AuthControllerTest.kt`

**Interfaces:**
- Produces: `UserResponse(id: Long, email: String, name: String, role: Role)` — JSON-Feld `id` zusätzlich zu den bestehenden drei. Task 4/5 (Frontend) verlassen sich auf `currentUser.id` als `number`.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

In `backend/src/test/kotlin/com/example/VoloMap/server/AuthControllerTest.kt`, den bestehenden Test `me reflects session state across login and logout` erweitern — die `jsonPath("$.name")`-Zeile um eine `id`-Prüfung ergänzen:

```kotlin
    @Test
    fun `me reflects session state across login and logout`() {
        val register = mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"cara@example.com","password":"geheim123","name":"Cara","role":"USER"}""")
        ).andReturn()
        val session = register.request.session as MockHttpSession

        mockMvc.perform(get("/auth/me").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Cara"))
            .andExpect(jsonPath("$.id").isNumber)

        mockMvc.perform(post("/auth/logout").session(session))
            .andExpect(status().isOk)

        mockMvc.perform(get("/auth/me").session(session))
            .andExpect(status().isUnauthorized)
    }
```

Auch den Test `registers a new user and returns its profile` um dieselbe Prüfung ergänzen:

```kotlin
    @Test
    fun `registers a new user and returns its profile`() {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"anna@example.com","password":"geheim123","name":"Anna","role":"USER"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("anna@example.com"))
            .andExpect(jsonPath("$.name").value("Anna"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.id").isNumber)
    }
```

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.AuthControllerTest"`
Expected: FAIL — `$.id` existiert noch nicht im JSON-Response.

- [ ] **Step 3: `UserResponse` und alle drei Konstruktionsstellen anpassen**

In `backend/src/main/kotlin/com/example/VoloMap/server/AuthController.kt`, Zeile 31:

```kotlin
data class UserResponse(val id: Long, val email: String, val name: String, val role: Role)
```

Alle drei Stellen, die `UserResponse(...)` bauen, entsprechend anpassen (Reihenfolge: `id` zuerst):

```kotlin
        return ResponseEntity.ok(UserResponse(user.id, user.email, user.name, user.role))
```

Das betrifft `register` (Zeile 63), `login` (Zeile 79) und `me` (Zeile 95) — an allen drei Stellen `UserResponse(user.email, user.name, user.role)` durch `UserResponse(user.id, user.email, user.name, user.role)` ersetzen.

- [ ] **Step 4: Tests laufen lassen, Erfolg bestätigen**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.AuthControllerTest"`
Expected: `BUILD SUCCESSFUL`, alle Tests grün.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/AuthController.kt backend/src/test/kotlin/com/example/VoloMap/server/AuthControllerTest.kt
git commit -m "feat: expose user id in UserResponse"
```

---

### Task 2: `PUT`/`DELETE /activities/{id}` Backend-Endpunkte

**Context:** Kernstück des Features. Beide Endpunkte erfordern `ROLE_ANBIETER` (Spring Security) und zusätzlich einen Ownership-Check im Controller (Spring Security kennt keine Objekt-Eigentümerschaft). `DELETE` muss zuerst abhängige `ActivityRating`-Zeilen entfernen, sonst schlägt der Fremdschlüssel fehl.

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt`
- Create: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditActivityTest.kt`
- Create: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerDeleteActivityTest.kt`
- Create: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditDeleteSecurityTest.kt`

**Interfaces:**
- Consumes: `VolunteerActivityRepository` (`findById`, `save`, `delete` — alle von `JpaRepository` geerbt), `ActivityRatingRepository.findByActivity(activity)` (existiert bereits), `UserRepository.findByEmail(email)` (existiert bereits), `GeocodingService.geocode(address: String): Pair<Double, Double>?` (existiert bereits).
- Produces: `PUT /activities/{id}` (Body: `UpdateActivityRequest`, Response: aktualisierte `VolunteerActivity` bei Erfolg, `403`/`404` sonst). `DELETE /activities/{id}` (kein Body, `204` bei Erfolg, `403`/`404` sonst). Task 4/5 (Frontend) rufen diese exakten Pfade auf.

- [ ] **Step 1: Fehlschlagende Unit-Tests für `PUT` schreiben**

Create `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditActivityTest.kt`:

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication
import java.time.LocalDateTime
import java.util.Optional

class MainControllerEditActivityTest {

    private val owner = User(
        id = 1,
        email = "owner@example.com",
        passwordHash = "hashed",
        name = "Owner",
        role = Role.ANBIETER
    )

    private val otherAnbieter = User(
        id = 2,
        email = "other@example.com",
        passwordHash = "hashed",
        name = "Other",
        role = Role.ANBIETER
    )

    private fun authenticationFor(email: String): Authentication {
        val authentication: Authentication = mock()
        whenever(authentication.name).thenReturn(email)
        return authentication
    }

    @Test
    fun `owner can update name without touching coordinates when address is unchanged`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val activityRatingRepository: ActivityRatingRepository = mock()
        val existing = VolunteerActivity(
            id = 5, name = "Alt", addressText = "Domkloster 4, Köln",
            latitude = 50.9413, longitude = 6.9583, createdBy = owner
        )
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, activityRatingRepository, mock())
        val req = UpdateActivityRequest(name = "Neu", addressText = "Domkloster 4, Köln")

        val result = controller.updateActivity(5, req, authenticationFor(owner.email))

        assertEquals(200, result.statusCode.value())
        verify(geocodingService, never()).geocode(any<String>())
        assertEquals(50.9413, (result.body as VolunteerActivity).latitude)
    }

    @Test
    fun `re-geocodes when address changes`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val existing = VolunteerActivity(
            id = 5, name = "Alt", addressText = "Alte Adresse",
            latitude = 1.0, longitude = 2.0, createdBy = owner
        )
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(geocodingService.geocode("Neue Adresse")).thenReturn(Pair(50.0, 6.0))
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = UpdateActivityRequest(name = "Alt", addressText = "Neue Adresse")

        val result = controller.updateActivity(5, req, authenticationFor(owner.email))

        assertEquals(50.0, (result.body as VolunteerActivity).latitude)
        assertEquals(6.0, (result.body as VolunteerActivity).longitude)
    }

    @Test
    fun `keeps old coordinates when new address cannot be geocoded`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val existing = VolunteerActivity(
            id = 5, name = "Alt", addressText = "Alte Adresse",
            latitude = 1.0, longitude = 2.0, createdBy = owner
        )
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(geocodingService.geocode(any<String>())).thenReturn(null)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = UpdateActivityRequest(name = "Alt", addressText = "Nicht auffindbar")

        val result = controller.updateActivity(5, req, authenticationFor(owner.email))

        assertEquals(1.0, (result.body as VolunteerActivity).latitude)
        assertEquals(2.0, (result.body as VolunteerActivity).longitude)
    }

    @Test
    fun `rejects update from a non-owner with 403`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val existing = VolunteerActivity(id = 5, name = "Alt", createdBy = owner)
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(otherAnbieter.email)).thenReturn(otherAnbieter)

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = UpdateActivityRequest(name = "Gehackt")

        val result = controller.updateActivity(5, req, authenticationFor(otherAnbieter.email))

        assertEquals(403, result.statusCode.value())
        verify(repository, never()).save(any<VolunteerActivity>())
    }

    @Test
    fun `returns 404 for a non-existent activity`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(repository.findById(999)).thenReturn(Optional.empty())

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = UpdateActivityRequest(name = "Egal")

        val result = controller.updateActivity(999, req, authenticationFor(owner.email))

        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `omitting dateTime keeps the existing value unchanged`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val originalDateTime = LocalDateTime.of(2026, 1, 1, 10, 0)
        val existing = VolunteerActivity(id = 5, name = "Alt", dateTime = originalDateTime, createdBy = owner)
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = UpdateActivityRequest(name = "Alt", dateTime = null)

        val result = controller.updateActivity(5, req, authenticationFor(owner.email))

        assertEquals(originalDateTime, (result.body as VolunteerActivity).dateTime)
    }
}
```

- [ ] **Step 2: Fehlschlagende Unit-Tests für `DELETE` schreiben**

Create `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerDeleteActivityTest.kt`:

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication
import java.util.Optional

class MainControllerDeleteActivityTest {

    private val owner = User(id = 1, email = "owner@example.com", passwordHash = "hashed", name = "Owner", role = Role.ANBIETER)
    private val otherAnbieter = User(id = 2, email = "other@example.com", passwordHash = "hashed", name = "Other", role = Role.ANBIETER)

    private fun authenticationFor(email: String): Authentication {
        val authentication: Authentication = mock()
        whenever(authentication.name).thenReturn(email)
        return authentication
    }

    @Test
    fun `owner can delete their activity, ratings are removed first`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val activityRatingRepository: ActivityRatingRepository = mock()
        val existing = VolunteerActivity(id = 5, name = "Alt", createdBy = owner)
        val ratings = listOf(
            ActivityRating(id = 10, user = owner, activity = existing, stars = 5)
        )
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(activityRatingRepository.findByActivity(existing)).thenReturn(ratings)

        val controller = MainController(repository, geocodingService, userRepository, activityRatingRepository, mock())
        val result = controller.deleteActivity(5, authenticationFor(owner.email))

        assertEquals(204, result.statusCode.value())
        verify(activityRatingRepository).deleteAll(ratings)
        verify(repository).delete(existing)
    }

    @Test
    fun `rejects delete from a non-owner with 403`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val existing = VolunteerActivity(id = 5, name = "Alt", createdBy = owner)
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(otherAnbieter.email)).thenReturn(otherAnbieter)

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val result = controller.deleteActivity(5, authenticationFor(otherAnbieter.email))

        assertEquals(403, result.statusCode.value())
        verify(repository, never()).delete(any<VolunteerActivity>())
    }

    @Test
    fun `returns 404 for a non-existent activity`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(repository.findById(999)).thenReturn(Optional.empty())

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val result = controller.deleteActivity(999, authenticationFor(owner.email))

        assertEquals(404, result.statusCode.value())
    }
}
```

- [ ] **Step 3: Tests laufen lassen, Fehlschlag bestätigen**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerEditActivityTest" --tests "com.example.VoloMap.server.MainControllerDeleteActivityTest"`
Expected: FAIL mit Kompilierfehlern — `UpdateActivityRequest`, `controller.updateActivity`, `controller.deleteActivity` existieren noch nicht.

- [ ] **Step 4: `UpdateActivityRequest`, `updateActivity` und `deleteActivity` implementieren**

In `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`, nach den bestehenden Imports `data class UpdateActivityRequest` ergänzen und die beiden neuen Endpunkte an das Ende der Klasse (vor der schließenden `}`) einfügen:

```kotlin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
```

(diese drei Imports zu den bestehenden Imports am Dateianfang hinzufügen)

```kotlin
data class UpdateActivityRequest(
    val name: String,
    val description: String? = null,
    val addressText: String? = null,
    val category: String? = null,
    val dateTime: LocalDateTime? = null,
)
```

(diese Data Class auf Package-Ebene, wie `Marker` in `Marker.kt` — direkt oberhalb oder unterhalb der `MainController`-Klasse in derselben Datei)

```kotlin
    @PutMapping("/activities/{id}")
    fun updateActivity(
        @PathVariable id: Long,
        @RequestBody req: UpdateActivityRequest,
        authentication: Authentication
    ): ResponseEntity<*> {
        val activity = repository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
        val user = userRepository.findByEmail(authentication.name)
        if (activity.createdBy?.id != user?.id) {
            return ResponseEntity.status(403).build<Any>()
        }

        activity.name = req.name
        activity.description = req.description
        activity.category = req.category
        if (req.dateTime != null) {
            activity.dateTime = req.dateTime
        }

        val addressChanged = req.addressText != activity.addressText
        if (addressChanged) {
            activity.addressText = req.addressText
            if (!req.addressText.isNullOrBlank()) {
                val coords = geocodingService.geocode(req.addressText)
                if (coords != null) {
                    activity.latitude = coords.first
                    activity.longitude = coords.second
                }
            } else {
                activity.latitude = null
                activity.longitude = null
            }
        }

        return ResponseEntity.ok(repository.save(activity))
    }

    @DeleteMapping("/activities/{id}")
    fun deleteActivity(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val activity = repository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val user = userRepository.findByEmail(authentication.name)
        if (activity.createdBy?.id != user?.id) {
            return ResponseEntity.status(403).build()
        }
        activityRatingRepository.deleteAll(activityRatingRepository.findByActivity(activity))
        repository.delete(activity)
        return ResponseEntity.noContent().build()
    }
```

- [ ] **Step 5: Tests laufen lassen, Erfolg bestätigen**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerEditActivityTest" --tests "com.example.VoloMap.server.MainControllerDeleteActivityTest"`
Expected: `BUILD SUCCESSFUL`, alle Tests grün.

- [ ] **Step 6: Fehlschlagende Security-Integrationstests schreiben**

Create `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditDeleteSecurityTest.kt`:

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class MainControllerEditDeleteSecurityTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var activityRatingRepository: ActivityRatingRepository

    @BeforeEach
    fun cleanUp() {
        activityRatingRepository.deleteAll()
        activityRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun registerAndSession(email: String, role: String): MockHttpSession {
        val result = mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"geheim123","name":"Test","role":"$role"}""")
        ).andReturn()
        return result.request.session as MockHttpSession
    }

    private fun createActivity(session: MockHttpSession, name: String): Long {
        val result = mockMvc.perform(
            post("/add")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name"}""")
        ).andReturn()
        val body = result.response.contentAsString
        return Regex(""""id":(\d+)""").find(body)!!.groupValues[1].toLong()
    }

    @Test
    fun `unauthenticated PUT and DELETE are rejected`() {
        val session = registerAndSession("owner1@example.com", "ANBIETER")
        val id = createActivity(session, "Testaktion")

        mockMvc.perform(
            put("/activities/$id").contentType(MediaType.APPLICATION_JSON).content("""{"name":"Hack"}""")
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(delete("/activities/$id")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `USER role cannot edit or delete`() {
        val ownerSession = registerAndSession("owner2@example.com", "ANBIETER")
        val id = createActivity(ownerSession, "Testaktion")
        val userSession = registerAndSession("user1@example.com", "USER")

        mockMvc.perform(
            put("/activities/$id").session(userSession).contentType(MediaType.APPLICATION_JSON).content("""{"name":"Hack"}""")
        ).andExpect(status().isForbidden)

        mockMvc.perform(delete("/activities/$id").session(userSession)).andExpect(status().isForbidden)
    }

    @Test
    fun `a different ANBIETER cannot edit or delete someone else's activity`() {
        val ownerSession = registerAndSession("owner3@example.com", "ANBIETER")
        val id = createActivity(ownerSession, "Testaktion")
        val otherSession = registerAndSession("other1@example.com", "ANBIETER")

        mockMvc.perform(
            put("/activities/$id").session(otherSession).contentType(MediaType.APPLICATION_JSON).content("""{"name":"Hack"}""")
        ).andExpect(status().isForbidden)

        mockMvc.perform(delete("/activities/$id").session(otherSession)).andExpect(status().isForbidden)
    }

    @Test
    fun `owner can edit their own activity`() {
        val session = registerAndSession("owner4@example.com", "ANBIETER")
        val id = createActivity(session, "Alter Name")

        mockMvc.perform(
            put("/activities/$id")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Neuer Name"}""")
        ).andExpect(status().isOk)

        assertEquals("Neuer Name", activityRepository.findById(id).get().name)
    }

    @Test
    fun `owner can delete their own activity even with existing ratings`() {
        val ownerSession = registerAndSession("owner5@example.com", "ANBIETER")
        val id = createActivity(ownerSession, "Zu löschen")
        val raterSession = registerAndSession("rater1@example.com", "USER")

        mockMvc.perform(
            post("/activities/$id/ratings")
                .session(raterSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":5}""")
        ).andExpect(status().isOk)

        mockMvc.perform(delete("/activities/$id").session(ownerSession))
            .andExpect(status().isNoContent)

        assertEquals(0, activityRepository.count())
        assertEquals(0, activityRatingRepository.count())
    }
}
```

- [ ] **Step 7: `SecurityConfig` um Autorisierungsregeln ergänzen**

In `backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt`, in der `authorizeHttpRequests`-Konfiguration (nach der bestehenden Zeile für `POST "/add"`) ergänzen:

```kotlin
                it.requestMatchers(HttpMethod.POST, "/add").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.PUT, "/activities/*").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.DELETE, "/activities/*").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.POST, "/activities/*/ratings", "/providers/*/ratings").hasRole("USER")
```

(die neuen zwei Zeilen zwischen den beiden bestehenden Zeilen einfügen — `/activities/*` matcht genau ein Pfadsegment nach `/activities/`, also `/activities/5`, nicht `/activities/5/ratings`, daher keine Überschneidung mit der bestehenden Ratings-Regel)

- [ ] **Step 8: Tests laufen lassen, Erfolg bestätigen**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerEditDeleteSecurityTest"`
Expected: `BUILD SUCCESSFUL`, alle Tests grün.

- [ ] **Step 9: Volle Backend-Testsuite laufen lassen**

Run: `cd backend && ./gradlew.bat test`
Expected: `BUILD SUCCESSFUL` — keine Regression in den bestehenden Tests.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditActivityTest.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerDeleteActivityTest.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditDeleteSecurityTest.kt
git commit -m "feat: add PUT/DELETE /activities/{id} with ownership checks and rating cascade"
```

---

### Task 3: Frontend — `EditActivityModal.svelte` und geteilte `deleteActivity()`-Hilfsfunktion

**Context:** `EditActivityModal` ist strukturell wie `RatingModal.svelte` (Backdrop + zentriertes Modal), mit denselben Feldern wie `AddActivity.svelte`, aber vorausgefüllt. Löschen ist keine eigene Modal-Komponente, sondern eine kleine geteilte Funktion (`confirm()` + `DELETE`-Request), die beide Konsumenten (Task 4, Task 5) direkt aufrufen.

**Files:**
- Modify: `frontend/src/auth.ts`
- Create: `frontend/src/lib/activityActions.ts`
- Create: `frontend/src/lib/EditActivityModal.svelte`

**Interfaces:**
- Consumes: `fetchWithSessionCheck` (aus `../auth`, bereits vorhanden).
- Produces: `AuthUser` bekommt `id: number`. `deleteActivity(id: number): Promise<boolean>` — zeigt `confirm()`, macht bei Bestätigung ein `DELETE /activities/{id}`, gibt `true` bei Erfolg zurück (Aufrufer soll danach `refresh` dispatchen), sonst `false` (Nutzer hat abgebrochen oder Request ist fehlgeschlagen, in beiden Fällen nichts weiter zu tun). `EditActivityModal.svelte` — Props: `marker: { id: number; name: string; description: string; address: string; category: string; dateTime: string }`. Events: `close` (Abbrechen/Schließen), `saved` (nach erfolgreichem Speichern — Aufrufer soll danach `refresh` dispatchen und das Modal schließen).

- [ ] **Step 1: `AuthUser`-Typ um `id` ergänzen**

In `frontend/src/auth.ts`, Zeile 5-9:

```typescript
export interface AuthUser {
    id: number;
    email: string;
    name: string;
    role: Role;
}
```

- [ ] **Step 2: `activityActions.ts` anlegen**

Create `frontend/src/lib/activityActions.ts`:

```typescript
import { fetchWithSessionCheck } from "../auth";

export async function deleteActivity(id: number): Promise<boolean> {
    if (!confirm("Aktivität wirklich löschen? Das entfernt auch alle Bewertungen dazu.")) {
        return false;
    }
    try {
        const res = await fetchWithSessionCheck(`http://localhost:8080/activities/${id}`, {
            method: "DELETE",
            credentials: "include",
        });
        if (res.status === 404) {
            // Already gone (e.g. deleted from another tab) — same end state as a
            // successful delete, so the caller should refresh without an alert.
            return true;
        }
        if (!res.ok) {
            alert("Löschen fehlgeschlagen. Bitte versuche es erneut.");
            return false;
        }
        return true;
    } catch (e) {
        alert("Server nicht erreichbar. Bitte versuche es später erneut.");
        return false;
    }
}
```

- [ ] **Step 3: `EditActivityModal.svelte` anlegen**

Create `frontend/src/lib/EditActivityModal.svelte`:

```svelte
<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import { fetchWithSessionCheck } from "../auth";

    export let marker: {
        id: number;
        name: string;
        description: string;
        address: string;
        category: string;
        dateTime: string;
    };

    const dispatch = createEventDispatcher<{ close: void; saved: void }>();

    let name = marker.name;
    let description = marker.description;
    let addressText = marker.address;
    let category = marker.category;
    let dateTime = marker.dateTime ? marker.dateTime.slice(0, 16) : "";

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

        try {
            const res = await fetchWithSessionCheck(`http://localhost:8080/activities/${marker.id}`, {
                method: "PUT",
                credentials: "include",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    name,
                    description: description || null,
                    addressText: addressText || null,
                    category: category || null,
                    dateTime: dateTime ? dateTime + ":00" : undefined,
                }),
            });

            if (!res.ok) {
                statusMessage = "Fehler beim Speichern. Bitte versuche es erneut.";
                statusIsWarning = true;
                return;
            }

            const saved = await res.json();
            if (addressText && (saved.latitude == null || saved.longitude == null)) {
                statusMessage = "Gespeichert — die Adresse konnte aber nicht gefunden werden, die Position auf der Karte wurde nicht aktualisiert.";
                statusIsWarning = true;
            } else {
                statusMessage = "Aktivität wurde aktualisiert.";
                statusIsWarning = false;
            }
            dispatch("saved");
        } catch (e) {
            statusMessage = "Server nicht erreichbar. Bitte versuche es später erneut.";
            statusIsWarning = true;
        } finally {
            submitting = false;
        }
    }
</script>

<button class="backdrop" aria-label="Schließen" on:click={() => dispatch("close")}></button>
<div class="modal">
    <div class="modal-header">
        <h3>Aktivität bearbeiten</h3>
        <button class="close" on:click={() => dispatch("close")} aria-label="Schließen">×</button>
    </div>

    <form on:submit|preventDefault={handleSubmit}>
        <label>
            Name *
            <input type="text" bind:value={name} required />
        </label>

        <label>
            Beschreibung
            <textarea bind:value={description}></textarea>
        </label>

        <label>
            Adresse
            <input type="text" bind:value={addressText} placeholder="Straße, Hausnummer, Stadt" />
        </label>

        <label>
            Kategorie
            <input type="text" bind:value={category} />
        </label>

        <label>
            Datum/Uhrzeit
            <input type="datetime-local" bind:value={dateTime} />
        </label>

        <button type="submit" disabled={submitting}>
            {submitting ? "Speichert…" : "Speichern"}
        </button>

        {#if statusMessage}
            <p class:warning={statusIsWarning}>{statusMessage}</p>
        {/if}
    </form>
</div>

<style>
    .backdrop {
        position: fixed;
        inset: 0;
        z-index: 2000;
        background: rgba(42, 42, 34, 0.4);
        border: none;
        padding: 0;
        cursor: default;
    }

    .modal {
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        z-index: 2001;
        background: var(--color-surface);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-panel);
        padding: 20px;
        width: min(420px, 90vw);
        max-height: 80vh;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: 12px;
    }

    .modal-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
    }

    .modal-header h3 {
        font-size: 1.05rem;
    }

    .close {
        background: none;
        border: none;
        font-size: 1.3rem;
        line-height: 1;
        cursor: pointer;
        color: var(--color-text-muted);
        padding: 0;
    }

    form {
        display: flex;
        flex-direction: column;
        gap: 12px;
    }

    label {
        display: flex;
        flex-direction: column;
        gap: 4px;
        font-size: 0.9rem;
        color: var(--color-text);
    }

    input,
    textarea {
        font-family: inherit;
        font-size: 0.9rem;
        padding: 8px 10px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        color: var(--color-text);
    }

    input:focus,
    textarea:focus {
        outline: none;
        border-color: var(--color-primary);
    }

    button[type="submit"] {
        align-self: flex-start;
    }

    button[type="submit"]:disabled {
        opacity: 0.6;
        cursor: default;
    }

    p.warning {
        color: var(--color-error);
        font-size: 0.85rem;
        margin: 0;
    }
</style>
```

- [ ] **Step 4: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler (die Datei wird von keiner Komponente importiert, bis Task 4/5).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/auth.ts frontend/src/lib/activityActions.ts frontend/src/lib/EditActivityModal.svelte
git commit -m "feat: add EditActivityModal and shared deleteActivity helper"
```

---

### Task 4: `PinDetailPanel.svelte` — Bearbeiten/Löschen für den Eigentümer

**Files:**
- Modify: `frontend/src/lib/PinDetailPanel.svelte`

**Interfaces:**
- Consumes: `EditActivityModal` (Task 3) mit `marker`-Prop und `close`/`saved`-Events; `deleteActivity` (Task 3) aus `./activityActions`; `currentUser`-Store (aus `../auth`, jetzt mit `id`).
- Produces: keine neuen Exports. Nutzt das bestehende `refresh`-Event erneut (kein neues Event nötig) — sowohl nach dem Speichern einer Bearbeitung als auch nach dem Löschen wird `dispatch("refresh")` gefeuert, genau wie bisher nach einer neuen Bewertung. `Map.svelte` reagiert bereits mit `on:refresh={fetchMarkers}`; verschwindet die aktuell angezeigte Aktivität aus `markers` (weil gelöscht), schließt sich das Panel automatisch über die bestehende reaktive `selectedMarker`-Ableitung — keine neue Logik in `Map.svelte` nötig.

- [ ] **Step 1: Imports und Ownership-Ableitung ergänzen**

In `frontend/src/lib/PinDetailPanel.svelte`, im `<script>`-Block nach den bestehenden Imports:

```typescript
    import EditActivityModal from "./EditActivityModal.svelte";
    import { deleteActivity } from "./activityActions";
    import { currentUser } from "../auth";
```

Nach der bestehenden `openRating`-Deklaration:

```typescript
    $: isOwner = $currentUser?.id === marker.providerId;
    let editing = false;

    async function handleDelete() {
        if (await deleteActivity(marker.id)) {
            dispatch("refresh");
        }
    }
```

- [ ] **Step 2: Buttons und Modal ins Template einfügen**

Im `.panel-header`-Div, nach dem Kategorie-Tag und vor dem bestehenden Schließen-Button (`×`), die neuen Buttons einfügen — der bestehende `.panel-header`-Block sieht danach so aus:

```svelte
    <div class="panel-header">
        {#if marker.category}
            <span
                class="tag"
                style="background:{categoryColor(marker.category).bg}; color:{categoryColor(marker.category).text};"
            >{marker.category}</span>
        {/if}
        {#if isOwner}
            <button class="edit-link" on:click={() => (editing = true)}>Bearbeiten</button>
            <button class="edit-link" on:click={handleDelete}>Löschen</button>
        {/if}
        <button class="close" on:click={() => dispatch("close")} aria-label="Schließen">×</button>
    </div>
```

Am Ende der Datei, nach dem bestehenden `{#if openRating}...{/if}`-Block, das Edit-Modal ergänzen:

```svelte
{#if editing}
    <EditActivityModal
        marker={{ id: marker.id, name: marker.name, description: marker.description, address: marker.address, category: marker.category, dateTime: marker.dateTime }}
        on:close={() => (editing = false)}
        on:saved={() => { editing = false; dispatch("refresh"); }}
    />
{/if}
```

Im `<style>`-Block, nach der bestehenden `.close`-Regel, die neue Button-Klasse ergänzen:

```css
    .edit-link {
        background: none;
        border: none;
        font-size: 0.75rem;
        color: var(--color-text-muted);
        cursor: pointer;
        padding: 2px 6px;
        text-decoration: underline;
    }

    .edit-link:hover {
        color: var(--color-primary);
    }
```

- [ ] **Step 3: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler in `PinDetailPanel.svelte`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/PinDetailPanel.svelte
git commit -m "feat: add edit/delete buttons to PinDetailPanel for the owning Anbieter"
```

---

### Task 5: `VolunteerList.svelte` — Bearbeiten/Löschen für den Eigentümer

**Files:**
- Modify: `frontend/src/lib/VolunteerList.svelte`

**Interfaces:**
- Consumes: `EditActivityModal`/`deleteActivity` (Task 3), `currentUser`-Store.
- Produces: keine neuen Events — nutzt das bestehende `refresh`-Event erneut, exakt wie Task 4.

- [ ] **Step 1: Imports, `description`-Feld im `markers`-Typ, Ownership-State**

In `frontend/src/lib/VolunteerList.svelte`, die Imports ergänzen:

```typescript
    import EditActivityModal from "./EditActivityModal.svelte";
    import { deleteActivity } from "./activityActions";
    import { currentUser } from "../auth";
```

Den `markers`-Prop-Typ um das bisher fehlende `description`-Feld ergänzen (wird von `EditActivityModal` zum Vorausfüllen benötigt) — nach `category: string;` einfügen:

```typescript
        category: string;
        description: string;
        dateTime: string;
```

Nach der bestehenden `openRating`-Deklaration:

```typescript
    let editingMarker: (typeof markers)[number] | null = null;

    async function handleDelete(marker: (typeof markers)[number]) {
        if (await deleteActivity(marker.id)) {
            dispatch("refresh");
        }
    }
```

- [ ] **Step 2: Buttons im Card-Template ergänzen**

Im `.ratings`-Div, nach den beiden bestehenden Bewertungs-Buttons, die neuen Buttons ergänzen (nur wenn `$currentUser?.id === marker.providerId`):

```svelte
            <div class="ratings">
                <button class="rating-badge" on:click|stopPropagation={() => openActivityRating(marker)}>
                    {marker.activityRating != null ? `★ ${marker.activityRating.toFixed(1)} (${marker.activityRatingCount})` : "Noch keine Bewertung"}
                </button>
                {#if marker.providerId != null}
                    <button class="rating-badge" on:click|stopPropagation={() => openProviderRating(marker)}>
                        Anbieter: {marker.providerRating != null ? `★ ${marker.providerRating.toFixed(1)} (${marker.providerRatingCount})` : "Noch keine Bewertung"}
                    </button>
                {/if}
                {#if $currentUser?.id === marker.providerId}
                    <button class="edit-link" on:click|stopPropagation={() => (editingMarker = marker)}>Bearbeiten</button>
                    <button class="edit-link" on:click|stopPropagation={() => handleDelete(marker)}>Löschen</button>
                {/if}
            </div>
```

Nach dem bestehenden `{#if openRating}...{/if}`-Block am Ende der Datei, das Edit-Modal ergänzen:

```svelte
{#if editingMarker}
    <EditActivityModal
        marker={{ id: editingMarker.id, name: editingMarker.name, description: editingMarker.description, address: editingMarker.address, category: editingMarker.category, dateTime: editingMarker.dateTime }}
        on:close={() => (editingMarker = null)}
        on:saved={() => { editingMarker = null; dispatch("refresh"); }}
    />
{/if}
```

- [ ] **Step 3: Style für `.edit-link` ergänzen**

Im `<style>`-Block, nach der bestehenden `.rating-badge:hover`-Regel:

```css
    .edit-link {
        font-size: 0.75rem;
        padding: 3px 6px;
        border: none;
        background: none;
        color: var(--color-text-muted);
        cursor: pointer;
        text-decoration: underline;
    }

    .edit-link:hover {
        color: var(--color-primary);
    }
```

- [ ] **Step 4: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler in `VolunteerList.svelte`. `Map.svelte` übergibt `markers` bereits vollständig vom Backend (inkl. `description`) an `VolunteerList`, daher ist keine Änderung in `Map.svelte` nötig.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/VolunteerList.svelte
git commit -m "feat: add edit/delete buttons to VolunteerList cards for the owning Anbieter"
```

---

### Task 6: End-to-End-Verifikation

**Context:** Kompletter manueller Rundgang mit laufendem Backend und Frontend, als eingeloggter Anbieter mit einer eigenen Aktivität.

**Files:** keine (nur Verifikation)

- [ ] **Step 1: Volle Testsuiten laufen lassen**

Run: `cd backend && ./gradlew.bat test`
Expected: `BUILD SUCCESSFUL`.

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler/Warnungen gegenüber dem Stand vor diesem Plan (bekannte Altlasten wie der `FilterBar.svelte`-Fehler und die `Router.svelte`-Warnung zählen nicht als neu).

- [ ] **Step 2: Visueller Rundgang im Browser**

Mit laufendem Backend (`./gradlew.bat bootRun`) und Frontend (`npm run dev`):

- Als Anbieter einloggen, eine eigene Aktivität anlegen (über `/add`)
- Auf der Karte (≥1024px, `PinDetailPanel`): eigene Aktivität anklicken → "Bearbeiten"/"Löschen"-Buttons sichtbar; fremde/gescrapte Aktivität anklicken → Buttons nicht sichtbar
- In der Bottom-Sheet-Liste (`VolunteerList`, auch <1024px testen): dieselbe Sichtbarkeitsregel gilt pro Karte
- "Bearbeiten" öffnet das Modal mit vorausgefüllten aktuellen Werten; Name ändern und speichern → Modal zeigt Erfolgsmeldung, nach Schließen zeigt Karte/Panel den neuen Namen
- Adresse ändern auf eine gültige neue Adresse → Pin bewegt sich auf der Karte zur neuen Position
- Adresse ändern auf eine unauffindbare Zeichenkette → Warnhinweis erscheint, Pin bleibt an der alten Position
- "Löschen" klicken → Bestätigungsdialog erscheint; abbrechen → nichts passiert; bestätigen → Aktivität verschwindet von Karte und Liste, ein offenes `PinDetailPanel` für diese Aktivität schließt sich automatisch
- Eine Aktivität mit vorhandener Bewertung löschen → funktioniert ohne Fehler (Bewertung wird mitgelöscht, im Backend-Log keine Fremdschlüssel-Fehler)
- Als eingeloggter `USER` oder als fremder Anbieter: keine Bearbeiten/Löschen-Buttons bei Aktivitäten sichtbar, die einem selbst nicht gehören

- [ ] **Step 3: Report**

Zusammenfassung Pass/Fail für Schritt 1-2. Wenn alles passt: Edit/Delete-Aktivitäten ist fertig.
