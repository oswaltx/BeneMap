# Bewertungssystem Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let logged-in `USER`s rate individual activities and providers (1-5 stars + optional comment), with ratings publicly readable (including logged-out) and re-rating updating the existing entry instead of duplicating.

**Architecture:** Two new independent JPA entities (`ActivityRating`, `ProviderRating`), each with a DB unique constraint on `(user, target)`, exposed through a new `RatingController`. The existing `/markers` response gains rating-summary fields so the frontend can show averages without a request per card. A new `RatingModal.svelte` component (shared for both rating targets) is triggered from `VolunteerList.svelte`.

**Tech Stack:** Spring Boot 4 (Kotlin) + Spring Security (session auth, already in place) on the backend; Svelte 5 legacy style (`<script lang="ts">`, `createEventDispatcher`) on the frontend — same stack and conventions as the rest of the project.

## Global Constraints

- Nur die Rolle `USER` darf Bewertungen abgeben (nicht `ANBIETER`) — exact wording from spec.
- Lesen von Bewertungen ist immer öffentlich, auch ausgeloggt.
- Erneutes Bewerten desselben Ziels durch denselben User **aktualisiert** die
  bestehende Bewertung (Upsert), statt abgelehnt zu werden.
- DB-Unique-Constraint auf `(user, activity)` bzw. `(user, provider)` erzwingt
  "eine Bewertung pro User+Ziel" auf Datenbankebene.
- Kein Teilnahme-Nachweis vor dem Bewerten (explizite Nicht-Ziel-Entscheidung
  aus der Spec).
- Anbieter-Bewertungen erscheinen inline in der bestehenden `VolunteerList`-
  Karte — keine eigene Profilseite.
- `stars` außerhalb 1-5 → 400; `POST` ohne Login → 401; `POST` als `ANBIETER`
  → 403; `POST /providers/{id}/ratings` mit `{id}`, das kein Anbieter ist →
  404.
- **Implementation-necessary addition beyond the spec's literal DTO field
  list:** the `Marker` DTO also gains `providerId: Long?` and
  `providerName: String?`. The spec only explicitly names the four rating
  fields, but the frontend needs the provider's identity to call
  `POST /providers/{id}/ratings` and to show a readable label in the rating
  modal — there is no other endpoint that exposes it.

---

### Task 1: `ActivityRating` and `ProviderRating` entities + repositories

**Context:** Pure data-model scaffolding, no behavior yet — mirrors how
`User`/`UserRepository` were introduced in the login feature (no test file,
just a compile check).

**Files:**
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/ActivityRating.kt`
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/ProviderRating.kt`
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/ActivityRatingRepository.kt`
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/ProviderRatingRepository.kt`

**Interfaces:**
- Consumes: `User` (id, name, role), `VolunteerActivity` (id) — both exist
  already.
- Produces: `ActivityRating(id, user: User, activity: VolunteerActivity,
  stars: Int, comment: String?, createdAt: Instant)`;
  `ProviderRating(id, user: User, provider: User, stars: Int, comment:
  String?, createdAt: Instant)`;
  `ActivityRatingRepository.findByUserAndActivity(user: User, activity:
  VolunteerActivity): ActivityRating?`,
  `ActivityRatingRepository.findByActivity(activity: VolunteerActivity):
  List<ActivityRating>`;
  `ProviderRatingRepository.findByUserAndProvider(user: User, provider:
  User): ProviderRating?`, `ProviderRatingRepository.findByProvider(provider:
  User): List<ProviderRating>`. Task 2 and Task 3 call these directly.

- [ ] **Step 1: `ActivityRating.kt` anlegen**

```kotlin
package com.example.VoloMap.server

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "activity_ratings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "activity_id"])]
)
class ActivityRating(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne
    @JoinColumn(name = "activity_id", nullable = false)
    var activity: VolunteerActivity,

    var stars: Int,

    @Column(columnDefinition = "TEXT")
    var comment: String? = null,

    var createdAt: Instant = Instant.now(),
)
```

- [ ] **Step 2: `ProviderRating.kt` anlegen**

```kotlin
package com.example.VoloMap.server

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "provider_ratings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "provider_id"])]
)
class ProviderRating(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne
    @JoinColumn(name = "provider_id", nullable = false)
    var provider: User,

    var stars: Int,

    @Column(columnDefinition = "TEXT")
    var comment: String? = null,

    var createdAt: Instant = Instant.now(),
)
```

- [ ] **Step 3: `ActivityRatingRepository.kt` anlegen**

```kotlin
package com.example.VoloMap.server

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ActivityRatingRepository : JpaRepository<ActivityRating, Long> {
    fun findByUserAndActivity(user: User, activity: VolunteerActivity): ActivityRating?
    fun findByActivity(activity: VolunteerActivity): List<ActivityRating>
}
```

- [ ] **Step 4: `ProviderRatingRepository.kt` anlegen**

```kotlin
package com.example.VoloMap.server

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProviderRatingRepository : JpaRepository<ProviderRating, Long> {
    fun findByUserAndProvider(user: User, provider: User): ProviderRating?
    fun findByProvider(provider: User): List<ProviderRating>
}
```

- [ ] **Step 5: Verify**

Run (from `backend/`): `./gradlew.bat compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/ActivityRating.kt backend/src/main/kotlin/com/example/VoloMap/server/ProviderRating.kt backend/src/main/kotlin/com/example/VoloMap/server/ActivityRatingRepository.kt backend/src/main/kotlin/com/example/VoloMap/server/ProviderRatingRepository.kt
git commit -m "feat: add ActivityRating and ProviderRating entities and repositories"
```

---

### Task 2: `RatingController` — create/list ratings, wire authorization

**Context:** The actual rating endpoints. `POST` upserts (create-or-update)
based on the unique `(user, target)` pair; `GET` is public and includes the
current caller's own rating (`myRating`) so the frontend can pre-fill the
form on re-open. Also updates `SecurityConfig` so these new routes are
reachable at all — without this, every request 401s regardless of the
controller code, matching how `SecurityConfig` was updated alongside
`AuthController` in the login feature.

**Files:**
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/RatingController.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt:72-77`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/RatingControllerTest.kt`

**Interfaces:**
- Consumes: `ActivityRatingRepository`, `ProviderRatingRepository` (Task 1);
  `VolunteerActivityRepository`, `UserRepository`, `Role` (already exist).
- Produces: `data class RatingRequest(stars: Int, comment: String?)`;
  `data class RatingEntry(userName: String, stars: Int, comment: String?,
  createdAt: Instant)`; `data class RatingListResponse(average: Double?,
  count: Int, ratings: List<RatingEntry>, myRating: RatingEntry?)`. Task 3
  (Marker aggregation) does its own averaging directly from the
  repositories and does not depend on these DTOs, but the frontend
  (Task 5/6) consumes this exact JSON shape from
  `GET /activities/{id}/ratings` and `GET /providers/{id}/ratings`.

- [ ] **Step 1: Failing Tests schreiben**

Create `backend/src/test/kotlin/com/example/VoloMap/server/RatingControllerTest.kt`:

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class RatingControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var activityRatingRepository: ActivityRatingRepository

    @Autowired
    lateinit var providerRatingRepository: ProviderRatingRepository

    @BeforeEach
    fun cleanUp() {
        activityRatingRepository.deleteAll()
        providerRatingRepository.deleteAll()
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

    private fun createActivity(name: String = "Testaktion"): Long =
        activityRepository.save(VolunteerActivity(name = name)).id

    @Test
    fun `posts an activity rating and returns it via GET`() {
        val session = registerAndSession("rater@example.com", "USER")
        val activityId = createActivity()

        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":4,"comment":"Gut organisiert"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(get("/activities/$activityId/ratings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.average").value(4.0))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.ratings[0].userName").value("Test"))
            .andExpect(jsonPath("$.ratings[0].stars").value(4))
            .andExpect(jsonPath("$.ratings[0].comment").value("Gut organisiert"))
    }

    @Test
    fun `re-rating the same activity updates instead of duplicating`() {
        val session = registerAndSession("rater@example.com", "USER")
        val activityId = createActivity()

        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":2,"comment":"Erster Eindruck"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":5,"comment":"Nach Nachdenken doch super"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(get("/activities/$activityId/ratings"))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.average").value(5.0))
            .andExpect(jsonPath("$.ratings[0].comment").value("Nach Nachdenken doch super"))
    }

    @Test
    fun `rating an activity without a session is rejected`() {
        val activityId = createActivity()
        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":3}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `rating an activity as ANBIETER is rejected`() {
        val session = registerAndSession("anbieter@example.com", "ANBIETER")
        val activityId = createActivity()
        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":3}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `rating a nonexistent activity returns 404`() {
        val session = registerAndSession("rater@example.com", "USER")
        mockMvc.perform(
            post("/activities/999999/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":3}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `stars outside 1-5 is rejected`() {
        val session = registerAndSession("rater@example.com", "USER")
        val activityId = createActivity()
        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":6}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `GET includes myRating only for the logged-in user's own rating`() {
        val activityId = createActivity()
        val sessionA = registerAndSession("a@example.com", "USER")
        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":5}""")
        ).andExpect(status().isOk)

        mockMvc.perform(get("/activities/$activityId/ratings").session(sessionA))
            .andExpect(jsonPath("$.myRating.stars").value(5))

        mockMvc.perform(get("/activities/$activityId/ratings"))
            .andExpect(jsonPath("$.myRating").doesNotExist())
    }

    @Test
    fun `posts a provider rating and returns it via GET`() {
        val session = registerAndSession("rater@example.com", "USER")
        registerAndSession("anbieter@example.com", "ANBIETER")
        val providerId = userRepository.findByEmail("anbieter@example.com")!!.id

        mockMvc.perform(
            post("/providers/$providerId/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":3,"comment":"Zuverlässig"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(get("/providers/$providerId/ratings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.average").value(3.0))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.ratings[0].comment").value("Zuverlässig"))
    }

    @Test
    fun `rating a non-provider user as provider returns 404`() {
        val session = registerAndSession("rater@example.com", "USER")
        registerAndSession("otheruser@example.com", "USER")
        val notAProvider = userRepository.findByEmail("otheruser@example.com")!!.id

        mockMvc.perform(
            post("/providers/$notAProvider/ratings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":3}""")
        ).andExpect(status().isNotFound)
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

Run (from `backend/`): `./gradlew.bat test --tests "com.example.VoloMap.server.RatingControllerTest"`
Expected: FAIL — `RatingController`/`RatingRequest`/`RatingListResponse`
don't exist yet (compile error), and all `/activities/*/ratings` /
`/providers/*/ratings` routes 404 or 401 in a way that doesn't match these
tests' expectations even once the compile error is fixed, because
`SecurityConfig` doesn't know about these routes yet.

- [ ] **Step 3: `RatingController.kt` anlegen**

```kotlin
package com.example.VoloMap.server

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class RatingRequest(
    @field:Min(1) @field:Max(5) val stars: Int,
    val comment: String? = null
)

data class RatingEntry(
    val userName: String,
    val stars: Int,
    val comment: String?,
    val createdAt: Instant
)

data class RatingListResponse(
    val average: Double?,
    val count: Int,
    val ratings: List<RatingEntry>,
    val myRating: RatingEntry?
)

@RestController
class RatingController(
    private val activityRepository: VolunteerActivityRepository,
    private val userRepository: UserRepository,
    private val activityRatingRepository: ActivityRatingRepository,
    private val providerRatingRepository: ProviderRatingRepository,
) {

    @PostMapping("/activities/{id}/ratings")
    fun rateActivity(
        @PathVariable id: Long,
        @Valid @RequestBody req: RatingRequest,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val activity = activityRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val user = userRepository.findByEmail(authentication.name)!!
        val existing = activityRatingRepository.findByUserAndActivity(user, activity)
        if (existing != null) {
            existing.stars = req.stars
            existing.comment = req.comment
            activityRatingRepository.save(existing)
        } else {
            activityRatingRepository.save(
                ActivityRating(user = user, activity = activity, stars = req.stars, comment = req.comment)
            )
        }
        return ResponseEntity.ok().build()
    }

    @GetMapping("/activities/{id}/ratings")
    fun getActivityRatings(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<RatingListResponse> {
        val activity = activityRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val ratings = activityRatingRepository.findByActivity(activity)
            .map { RatingEntry(it.user.name, it.stars, it.comment, it.createdAt) }
        val me = userRepository.findByEmail(authentication.name)
        val myRating = me?.let { activityRatingRepository.findByUserAndActivity(it, activity) }
            ?.let { RatingEntry(it.user.name, it.stars, it.comment, it.createdAt) }
        return ResponseEntity.ok(
            RatingListResponse(
                average = ratings.map { it.stars }.average().takeIf { ratings.isNotEmpty() },
                count = ratings.size,
                ratings = ratings,
                myRating = myRating
            )
        )
    }

    @PostMapping("/providers/{id}/ratings")
    fun rateProvider(
        @PathVariable id: Long,
        @Valid @RequestBody req: RatingRequest,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val provider = userRepository.findById(id).orElse(null)
        if (provider == null || provider.role != Role.ANBIETER) {
            return ResponseEntity.notFound().build()
        }
        val user = userRepository.findByEmail(authentication.name)!!
        val existing = providerRatingRepository.findByUserAndProvider(user, provider)
        if (existing != null) {
            existing.stars = req.stars
            existing.comment = req.comment
            providerRatingRepository.save(existing)
        } else {
            providerRatingRepository.save(
                ProviderRating(user = user, provider = provider, stars = req.stars, comment = req.comment)
            )
        }
        return ResponseEntity.ok().build()
    }

    @GetMapping("/providers/{id}/ratings")
    fun getProviderRatings(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<RatingListResponse> {
        val provider = userRepository.findById(id).orElse(null)
        if (provider == null || provider.role != Role.ANBIETER) {
            return ResponseEntity.notFound().build()
        }
        val ratings = providerRatingRepository.findByProvider(provider)
            .map { RatingEntry(it.user.name, it.stars, it.comment, it.createdAt) }
        val me = userRepository.findByEmail(authentication.name)
        val myRating = me?.let { providerRatingRepository.findByUserAndProvider(it, provider) }
            ?.let { RatingEntry(it.user.name, it.stars, it.comment, it.createdAt) }
        return ResponseEntity.ok(
            RatingListResponse(
                average = ratings.map { it.stars }.average().takeIf { ratings.isNotEmpty() },
                count = ratings.size,
                ratings = ratings,
                myRating = myRating
            )
        )
    }
}
```

- [ ] **Step 4: `SecurityConfig.kt` — Autorisierungsregeln erweitern**

In `backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt`,
replace the `authorizeHttpRequests` block (currently lines 72-77):

```kotlin
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/", "/markers", "/categories", "/activities/*/ratings", "/providers/*/ratings").permitAll()
                it.requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                it.requestMatchers(HttpMethod.POST, "/add").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.POST, "/activities/*/ratings", "/providers/*/ratings").hasRole("USER")
                it.anyRequest().authenticated()
            }
```

(Only the two new `/activities/*/ratings`/`/providers/*/ratings` path
entries are new — the `GET` permitAll list gains them, and a new `POST`
`hasRole("USER")` line is added before the `POST /add` rule's sibling
lines. No other line in this method changes.)

- [ ] **Step 5: Tests erneut laufen lassen, Erfolg bestätigen**

Run (from `backend/`): `./gradlew.bat test --tests "com.example.VoloMap.server.RatingControllerTest"`
Expected: PASS (alle 9 Tests).

- [ ] **Step 6: Volle Testsuite laufen lassen**

Run (from `backend/`): `./gradlew.bat test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/RatingController.kt backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt backend/src/test/kotlin/com/example/VoloMap/server/RatingControllerTest.kt
git commit -m "feat: add RatingController for activity and provider ratings"
```

---

### Task 3: `/markers` — Bewertungs-Durchschnitte einbetten

**Context:** So the frontend list can show star averages without a request
per card, `GET /markers` gains four rating fields plus the provider's
identity (see Global Constraints — the identity fields are needed even
though the spec's DTO list didn't literally name them). Averages are
computed by loading all ratings once and grouping in Kotlin (two extra
queries total, not one per marker) rather than querying per activity in a
loop.

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`
- Modify: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt`
- Modify: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt`

**Interfaces:**
- Consumes: `ActivityRatingRepository`, `ProviderRatingRepository` (Task 1).
- Produces: `Marker` gains `activityRating: Double?`, `activityRatingCount:
  Int`, `providerId: Long?`, `providerName: String?`, `providerRating:
  Double?`, `providerRatingCount: Int`. `MainController`'s constructor
  gains two more parameters: `activityRatingRepository:
  ActivityRatingRepository, providerRatingRepository:
  ProviderRatingRepository` (5 total, after `repository, geocodingService,
  userRepository`). Task 5/6 (frontend) consume these exact `Marker` field
  names from `GET /markers`.

- [ ] **Step 1: Failing Test schreiben**

In `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt`,
add this test at the end of the class, before the closing `}`:

```kotlin
    @Test
    fun `includes rating averages and provider identity for an activity`() {
        val repository = mock<VolunteerActivityRepository>()
        val activityRatingRepository = mock<ActivityRatingRepository>()
        val providerRatingRepository = mock<ProviderRatingRepository>()
        val provider = User(id = 7, email = "anbieter@example.com", passwordHash = "x", name = "Anbieter Anna", role = Role.ANBIETER)
        val rated = activity(
            name = "Bewertete Aktion",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        ).also { it.createdBy = provider }
        whenever(repository.findAll()).thenReturn(listOf(rated))
        whenever(activityRatingRepository.findAll()).thenReturn(
            listOf(
                ActivityRating(user = mock(), activity = rated, stars = 4),
                ActivityRating(user = mock(), activity = rated, stars = 2),
            )
        )
        whenever(providerRatingRepository.findAll()).thenReturn(
            listOf(ProviderRating(user = mock(), provider = provider, stars = 5))
        )

        val controller = MainController(repository, mock(), mock(), activityRatingRepository, providerRatingRepository)
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals(3.0, result[0].activityRating)
        assertEquals(2, result[0].activityRatingCount)
        assertEquals(7L, result[0].providerId)
        assertEquals("Anbieter Anna", result[0].providerName)
        assertEquals(5.0, result[0].providerRating)
        assertEquals(1, result[0].providerRatingCount)
    }

    @Test
    fun `rating fields are null and zero when an activity has no ratings or owner`() {
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

        assertNull(result[0].activityRating)
        assertEquals(0, result[0].activityRatingCount)
        assertNull(result[0].providerId)
        assertNull(result[0].providerName)
        assertNull(result[0].providerRating)
        assertEquals(0, result[0].providerRatingCount)
    }
```

Also add `import org.junit.jupiter.api.Assertions.assertNull` to this
file's imports (it currently only imports `assertEquals`), and update the
two existing `MainController(repository, mock(), mock())` calls (in
`combines category, search and time range filters with AND semantics` and
`search matches name, address or description case-insensitively`) to
`MainController(repository, mock(), mock(), mock(), mock())` — the
constructor now takes 5 arguments, not 3.

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

Run (from `backend/`): `./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerMarkersTest"`
Expected: FAIL — compile error (`Marker` has no `activityRating` property
yet, `MainController` doesn't take 5 constructor arguments yet).

- [ ] **Step 3: `Marker.kt` erweitern**

Replace `backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt` in full:

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
    val dateTime: LocalDateTime?,
    val activityRating: Double?,
    val activityRatingCount: Int,
    val providerId: Long?,
    val providerName: String?,
    val providerRating: Double?,
    val providerRatingCount: Int,
)
```

- [ ] **Step 4: `MainController.kt` — Konstruktor und `markers()` anpassen**

Replace `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt` in full:

```kotlin
package com.example.VoloMap.server

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
class MainController(
    private val repository: VolunteerActivityRepository,
    private val geocodingService: GeocodingService,
    private val userRepository: UserRepository,
    private val activityRatingRepository: ActivityRatingRepository,
    private val providerRatingRepository: ProviderRatingRepository,
) {

    @GetMapping("/")
    fun index() = "Hello World!"


    @GetMapping("/markers")
    fun markers(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) date: String?, // format: YYYY-MM-DD
        @RequestParam(required = false) timeFrom: Int?,
        @RequestParam(required = false) timeTo: Int?,
        @RequestParam(required = false) search: String?,
    ): List<Marker> {
        val filterDate = date?.let { LocalDate.parse(it) }
        val searchText = search?.trim()?.lowercase()

        val activityRatingsByActivityId = activityRatingRepository.findAll().groupBy { it.activity.id }
        val providerRatingsByProviderId = providerRatingRepository.findAll().groupBy { it.provider.id }

        return repository.findAll()
            .filter { category == null || it.category == category }
            .filter { it.latitude != null && it.longitude != null }
            .map { activity ->
                val activityRatings = activityRatingsByActivityId[activity.id].orEmpty()
                val providerId = activity.createdBy?.id
                val providerRatings = providerId?.let { providerRatingsByProviderId[it] }.orEmpty()
                Marker(
                    id = activity.id,
                    lat = activity.latitude!!,
                    lng = activity.longitude!!,
                    name = activity.name,
                    address = activity.addressText ?: "",
                    category = activity.category ?: "",
                    description = activity.description ?: "",
                    dateTime = activity.dateTime,
                    activityRating = activityRatings.map { it.stars }.average().takeIf { activityRatings.isNotEmpty() },
                    activityRatingCount = activityRatings.size,
                    providerId = providerId,
                    providerName = activity.createdBy?.name,
                    providerRating = providerRatings.map { it.stars }.average().takeIf { providerRatings.isNotEmpty() },
                    providerRatingCount = providerRatings.size,
                )
            }
            .filter { filterDate == null || it.dateTime?.toLocalDate() == filterDate }
            .filter { timeFrom == null || (it.dateTime?.hour ?: 0) >= timeFrom }
            .filter { timeTo == null || (it.dateTime?.hour ?: 0) < timeTo }
            .filter {
                searchText == null ||
                        it.name.lowercase().contains(searchText) ||
                        it.address.lowercase().contains(searchText) ||
                        it.description.lowercase().contains(searchText)
            }
    }
    @GetMapping("/categories")
    fun categories(): List<String> {
        return repository.findAll()
            .mapNotNull { it.category }
            .distinct()
            .sorted()
    }
    @PostMapping("/add")
    fun addActivity(
        @RequestBody activity: VolunteerActivity,
        authentication: Authentication
    ): ResponseEntity<VolunteerActivity> {
        activity.id = 0
        activity.createdBy = userRepository.findByEmail(authentication.name)

        if (activity.latitude == null && activity.longitude == null && !activity.addressText.isNullOrBlank()) {
            val coords = geocodingService.geocode(activity.addressText!!)
            if (coords != null) {
                activity.latitude = coords.first
                activity.longitude = coords.second
            }
        }
        val savedActivity = repository.save(activity)
        return ResponseEntity.ok(savedActivity)
    }


}
```

- [ ] **Step 5: `MainControllerAddActivityTest.kt` an neue Konstruktor-Arität anpassen**

In `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt`,
update all three `MainController(repository, geocodingService,
userRepository)` calls to
`MainController(repository, geocodingService, userRepository, mock(), mock())`.
These tests exercise `addActivity`, which never touches
`activityRatingRepository`/`providerRatingRepository`, so unstubbed
`mock()` instances are sufficient — no other change needed in this file.

- [ ] **Step 6: Tests erneut laufen lassen, Erfolg bestätigen**

Run (from `backend/`): `./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerMarkersTest"`
Expected: PASS (alle 4 Tests: die 2 bestehenden + die 2 neuen).

- [ ] **Step 7: Volle Testsuite laufen lassen**

Run (from `backend/`): `./gradlew.bat test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt
git commit -m "feat: embed rating averages and provider identity in /markers"
```

---

### Task 4: Backend-Gesamtverifikation

**Context:** Confirms the three backend tasks work together before the
frontend builds on top of them — same pattern as the login feature's
Task 5.

**Files:** keine (nur Verifikation)

- [ ] **Step 1: Volle Testsuite**

Run (from `backend/`): `./gradlew.bat test`
Expected: `BUILD SUCCESSFUL`, alle Tests grün (inkl.
`RatingControllerTest`, `MainControllerMarkersTest`,
`MainControllerAddActivityTest`, sowie alle bestehenden Login-Tests
unverändert grün).

- [ ] **Step 2: Manueller Smoke-Test mit laufendem Server**

Run: `cd backend && ./gradlew.bat bootRun`

In einem zweiten Terminal:

```bash
curl -i -c cookies.txt -X POST http://localhost:8080/auth/register -H "Content-Type: application/json" -d "{\"email\":\"rater@example.com\",\"password\":\"geheim123\",\"name\":\"Rater\",\"role\":\"USER\"}"
curl -i http://localhost:8080/markers
```

Expected: Register → `200`; `/markers` → `200`, jeder Eintrag hat die neuen
Felder `activityRating`, `activityRatingCount`, `providerId`,
`providerName`, `providerRating`, `providerRatingCount` (bei den
vorhandenen, ungeseedeten Testdaten meist `null`/`0`, da noch keine
Bewertungen existieren — das ist erwartet, kein Fehler).

- [ ] **Step 3: Report**

Pass/Fail für Step 1-2 zusammenfassen. Server danach stoppen (Strg+C im
`bootRun`-Terminal).

---

### Task 5: Frontend — `RatingModal.svelte`

**Context:** Ein wiederverwendbares Modal für beide Bewertungsziele
(Aktivität und Anbieter). Lädt beim Öffnen die vorhandenen Bewertungen
(öffentlich, kein Login nötig), zeigt das Eingabeformular nur für
eingeloggte `USER` und füllt es mit `myRating` vor, falls der User dieses
Ziel schon bewertet hat.

**Files:**
- Create: `frontend/src/lib/RatingModal.svelte`

**Interfaces:**
- Consumes: `currentUser`, `fetchWithSessionCheck` aus `../auth` (bereits
  vorhanden); `Link` aus `./Link.svelte` (bereits vorhanden).
- Produces: Props `target: "activity" | "provider"`, `targetId: number`,
  `targetLabel: string`; Events `close` (kein Payload), `rated` (kein
  Payload, gefeuert nach erfolgreichem Speichern). Task 6
  (`VolunteerList.svelte`) rendert diese Komponente mit genau diesen Prop-
  und Event-Namen.

- [ ] **Step 1: `RatingModal.svelte` anlegen**

```svelte
<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import Link from "./Link.svelte";
    import { currentUser, fetchWithSessionCheck } from "../auth";

    export let target: "activity" | "provider";
    export let targetId: number;
    export let targetLabel: string;

    const dispatch = createEventDispatcher<{ close: void; rated: void }>();

    interface RatingEntry {
        userName: string;
        stars: number;
        comment: string | null;
        createdAt: string;
    }

    interface RatingListResponse {
        average: number | null;
        count: number;
        ratings: RatingEntry[];
        myRating: RatingEntry | null;
    }

    const endpoint = target === "activity" ? `activities/${targetId}/ratings` : `providers/${targetId}/ratings`;

    let ratings: RatingEntry[] = [];
    let average: number | null = null;
    let count = 0;
    let loading = true;
    let loadError: string | null = null;

    let selectedStars = 0;
    let comment = "";
    let submitting = false;
    let submitError: string | null = null;

    async function loadRatings() {
        loading = true;
        loadError = null;
        try {
            const res = await fetch(`http://localhost:8080/${endpoint}`, { credentials: "include" });
            if (!res.ok) throw new Error("Request failed");
            const data: RatingListResponse = await res.json();
            average = data.average;
            count = data.count;
            ratings = data.ratings;
            if (data.myRating) {
                selectedStars = data.myRating.stars;
                comment = data.myRating.comment ?? "";
            }
        } catch (e) {
            loadError = "Bewertungen konnten nicht geladen werden.";
        } finally {
            loading = false;
        }
    }

    loadRatings();

    async function handleSubmit() {
        if (selectedStars < 1) {
            submitError = "Bitte wähle eine Sternebewertung.";
            return;
        }
        submitting = true;
        submitError = null;
        try {
            const res = await fetchWithSessionCheck(`http://localhost:8080/${endpoint}`, {
                method: "POST",
                credentials: "include",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ stars: selectedStars, comment: comment || null }),
            });
            if (!res.ok) {
                submitError = "Bewertung konnte nicht gespeichert werden.";
                return;
            }
            await loadRatings();
            dispatch("rated");
        } catch (e) {
            submitError = "Server nicht erreichbar. Bitte versuche es später erneut.";
        } finally {
            submitting = false;
        }
    }
</script>

<button class="backdrop" aria-label="Schließen" on:click={() => dispatch("close")}></button>
<div class="modal">
    <div class="modal-header">
        <h3>{targetLabel}</h3>
        <button class="close" on:click={() => dispatch("close")} aria-label="Schließen">×</button>
    </div>

    {#if loading}
        <p>Lädt…</p>
    {:else if loadError}
        <p class="warning">{loadError}</p>
    {:else}
        <p class="summary">
            {#if average != null}
                ★ {average.toFixed(1)} ({count} Bewertung{count === 1 ? "" : "en"})
            {:else}
                Noch keine Bewertungen.
            {/if}
        </p>

        <div class="rating-list">
            {#each ratings as r}
                <div class="rating-entry">
                    <div class="rating-entry-header">
                        <strong>{r.userName}</strong>
                        <span class="stars">{"★".repeat(r.stars)}{"☆".repeat(5 - r.stars)}</span>
                    </div>
                    {#if r.comment}<p>{r.comment}</p>{/if}
                </div>
            {/each}
        </div>

        {#if $currentUser?.role === "USER"}
            <form class="rate-form" on:submit|preventDefault={handleSubmit}>
                <div class="star-input">
                    {#each [1, 2, 3, 4, 5] as n}
                        <button
                                type="button"
                                class="star-button"
                                class:selected={n <= selectedStars}
                                on:click={() => (selectedStars = n)}
                                aria-label={`${n} Sterne`}
                        >★</button>
                    {/each}
                </div>
                <textarea bind:value={comment} placeholder="Kommentar (optional)"></textarea>
                <button type="submit" disabled={submitting}>
                    {submitting ? "Speichert…" : "Bewertung abschicken"}
                </button>
                {#if submitError}<p class="warning">{submitError}</p>{/if}
            </form>
        {:else}
            <p class="notice">
                Nur eingeloggte User können bewerten.
                <Link href="/login">Jetzt einloggen</Link> oder
                <Link href="/register">registrieren</Link>.
            </p>
        {/if}
    {/if}
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

    .summary {
        font-weight: 600;
        color: var(--color-text);
        margin: 0;
    }

    .rating-list {
        display: flex;
        flex-direction: column;
        gap: 8px;
        max-height: 200px;
        overflow-y: auto;
    }

    .rating-entry {
        border-top: 1px solid var(--color-border);
        padding-top: 8px;
    }

    .rating-entry:first-child {
        border-top: none;
        padding-top: 0;
    }

    .rating-entry-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-size: 0.85rem;
    }

    .stars {
        color: var(--color-accent-text);
    }

    .rating-entry p {
        margin: 4px 0 0;
        font-size: 0.85rem;
        color: var(--color-text-muted);
    }

    .rate-form {
        display: flex;
        flex-direction: column;
        gap: 8px;
        border-top: 1px solid var(--color-border);
        padding-top: 12px;
    }

    .star-input {
        display: flex;
        gap: 4px;
    }

    .star-button {
        background: none;
        border: none;
        font-size: 1.4rem;
        line-height: 1;
        cursor: pointer;
        color: var(--color-border);
        padding: 0;
    }

    .star-button.selected {
        color: var(--color-accent-text);
    }

    textarea {
        font-family: inherit;
        font-size: 0.9rem;
        padding: 8px 10px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        color: var(--color-text);
        resize: vertical;
        min-height: 60px;
    }

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

    .warning {
        color: var(--color-error);
        font-size: 0.85rem;
        margin: 0;
    }

    .notice {
        color: var(--color-text-muted);
        font-size: 0.9rem;
        text-align: center;
        margin: 0;
        border-top: 1px solid var(--color-border);
        padding-top: 12px;
    }
</style>
```

- [ ] **Step 2: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler (die Datei wird von keiner Komponente
importiert, bis Task 6).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/RatingModal.svelte
git commit -m "feat: add RatingModal component for activity and provider ratings"
```

---

### Task 6: `VolunteerList` und `Map` — Bewertungen anzeigen und öffnen

**Context:** Verdrahtet `RatingModal` in die bestehende Karten-Liste: jede
Karte zeigt zwei klickbare Sterne-Badges (Aktivität, und Anbieter falls
vorhanden), die das Modal öffnen. Nach erfolgreichem Speichern feuert
`VolunteerList` ein `refresh`-Event nach oben, das `Map.svelte` mit einem
erneuten `fetchMarkers()` beantwortet, damit die angezeigten Durchschnitte
aktuell bleiben.

**Files:**
- Modify: `frontend/src/lib/VolunteerList.svelte`
- Modify: `frontend/src/lib/Map.svelte:113`

**Interfaces:**
- Consumes: `RatingModal` (Task 5) mit den Props/Events aus Task 5;
  `markers`-Prop erweitert um die sechs neuen Felder aus Task 3
  (`activityRating`, `activityRatingCount`, `providerId`, `providerRating`,
  `providerRatingCount` — `providerName` wird hier nicht gebraucht, das
  Modal zeigt für Anbieter-Bewertungen den festen Titel "Anbieter").
- Produces: `VolunteerList` dispatcht `refresh` (kein Payload) — `Map.svelte`
  reagiert darauf mit `fetchMarkers()`, der bereits vorhandenen Funktion.

- [ ] **Step 1: `VolunteerList.svelte` komplett ersetzen**

```svelte
<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import RatingModal from "./RatingModal.svelte";

    export let markers: {
        id: number;
        name: string;
        address: string;
        category: string;
        dateTime: string;
        lat: number;
        lng: number;
        activityRating: number | null;
        activityRatingCount: number;
        providerId: number | null;
        providerRating: number | null;
        providerRatingCount: number;
    }[] = [];

    const dispatch = createEventDispatcher<{ refresh: void }>();

    const categoryPalette = [
        { bg: "#FDEBB0", text: "#6B4E00" },
        { bg: "#CFE3D2", text: "#1F4A2C" },
        { bg: "#FBD8CC", text: "#8A3B22" },
        { bg: "#D7E4F0", text: "#204A6B" },
        { bg: "#E8DFF5", text: "#4A2E6B" },
    ];

    function categoryColor(category: string) {
        let hash = 0;
        for (let i = 0; i < category.length; i++) {
            hash = category.charCodeAt(i) + ((hash << 5) - hash);
        }
        const index = Math.abs(hash) % categoryPalette.length;
        return categoryPalette[index];
    }

    let openRating: { target: "activity" | "provider"; targetId: number; targetLabel: string } | null = null;

    function openActivityRating(marker: (typeof markers)[number]) {
        openRating = { target: "activity", targetId: marker.id, targetLabel: marker.name };
    }

    function openProviderRating(marker: (typeof markers)[number]) {
        if (marker.providerId == null) return;
        openRating = { target: "provider", targetId: marker.providerId, targetLabel: "Anbieter" };
    }

    function handleRated() {
        openRating = null;
        dispatch("refresh");
    }
</script>

<div class="list">
    {#each markers as marker}
        <div class="card">
            <div class="card-header">
                <strong>{marker.name}</strong>
                {#if marker.category}
                    <span
                        class="tag"
                        style="background:{categoryColor(marker.category).bg}; color:{categoryColor(marker.category).text};"
                    >{marker.category}</span>
                {/if}
            </div>
            <p class="address">{marker.address}</p>
            <p class="date">{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
            <div class="ratings">
                <button class="rating-badge" on:click={() => openActivityRating(marker)}>
                    {marker.activityRating != null ? `★ ${marker.activityRating.toFixed(1)} (${marker.activityRatingCount})` : "Noch keine Bewertung"}
                </button>
                {#if marker.providerId != null}
                    <button class="rating-badge" on:click={() => openProviderRating(marker)}>
                        Anbieter: {marker.providerRating != null ? `★ ${marker.providerRating.toFixed(1)} (${marker.providerRatingCount})` : "Noch keine Bewertung"}
                    </button>
                {/if}
            </div>
        </div>
    {/each}
    {#if markers.length === 0}
        <p class="empty">Keine Aktivitäten gefunden.</p>
    {/if}
</div>

{#if openRating}
    <RatingModal
        target={openRating.target}
        targetId={openRating.targetId}
        targetLabel={openRating.targetLabel}
        on:close={() => (openRating = null)}
        on:rated={handleRated}
    />
{/if}

<style>
    .list {
        display: flex;
        flex-direction: column;
        gap: 8px;
    }
    .card {
        background: var(--color-bg);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        padding: 10px 12px;
    }
    .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 8px;
    }
    .tag {
        font-size: 0.7rem;
        padding: 2px 8px;
        border-radius: var(--radius-pill);
        white-space: nowrap;
    }
    .address,
    .date {
        margin: 4px 0 0;
        font-size: 0.85rem;
        color: var(--color-text-muted);
    }
    .ratings {
        display: flex;
        gap: 6px;
        margin-top: 6px;
        flex-wrap: wrap;
    }
    .rating-badge {
        font-size: 0.75rem;
        padding: 3px 8px;
        border-radius: var(--radius-pill);
        border: 1px solid var(--color-border);
        background: var(--color-surface);
        color: var(--color-text);
        cursor: pointer;
    }
    .rating-badge:hover {
        border-color: var(--color-primary);
    }
    .empty {
        color: var(--color-text-muted);
        font-size: 0.85rem;
        text-align: center;
        padding: 12px 0;
    }
</style>
```

- [ ] **Step 2: `Map.svelte` — `VolunteerList` auf `refresh` reagieren lassen**

In `frontend/src/lib/Map.svelte`, line 113 currently reads:

```svelte
                <VolunteerList {markers} />
```

Replace it with:

```svelte
                <VolunteerList {markers} on:refresh={fetchMarkers} />
```

(`fetchMarkers` already exists in this file's `<script>` block from the
existing search/filter wiring — no new function needed.)

- [ ] **Step 3: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler in `VolunteerList.svelte` oder `Map.svelte`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/VolunteerList.svelte frontend/src/lib/Map.svelte
git commit -m "feat: show rating badges in VolunteerList and wire up RatingModal"
```

---

### Task 7: End-to-End-Verifikation

**Context:** Kompletter manueller Rundgang durch den Bewertungs-Flow mit
laufendem Backend und Frontend.

**Files:** keine (nur Verifikation)

- [ ] **Step 1: Volle svelte-check-Prüfung**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler/Warnungen gegenüber dem Stand vor diesem Plan
(bekannte Altlasten wie der `FilterBar.svelte`-Fehler und die
`Router.svelte`-Warnung zählen nicht als neu).

- [ ] **Step 2: Backend-Tests unverändert grün**

Run: `cd backend && ./gradlew.bat test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Visueller Rundgang im Browser**

Mit laufendem Backend (`./gradlew.bat bootRun`) und Frontend
(`npm run dev`):

- `/`: Bottom-Sheet öffnen, jede Karte zeigt zwei Sterne-Badges
  ("Noch keine Bewertung" initial, da neu erzeugte Testdaten unbewertet
  sind)
- Ausgeloggt auf ein Sterne-Badge klicken: Modal öffnet, zeigt
  Bewertungsliste (leer) und stattdessen unten den Login/Registrieren-
  Hinweis statt eines Formulars
- Als `USER` registrieren, zurück zur Karte, Aktivitäts-Sterne-Badge
  klicken: Formular sichtbar, 4 Sterne + Kommentar abschicken → Modal
  zeigt die neue Bewertung in der Liste, Badge in der Karte aktualisiert
  sich nach Schließen des Modals (Refresh via `on:refresh`)
- Dieselbe Aktivität erneut bewerten (5 Sterne, anderer Kommentar): Modal
  beim Öffnen mit der vorherigen Bewertung vorausgefüllt; nach Speichern
  zeigt die Liste nur einen Eintrag mit dem neuen Wert (kein Duplikat)
- Anbieter-Sterne-Badge einer Aktivität mit `createdBy` klicken (dafür
  vorher als `ANBIETER` eine Aktivität über `/add` anlegen, dann als
  anderer `USER` bewerten): funktioniert unabhängig von der
  Aktivitäts-Bewertung
- Als `ANBIETER` eingeloggt ein Sterne-Badge öffnen: Formular nicht
  sichtbar, stattdessen der Login/Registrieren-Hinweis (auch wenn man
  selbst eingeloggt ist, nur eben mit falscher Rolle)
- `stars`-Wert 6 oder 0 direkt per `curl` an `/activities/{id}/ratings`
  senden (mit gültigem Session-Cookie): `400`

- [ ] **Step 4: Report**

Zusammenfassung Pass/Fail für Schritt 1-3. Wenn alles passt: Bewertungssystem ist fertig.
