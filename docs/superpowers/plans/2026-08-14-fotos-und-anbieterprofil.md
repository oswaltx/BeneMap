# Fotos-und-Anbieterprofil Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Anbieter können ihren Aktivitäten eine Foto-Galerie hinzufügen und ein eigenes Profil mit Profilbild und Website pflegen; Nutzer sehen die Galerie in der Aktivitäts-Detailansicht.

**Architecture:** Backend speichert Foto-URLs als zeilenweise getrennten Text in einer neuen `photoUrls`-Spalte auf `VolunteerActivity` (kein Datei-Upload, kein neues Entity) und zwei neue Felder `photoUrl`/`websiteUrl` auf `User`. `Marker` (das öffentliche API-DTO) bekommt geparste Felder dafür. Neuer Endpunkt `PUT /auth/me` für das Anbieter-Selbstprofil. Frontend: neues Textarea-Feld in den bestehenden Aktivitäts-Formularen, eine neue "Mein Profil"-Seite, und eine Hero-Bild-plus-Streifen-Galerie in `PinDetailPanel.svelte`.

**Tech Stack:** Kotlin/Spring Boot 4.0.3 Backend (JUnit 5, Mockito-Kotlin für Unit-Tests, `@SpringBootTest`+`MockMvc` für Integrationstests). Svelte 5 Legacy-Stil Frontend (`<script lang="ts">`, `createEventDispatcher`, plain `let`/`$:`).

## Global Constraints

- Nur URL-Eingabe, kein Datei-Upload — Anbieter fügt Links zu bereits gehosteten Bildern ein.
- Maximal 10 Foto-URLs pro Aktivität, serverseitig gekappt (überzählige Zeilen werden stillschweigend verworfen, kein Fehler).
- Keine Bild-Validierung — ungültige URLs zeigen das Browser-Standard-Platzhalterbild.
- Galerie nur in `PinDetailPanel.svelte` — `VolunteerList`-Karten bleiben unverändert kompakt/text-only.
- `PUT /auth/me` ändert ausschließlich `photoUrl`/`websiteUrl` — niemals `email`/`name`/`role` (eigenes schmales Request-DTO, nicht der volle User).
- "Mein Profil"-Seite: neue Route, dauerhaft bearbeitbar (nicht nur bei Registrierung).

---

### Task 1: Backend — Aktivitäts-Fotos (Datenmodell, Parsing, Add/Update/Markers)

**Context:** `VolunteerActivity` bekommt eine neue `photoUrls`-Spalte (roher, zeilenweise getrennter Text — dieselbe Konvention wie das Frontend-Textarea sie später direkt durchreicht). Zwei kleine reine Funktionen übernehmen Parsen (für die Ausgabe in `Marker`) und Normalisieren+Kappen (für das Speichern bei `/add` und `PUT /activities/{id}`).

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`
- Modify: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt`
- Modify: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditActivityTest.kt`
- Modify: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt`

**Interfaces:**
- Produces: `parsePhotoUrls(raw: String?): List<String>` und `normalizePhotoUrls(raw: String?): String?` (private Top-Level-Funktionen in `MainController.kt`, max. 10 Einträge). `VolunteerActivity.photoUrls: String?`. `Marker.photoUrls: List<String>`. `UpdateActivityRequest.photoUrls: String? = null`. `/add` erwartet `photoUrls` direkt als Feld des `VolunteerActivity`-Request-Bodys (roher Text, keine Liste) — Task 3 (Frontend) sendet das Textarea genau so.

- [ ] **Step 1: Fehlschlagende Tests für Parsing/Kappung schreiben**

In `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt`, am Ende der Klasse (vor der letzten `}`) ergänzen:

```kotlin
    @Test
    fun `normalizes and caps photo URLs when adding an activity`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val rawUrls = (1..12).joinToString("\n") { "https://example.com/photo$it.jpg" }
        val activity = VolunteerActivity(name = "Test", latitude = 1.0, longitude = 2.0, photoUrls = rawUrls)

        val result = controller.addActivity(activity, authenticationFor(provider.email))

        val storedLines = result.body?.photoUrls?.lines() ?: emptyList()
        assertEquals(10, storedLines.size)
        assertEquals("https://example.com/photo1.jpg", storedLines.first())
    }

    @Test
    fun `blank photo URLs field is stored as null`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val activity = VolunteerActivity(name = "Test", latitude = 1.0, longitude = 2.0, photoUrls = "   \n  \n")

        val result = controller.addActivity(activity, authenticationFor(provider.email))

        assertNull(result.body?.photoUrls)
    }
```

In `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditActivityTest.kt`, am Ende der Klasse (vor der letzten `}`) ergänzen:

```kotlin
    @Test
    fun `updates and caps photo URLs on edit`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val existing = VolunteerActivity(id = 5, name = "Alt", createdBy = owner)
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val rawUrls = (1..12).joinToString("\n") { "https://example.com/photo$it.jpg" }
        val req = UpdateActivityRequest(name = "Alt", photoUrls = rawUrls)

        val result = controller.updateActivity(5, req, authenticationFor(owner.email))

        val storedLines = (result.body as UpdateActivityResponse).activity.photoUrls?.lines() ?: emptyList()
        assertEquals(10, storedLines.size)
    }

    @Test
    fun `omitting photo URLs on edit clears them (full-replace semantics)`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val existing = VolunteerActivity(
            id = 5, name = "Alt", createdBy = owner,
            photoUrls = "https://example.com/old.jpg"
        )
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock())
        val req = UpdateActivityRequest(name = "Alt")

        val result = controller.updateActivity(5, req, authenticationFor(owner.email))

        assertNull((result.body as UpdateActivityResponse).activity.photoUrls)
    }
```

In `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt`, am Ende der Klasse (vor der letzten `}`) ergänzen:

```kotlin
    @Test
    fun `parses stored photo URLs into a list, trimming and dropping blank lines`() {
        val repository = mock<VolunteerActivityRepository>()
        val withPhotos = activity(
            name = "Mit Fotos",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        ).also { it.photoUrls = "https://example.com/a.jpg\n  \nhttps://example.com/b.jpg  \n" }
        whenever(repository.findAll()).thenReturn(listOf(withPhotos))

        val controller = MainController(repository, mock(), mock(), mock(), mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals(listOf("https://example.com/a.jpg", "https://example.com/b.jpg"), result[0].photoUrls)
    }

    @Test
    fun `photo URLs list is empty when the activity has none`() {
        val repository = mock<VolunteerActivityRepository>()
        val noPhotos = activity(
            name = "Ohne Fotos",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        whenever(repository.findAll()).thenReturn(listOf(noPhotos))

        val controller = MainController(repository, mock(), mock(), mock(), mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals(emptyList<String>(), result[0].photoUrls)
    }
```

In `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditActivityTest.kt` fehlt aktuell der `assertNull`-Import (wurde in einem früheren Feature als ungenutzt entfernt) — der neue Test `omitting photo URLs on edit clears them` in Schritt 1 oben braucht ihn. Import-Zeile ergänzen:

```kotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
```

(vor der bestehenden `import org.junit.jupiter.api.Test`-Zeile)

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerAddActivityTest" --tests "com.example.VoloMap.server.MainControllerEditActivityTest" --tests "com.example.VoloMap.server.MainControllerMarkersTest"`
Expected: FAIL mit Kompilierfehlern — `photoUrls` existiert an keiner der referenzierten Stellen.

- [ ] **Step 3: `VolunteerActivity.photoUrls` ergänzen**

In `backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt`, nach dem bestehenden `sourceUrl`-Feld (Zeile 35) einfügen:

```kotlin
    // Zeilenweise getrennte Liste von Bild-URLs — kein Datei-Upload, Anbieter fügt
    // Links zu bereits gehosteten Bildern ein. Roher Text, wird von MainController
    // beim Speichern normalisiert/gekappt und beim Auslesen in eine Liste geparst.
    @Column(columnDefinition = "TEXT")
    var photoUrls: String? = null,

```

(direkt nach der `sourceUrl`-Deklaration, vor `var category: String? = null,`)

- [ ] **Step 4: `Marker.photoUrls` ergänzen**

In `backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt`, nach `val description: String,` einfügen:

```kotlin
    val photoUrls: List<String>,
```

Die Datei sieht danach so aus:

```kotlin
package com.example.VoloMap.server

import java.time.LocalDateTime

data class Marker(
    val id: Long,
    val lat: Double,
    val lng: Double,
    val name: String,
    val address: String,
    val category: String,
    val description: String,
    val photoUrls: List<String>,
    val dateTime: LocalDateTime?,
    val activityRating: Double?,
    val activityRatingCount: Int,
    val providerId: Long?,
    val providerName: String?,
    val providerRating: Double?,
    val providerRatingCount: Int,
)
```

- [ ] **Step 5: Parsing/Normalisierungs-Funktionen und Verdrahtung in `MainController.kt`**

Am Ende von `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`, nach der bestehenden `UpdateActivityRequest`-Data-Class, ergänzen:

```kotlin

private const val MAX_PHOTO_URLS = 10

private fun parsePhotoUrls(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_PHOTO_URLS)
}

private fun normalizePhotoUrls(raw: String?): String? {
    val parsed = parsePhotoUrls(raw)
    return if (parsed.isEmpty()) null else parsed.joinToString("\n")
}
```

In der `markers()`-Funktion, in der `Marker(...)`-Konstruktion, nach `description = activity.description ?: "",` einfügen:

```kotlin
                    photoUrls = parsePhotoUrls(activity.photoUrls),
```

In `addActivity()`, direkt nach `activity.createdBy = userRepository.findByEmail(authentication.name)` einfügen:

```kotlin
        activity.photoUrls = normalizePhotoUrls(activity.photoUrls)
```

In `UpdateActivityRequest` (am Ende der Datei) das Feld `photoUrls` ergänzen:

```kotlin
data class UpdateActivityRequest(
    val name: String,
    val description: String? = null,
    val addressText: String? = null,
    val category: String? = null,
    val dateTime: LocalDateTime? = null,
    val photoUrls: String? = null,
)
```

In `updateActivity()`, nach `activity.category = req.category` einfügen:

```kotlin
        activity.photoUrls = normalizePhotoUrls(req.photoUrls)
```

- [ ] **Step 6: Tests laufen lassen, Erfolg bestätigen**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerAddActivityTest" --tests "com.example.VoloMap.server.MainControllerEditActivityTest" --tests "com.example.VoloMap.server.MainControllerMarkersTest"`
Expected: `BUILD SUCCESSFUL`, alle Tests grün.

- [ ] **Step 7: Volle Backend-Testsuite laufen lassen**

Run: `cd backend && ./gradlew.bat test`
Expected: `BUILD SUCCESSFUL` — keine Regression.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditActivityTest.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt
git commit -m "feat: add photo URLs to activities (add/edit/markers)"
```

---

### Task 2: Backend — Anbieter-Profil (photoUrl/websiteUrl, PUT /auth/me, Marker-Provider-Felder)

**Context:** `User` bekommt zwei neue, frei bearbeitbare Felder. Ein neuer, schmaler Endpunkt `PUT /auth/me` ändert nur diese beiden Felder des eingeloggten Users — nie `email`/`name`/`role`. `Marker` bekommt die entsprechenden Provider-Felder, damit das Frontend sie ohne Zusatz-Request anzeigen kann.

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/User.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/AuthController.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`
- Modify: `backend/src/test/kotlin/com/example/VoloMap/server/AuthControllerTest.kt`
- Modify: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt`

**Interfaces:**
- Consumes: keine neuen aus Task 1.
- Produces: `User.photoUrl: String?`, `User.websiteUrl: String?`. `UserResponse` bekommt `photoUrl: String? = null, websiteUrl: String? = null` als letzte zwei Felder. `PUT /auth/me` mit Body `UpdateProfileRequest(photoUrl: String? = null, websiteUrl: String? = null)`, Antwort `UserResponse`. `Marker.providerPhotoUrl: String?`, `Marker.providerWebsiteUrl: String?`. Task 5 (Frontend "Mein Profil") ruft `PUT /auth/me` mit genau diesem Body-Schema auf; Task 4 (PinDetailPanel-Galerie) liest `marker.providerPhotoUrl`/`marker.providerWebsiteUrl`.

- [ ] **Step 1: Fehlschlagende Tests schreiben**

In `backend/src/test/kotlin/com/example/VoloMap/server/AuthControllerTest.kt`, am Ende der Klasse (vor der letzten `}`) ergänzen:

```kotlin
    @Test
    fun `owner can update their own profile photo and website`() {
        val register = mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"dana@example.com","password":"geheim123","name":"Dana","role":"ANBIETER"}""")
        ).andReturn()
        val session = register.request.session as MockHttpSession

        mockMvc.perform(
            put("/auth/me")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"photoUrl":"https://example.com/dana.jpg","websiteUrl":"https://dana-vereint.de"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.photoUrl").value("https://example.com/dana.jpg"))
            .andExpect(jsonPath("$.websiteUrl").value("https://dana-vereint.de"))
            .andExpect(jsonPath("$.email").value("dana@example.com"))
            .andExpect(jsonPath("$.role").value("ANBIETER"))
    }

    @Test
    fun `updating profile without a session is unauthorized`() {
        mockMvc.perform(
            put("/auth/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"photoUrl":"https://example.com/x.jpg"}""")
        ).andExpect(status().isUnauthorized)
    }
```

In `backend/src/test/kotlin/com/example/VoloMap/server/AuthControllerTest.kt` den Import-Block (Zeile 11-14) um `put` ergänzen:

```kotlin
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
```

In `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt`, am Ende der Klasse ergänzen:

```kotlin
    @Test
    fun `includes provider photo and website when set`() {
        val repository = mock<VolunteerActivityRepository>()
        val activityRatingRepository = mock<ActivityRatingRepository>()
        val providerRatingRepository = mock<ProviderRatingRepository>()
        val provider = User(
            id = 7, email = "anbieter@example.com", passwordHash = "x", name = "Anbieter Anna",
            role = Role.ANBIETER, photoUrl = "https://example.com/anna.jpg", websiteUrl = "https://anna-verein.de"
        )
        val rated = activity(
            name = "Bewertete Aktion",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        ).also { it.createdBy = provider }
        whenever(repository.findAll()).thenReturn(listOf(rated))
        whenever(activityRatingRepository.findAll()).thenReturn(emptyList())
        whenever(providerRatingRepository.findAll()).thenReturn(emptyList())

        val controller = MainController(repository, mock(), mock(), activityRatingRepository, providerRatingRepository)
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals("https://example.com/anna.jpg", result[0].providerPhotoUrl)
        assertEquals("https://anna-verein.de", result[0].providerWebsiteUrl)
    }

    @Test
    fun `provider photo and website are null without an owner`() {
        val repository = mock<VolunteerActivityRepository>()
        val activityRatingRepository = mock<ActivityRatingRepository>()
        val providerRatingRepository = mock<ProviderRatingRepository>()
        val unrated = activity(
            name = "Unbewertete Aktion",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        whenever(repository.findAll()).thenReturn(listOf(unrated))
        whenever(activityRatingRepository.findAll()).thenReturn(emptyList())
        whenever(providerRatingRepository.findAll()).thenReturn(emptyList())

        val controller = MainController(repository, mock(), mock(), activityRatingRepository, providerRatingRepository)
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertNull(result[0].providerPhotoUrl)
        assertNull(result[0].providerWebsiteUrl)
    }
```

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.AuthControllerTest" --tests "com.example.VoloMap.server.MainControllerMarkersTest"`
Expected: FAIL mit Kompilierfehlern.

- [ ] **Step 3: `User.photoUrl`/`websiteUrl` ergänzen**

In `backend/src/main/kotlin/com/example/VoloMap/server/User.kt`, nach `var role: Role,` einfügen:

```kotlin
    var photoUrl: String? = null,
    var websiteUrl: String? = null,

```

- [ ] **Step 4: `Marker.providerPhotoUrl`/`providerWebsiteUrl` ergänzen**

In `backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt`, nach `val providerName: String?,` einfügen:

```kotlin
    val providerPhotoUrl: String?,
    val providerWebsiteUrl: String?,
```

Die Datei sieht danach so aus:

```kotlin
package com.example.VoloMap.server

import java.time.LocalDateTime

data class Marker(
    val id: Long,
    val lat: Double,
    val lng: Double,
    val name: String,
    val address: String,
    val category: String,
    val description: String,
    val photoUrls: List<String>,
    val dateTime: LocalDateTime?,
    val activityRating: Double?,
    val activityRatingCount: Int,
    val providerId: Long?,
    val providerName: String?,
    val providerPhotoUrl: String?,
    val providerWebsiteUrl: String?,
    val providerRating: Double?,
    val providerRatingCount: Int,
)
```

- [ ] **Step 5: `markers()` um die neuen Provider-Felder ergänzen**

In `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`, in der `Marker(...)`-Konstruktion innerhalb von `markers()`, nach `providerName = activity.createdBy?.name,` einfügen:

```kotlin
                    providerPhotoUrl = activity.createdBy?.photoUrl,
                    providerWebsiteUrl = activity.createdBy?.websiteUrl,
```

- [ ] **Step 6: `UserResponse` und `PUT /auth/me` in `AuthController.kt`**

In `backend/src/main/kotlin/com/example/VoloMap/server/AuthController.kt`, Zeile 31, `UserResponse` ersetzen durch:

```kotlin
data class UserResponse(
    val id: Long,
    val email: String,
    val name: String,
    val role: Role,
    val photoUrl: String? = null,
    val websiteUrl: String? = null,
)
data class UpdateProfileRequest(val photoUrl: String? = null, val websiteUrl: String? = null)
```

Alle drei bestehenden `UserResponse(user.id, user.email, user.name, user.role)`-Aufrufe (in `register`, `login`, `me`) ersetzen durch:

```kotlin
        return ResponseEntity.ok(UserResponse(user.id, user.email, user.name, user.role, user.photoUrl, user.websiteUrl))
```

Neuen Endpunkt `updateProfile` nach `me` (vor `establishSession`) einfügen:

```kotlin
    @PutMapping("/me")
    fun updateProfile(
        @RequestBody req: UpdateProfileRequest,
        authentication: Authentication
    ): ResponseEntity<UserResponse> {
        val user = userRepository.findByEmail(authentication.name)!!
        user.photoUrl = req.photoUrl?.trim()?.ifBlank { null }
        user.websiteUrl = req.websiteUrl?.trim()?.ifBlank { null }
        userRepository.save(user)
        return ResponseEntity.ok(UserResponse(user.id, user.email, user.name, user.role, user.photoUrl, user.websiteUrl))
    }
```

Den Import-Block um `PutMapping` ergänzen:

```kotlin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
```

Kein Eintrag in `SecurityConfig.kt` nötig — `PUT /auth/me` fällt bereits unter die bestehende `it.anyRequest().authenticated()`-Regel (jeder eingeloggte User, unabhängig von der Rolle, darf sein eigenes Profil ändern — analog zu `GET /auth/me`, das ebenfalls nicht rollen-eingeschränkt ist).

- [ ] **Step 7: Tests laufen lassen, Erfolg bestätigen**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.AuthControllerTest" --tests "com.example.VoloMap.server.MainControllerMarkersTest"`
Expected: `BUILD SUCCESSFUL`, alle Tests grün.

- [ ] **Step 8: Volle Backend-Testsuite laufen lassen**

Run: `cd backend && ./gradlew.bat test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/User.kt backend/src/main/kotlin/com/example/VoloMap/server/AuthController.kt backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt backend/src/test/kotlin/com/example/VoloMap/server/AuthControllerTest.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt
git commit -m "feat: add provider photo/website profile fields and PUT /auth/me"
```

---

### Task 3: Frontend — Foto-URLs im Formular (Add + Edit) und Typ-Durchreichen

**Context:** Beide Formulare bekommen ein Textarea "Foto-URLs (eine pro Zeile)". Das Backend erwartet den rohen, zeilenweise getrennten Text direkt (siehe Task 1) — das Frontend muss nichts in ein Array parsen, nur den Textarea-Wert 1:1 durchreichen. `PinDetailPanel.svelte` und `VolunteerList.svelte` müssen ihren jeweiligen `marker`-Typ um `photoUrls: string[]` erweitern, damit sie das Feld beim Öffnen von `EditActivityModal` korrekt vorausfüllen können — die eigentliche Galerie-Anzeige kommt erst in Task 4.

**Files:**
- Modify: `frontend/src/lib/AddActivity.svelte`
- Modify: `frontend/src/lib/EditActivityModal.svelte`
- Modify: `frontend/src/lib/PinDetailPanel.svelte`
- Modify: `frontend/src/lib/VolunteerList.svelte`

**Interfaces:**
- Consumes: keine neuen aus Task 1/2 (reiner Frontend-Task, der bereits vorhandene Backend-Felder nutzt).
- Produces: `EditActivityModal`s `marker`-Prop bekommt `photoUrls: string[]` als zusätzliches Pflichtfeld. Task 4 baut darauf die Galerie-Anzeige.

- [ ] **Step 1: `AddActivity.svelte` — Foto-URLs-Feld**

In `frontend/src/lib/AddActivity.svelte`, nach `let dateTime = "";` einfügen:

```typescript
    let photoUrlsText = "";
```

Im `body`-Objekt des `fetch`-Aufrufs, nach `dateTime: dateTime ? dateTime + ":00" : undefined,` einfügen:

```typescript
                    photoUrls: photoUrlsText.trim() || undefined,
```

Nach dem Zurücksetzen der Felder (`dateTime = "";`) ergänzen:

```typescript
            photoUrlsText = "";
```

Im Template, nach dem `Datum/Uhrzeit`-Label-Block, vor dem `<button type="submit">`, einfügen:

```svelte
            <label>
                Foto-URLs (eine pro Zeile)
                <textarea bind:value={photoUrlsText} rows="3" placeholder={"https://...\nhttps://..."}></textarea>
            </label>
```

- [ ] **Step 2: `EditActivityModal.svelte` — Foto-URLs-Feld**

In `frontend/src/lib/EditActivityModal.svelte`, den `marker`-Prop-Typ um `photoUrls: string[];` erweitern:

```typescript
    export let marker: {
        id: number;
        name: string;
        description: string;
        address: string;
        category: string;
        dateTime: string;
        photoUrls: string[];
    };
```

Nach `let dateTime = marker.dateTime ? marker.dateTime.slice(0, 16) : "";` einfügen:

```typescript
    let photoUrlsText = marker.photoUrls.join("\n");
```

Im `body`-Objekt des `PUT`-Aufrufs, nach `dateTime: dateTime ? dateTime + ":00" : undefined,` einfügen:

```typescript
                    photoUrls: photoUrlsText.trim() || undefined,
```

Im Template, nach dem `Datum/Uhrzeit`-Label-Block, vor dem `<button type="submit">`, einfügen:

```svelte
        <label>
            Foto-URLs (eine pro Zeile)
            <textarea bind:value={photoUrlsText} rows="3" placeholder={"https://...\nhttps://..."}></textarea>
        </label>
```

- [ ] **Step 3: `PinDetailPanel.svelte` — Typ erweitern und durchreichen**

In `frontend/src/lib/PinDetailPanel.svelte`, den `marker`-Prop-Typ um `photoUrls: string[];` erweitern (nach `description: string;`):

```typescript
    export let marker: {
        id: number;
        name: string;
        address: string;
        category: string;
        description: string;
        photoUrls: string[];
        dateTime: string;
        activityRating: number | null;
        activityRatingCount: number;
        providerId: number | null;
        providerName: string | null;
        providerRating: number | null;
        providerRatingCount: number;
    };
```

Im bestehenden `EditActivityModal`-Aufruf (im `marker`-Literal) `photoUrls: marker.photoUrls` ergänzen:

```svelte
        marker={{ id: marker.id, name: marker.name, description: marker.description, address: marker.address, category: marker.category, dateTime: marker.dateTime, photoUrls: marker.photoUrls }}
```

- [ ] **Step 4: `VolunteerList.svelte` — Typ erweitern und durchreichen**

In `frontend/src/lib/VolunteerList.svelte`, den `markers`-Prop-Typ um `photoUrls: string[];` erweitern (nach `description: string;`):

```typescript
    export let markers: {
        id: number;
        name: string;
        address: string;
        category: string;
        description: string;
        photoUrls: string[];
        dateTime: string;
        lat: number;
        lng: number;
        activityRating: number | null;
        activityRatingCount: number;
        providerId: number | null;
        providerName: string | null;
        providerRating: number | null;
        providerRatingCount: number;
    }[] = [];
```

Im bestehenden `EditActivityModal`-Aufruf (im `marker`-Literal) `photoUrls: editingMarker.photoUrls` ergänzen:

```svelte
        marker={{ id: editingMarker.id, name: editingMarker.name, description: editingMarker.description, address: editingMarker.address, category: editingMarker.category, dateTime: editingMarker.dateTime, photoUrls: editingMarker.photoUrls }}
```

- [ ] **Step 5: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/AddActivity.svelte frontend/src/lib/EditActivityModal.svelte frontend/src/lib/PinDetailPanel.svelte frontend/src/lib/VolunteerList.svelte
git commit -m "feat: add photo URLs field to activity forms and thread the type through"
```

---

### Task 4: Frontend — Galerie und Anbieter-Profilbild in `PinDetailPanel.svelte`

**Context:** Hero-Bild-plus-Streifen-Galerie (Option A aus dem visuellen Vergleich): erstes Foto groß oben, restliche als kleine Vorschau darunter, Klick auf ein Vorschaubild tauscht das Hero-Bild. Da `PinDetailPanel` beim direkten Wechsel zwischen zwei Pins (Panel bleibt offen) nicht neu erstellt wird, sondern nur die `marker`-Prop aktualisiert wird, muss der ausgewählte Foto-Index explizit auf `marker.id`-Wechsel zurückgesetzt werden — sonst zeigt ein Wechsel zu einer Aktivität mit weniger Fotos einen falschen oder nicht existierenden Index.

**Files:**
- Modify: `frontend/src/lib/PinDetailPanel.svelte`

**Interfaces:**
- Consumes: `marker.photoUrls: string[]` (Task 3), `marker.providerPhotoUrl: string | null`, `marker.providerWebsiteUrl: string | null` (neu in diesem Task, aus dem Backend von Task 2).
- Produces: keine neuen Exports.

- [ ] **Step 1: `marker`-Prop-Typ um Provider-Felder erweitern**

In `frontend/src/lib/PinDetailPanel.svelte`, den `marker`-Prop-Typ (aus Task 3) um zwei weitere Felder erweitern, nach `providerName: string | null;`:

```typescript
        providerName: string | null;
        providerPhotoUrl: string | null;
        providerWebsiteUrl: string | null;
```

- [ ] **Step 2: State für den ausgewählten Foto-Index**

Nach der bestehenden `let editing = false;`-Zeile einfügen:

```typescript
    let selectedPhotoIndex = 0;
    $: {
        marker.id;
        selectedPhotoIndex = 0;
    }
```

- [ ] **Step 3: Galerie-Template**

Im Template, direkt nach dem schließenden `</div>` des `.panel-header`-Blocks und vor `<h3>{marker.name}</h3>`, einfügen:

```svelte
    {#if marker.photoUrls.length > 0}
        <div class="gallery">
            <img class="hero-photo" src={marker.photoUrls[selectedPhotoIndex]} alt={marker.name} />
            {#if marker.photoUrls.length > 1}
                <div class="photo-strip">
                    {#each marker.photoUrls as url, i}
                        <button
                            class="thumb"
                            class:selected={i === selectedPhotoIndex}
                            on:click={() => (selectedPhotoIndex = i)}
                            aria-label={`Foto ${i + 1} anzeigen`}
                        >
                            <img src={url} alt="" />
                        </button>
                    {/each}
                </div>
            {/if}
        </div>
    {/if}
```

- [ ] **Step 4: Anbieter-Profilbild und Website-Link**

Den bestehenden `.provider`-Block ersetzen durch:

```svelte
    {#if marker.providerId != null}
        <div class="provider">
            <div class="provider-header">
                {#if marker.providerPhotoUrl}
                    <img class="provider-avatar" src={marker.providerPhotoUrl} alt={marker.providerName ?? "Anbieter"} />
                {/if}
                <span class="provider-name">{marker.providerName}</span>
            </div>
            {#if marker.providerWebsiteUrl}
                <a class="provider-website" href={marker.providerWebsiteUrl} target="_blank" rel="noopener noreferrer">Website besuchen</a>
            {/if}
            <button class="rating-badge" on:click={openProviderRating}>
                {marker.providerRating != null ? `★ ${marker.providerRating.toFixed(1)} (${marker.providerRatingCount})` : "Noch keine Bewertung"}
            </button>
        </div>
    {/if}
```

- [ ] **Step 5: CSS ergänzen**

Im `<style>`-Block, nach der bestehenden `.description`-Regel, einfügen:

```css
    .gallery {
        margin: 4px 0;
    }

    .hero-photo {
        width: 100%;
        height: 160px;
        object-fit: cover;
        border-radius: var(--radius-md);
        background: var(--color-bg);
    }

    .photo-strip {
        display: flex;
        gap: 6px;
        margin-top: 6px;
        overflow-x: auto;
    }

    .thumb {
        flex: 0 0 48px;
        height: 48px;
        padding: 0;
        border: 2px solid transparent;
        border-radius: var(--radius-md);
        overflow: hidden;
        cursor: pointer;
        background: none;
    }

    .thumb img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
    }

    .thumb.selected {
        border-color: var(--color-primary);
    }
```

Im `<style>`-Block, nach der bestehenden `.provider-name`-Regel, einfügen:

```css
    .provider-header {
        display: flex;
        align-items: center;
        gap: 8px;
    }

    .provider-avatar {
        width: 28px;
        height: 28px;
        border-radius: 50%;
        object-fit: cover;
    }

    .provider-website {
        font-size: 0.8rem;
        color: var(--color-primary);
        align-self: flex-start;
    }
```

- [ ] **Step 6: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/lib/PinDetailPanel.svelte
git commit -m "feat: add photo gallery and provider avatar/website to PinDetailPanel"
```

---

### Task 5: Frontend — "Mein Profil"-Seite

**Context:** Neue Route, nur für eingeloggte Anbieter sichtbar (analog zu `/add`). Da `currentUser` asynchron über einen Store befüllt wird (nicht wie bei `EditActivityModal` synchron per Prop), braucht das Vorausfüllen der Formularfelder eine explizite "nur beim ersten Mal, wenn currentUser verfügbar ist"-Absicherung — sonst überschreibt ein späteres reaktives Update die Eingaben des Nutzers während er tippt.

**Files:**
- Create: `frontend/src/lib/Profile.svelte`
- Modify: `frontend/src/router.ts`
- Modify: `frontend/src/lib/NavBar.svelte`
- Modify: `frontend/src/auth.ts`

**Interfaces:**
- Consumes: `PUT /auth/me` (Task 2), `currentUser`-Store (aus `../auth`).
- Produces: Route `/profile`. `AuthUser` bekommt `photoUrl: string | null; websiteUrl: string | null;`.

- [ ] **Step 1: `AuthUser`-Typ erweitern**

In `frontend/src/auth.ts`, das `AuthUser`-Interface erweitern:

```typescript
export interface AuthUser {
    id: number;
    email: string;
    name: string;
    role: Role;
    photoUrl: string | null;
    websiteUrl: string | null;
}
```

- [ ] **Step 2: `Profile.svelte` anlegen**

Create `frontend/src/lib/Profile.svelte`:

```svelte
<script lang="ts">
    import { currentUser, authChecked, fetchWithSessionCheck } from "../auth";

    let photoUrl = "";
    let websiteUrl = "";
    let prefilled = false;

    $: if ($currentUser && !prefilled) {
        photoUrl = $currentUser.photoUrl ?? "";
        websiteUrl = $currentUser.websiteUrl ?? "";
        prefilled = true;
    }

    let submitting = false;
    let statusMessage: string | null = null;
    let statusIsWarning = false;

    async function handleSubmit() {
        submitting = true;
        statusMessage = null;

        try {
            const res = await fetchWithSessionCheck("http://localhost:8080/auth/me", {
                method: "PUT",
                credentials: "include",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    photoUrl: photoUrl.trim() || null,
                    websiteUrl: websiteUrl.trim() || null,
                }),
            });

            if (!res.ok) {
                statusMessage = "Fehler beim Speichern. Bitte versuche es erneut.";
                statusIsWarning = true;
                return;
            }

            currentUser.set(await res.json());
            statusMessage = "Profil wurde aktualisiert.";
            statusIsWarning = false;
        } catch (e) {
            statusMessage = "Server nicht erreichbar. Bitte versuche es später erneut.";
            statusIsWarning = true;
        } finally {
            submitting = false;
        }
    }
</script>

{#if !$authChecked}
    <div class="page"><p>Lädt…</p></div>
{:else if $currentUser?.role !== "ANBIETER"}
    <div class="page">
        <p class="notice">Nur eingeloggte Anbieter haben ein Profil.</p>
    </div>
{:else}
    <div class="page">
        <form on:submit|preventDefault={handleSubmit}>
            <label>
                Profilbild-URL
                <input type="text" bind:value={photoUrl} placeholder="https://..." />
            </label>

            <label>
                Website
                <input type="text" bind:value={websiteUrl} placeholder="https://..." />
            </label>

            <button type="submit" disabled={submitting}>
                {submitting ? "Speichert…" : "Speichern"}
            </button>

            {#if statusMessage}
                <p class:warning={statusIsWarning}>{statusMessage}</p>
            {/if}
        </form>
    </div>
{/if}

<style>
    .page {
        flex: 1;
        display: flex;
        justify-content: center;
        padding: 24px 16px;
    }

    form {
        display: flex;
        flex-direction: column;
        gap: 12px;
        width: 100%;
        max-width: 420px;
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        padding: 20px;
        box-shadow: var(--shadow-panel);
        height: fit-content;
    }

    label {
        display: flex;
        flex-direction: column;
        gap: 4px;
        font-size: 0.9rem;
        color: var(--color-text);
    }

    input {
        font-family: inherit;
        font-size: 0.9rem;
        padding: 8px 10px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        color: var(--color-text);
    }

    input:focus {
        outline: none;
        border-color: var(--color-primary);
    }

    button {
        align-self: flex-start;
    }

    button:disabled {
        opacity: 0.6;
        cursor: default;
    }

    p.warning {
        color: var(--color-error);
        font-size: 0.85rem;
        margin: 0;
    }

    .notice {
        color: var(--color-text-muted);
        font-size: 0.9rem;
        text-align: center;
        max-width: 420px;
    }
</style>
```

- [ ] **Step 3: Route registrieren**

In `frontend/src/router.ts`, den Import ergänzen:

```typescript
import Profile from "./lib/Profile.svelte";
```

Und den `routes`-Eintrag ergänzen:

```typescript
export const routes: Record<string, Component> = {
    "/": Home,
    "/about": About,
    "/add": AddActivity,
    "/profile": Profile,
    "/login": Login,
    "/register": Register,
};
```

- [ ] **Step 4: NavBar-Link ergänzen**

In `frontend/src/lib/NavBar.svelte`, nach dem bestehenden `{#if $currentUser?.role === "ANBIETER"}`-Block für "Aktivität hinzufügen", innerhalb desselben `{#if}`, ergänzen:

```svelte
        {#if $currentUser?.role === "ANBIETER"}
            <Link href="/add" activeClass="active">Aktivität hinzufügen</Link>
            <Link href="/profile" activeClass="active">Mein Profil</Link>
        {/if}
```

- [ ] **Step 5: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/Profile.svelte frontend/src/router.ts frontend/src/lib/NavBar.svelte frontend/src/auth.ts
git commit -m "feat: add Mein-Profil page for editing provider photo/website"
```

---

### Task 6: End-to-End-Verifikation

**Context:** Kompletter manueller Rundgang mit laufendem Backend und Frontend, als eingeloggter Anbieter.

**Files:** keine (nur Verifikation)

- [ ] **Step 1: Volle Testsuiten laufen lassen**

Run: `cd backend && ./gradlew.bat test`
Expected: `BUILD SUCCESSFUL`.

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler/Warnungen gegenüber dem Stand vor diesem Plan (bekannte Altlasten wie der `FilterBar.svelte`-Fehler und die `Router.svelte`-Warnung zählen nicht als neu).

- [ ] **Step 2: Visueller Rundgang im Browser**

Mit laufendem Backend (`./gradlew.bat bootRun`) und Frontend (`npm run dev`):

- Als Anbieter einloggen, eine Aktivität mit mehreren Foto-URLs anlegen (über `/add`, mehrere Zeilen im neuen Textarea-Feld, z. B. 3 echte Bild-URLs)
- Auf der Karte: Aktivität anklicken → `PinDetailPanel` zeigt das erste Foto groß, darunter die restlichen als Vorschau-Streifen
- Klick auf ein Vorschaubild tauscht das große Foto aus
- Zu einer anderen, fotolosen Aktivität wechseln (Klick auf einen anderen Pin, Panel bleibt offen) → keine Galerie sichtbar, kein Fehler
- Zurück zur ersten Aktivität wechseln → Galerie zeigt wieder Foto 1 groß (Index korrekt zurückgesetzt)
- Mehr als 10 Foto-URLs einfügen (beim Bearbeiten) → nur die ersten 10 werden gespeichert (nach dem Speichern erneut öffnen und Zeilenzahl im Textarea prüfen)
- Als Anbieter zu "Mein Profil" navigieren, Profilbild-URL und Website setzen, speichern → Erfolgsmeldung erscheint
- Zur eigenen Aktivität zurück (Karte oder Liste) → Anbieter-Bereich im `PinDetailPanel` zeigt jetzt das Profilbild als kleinen Avatar und einen "Website besuchen"-Link, der in neuem Tab öffnet
- Als `USER`-Rolle eingeloggt: kein "Mein Profil"-Link in der Navigation, `/profile` direkt aufgerufen zeigt den Hinweis "Nur eingeloggte Anbieter haben ein Profil"
- Aktivität ohne jegliche Fotos: kein leerer Galerie-Bereich, keine visuelle Lücke

- [ ] **Step 3: Report**

Zusammenfassung Pass/Fail für Schritt 1-2. Wenn alles passt: Fotos-und-Anbieterprofil ist fertig.
