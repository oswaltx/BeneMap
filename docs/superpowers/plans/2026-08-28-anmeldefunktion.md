# Anmeldefunktion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let logged-in volunteers (Rolle `USER`) sign up for an app-native activity directly in the app ("Ich mache mit"), and let the owning provider (`ANBIETER`) see who signed up.

**Architecture:** A new join entity `ActivitySignup` (User↔VolunteerActivity, unique per pair), mirroring the existing `ActivityRating` pattern exactly. A new `SignupController` exposes sign-up/withdraw/status endpoints, gated the same way ratings already are (`hasRole("USER")` for the mutating endpoints, public GET). The signup count and optional capacity flow through the existing `Marker` DTO so the UI can show a badge without an extra request; the full participant list (name + email) is fetched lazily and only populated server-side when the requester is the activity's own provider.

**Tech Stack:** Kotlin/Spring Boot/JPA/Spring Security (backend), Svelte 5 (frontend), JUnit 5 + Mockito-Kotlin + Spring MockMvc (backend tests).

## Global Constraints

- Only Rolle `USER` may sign up or withdraw — same restriction as `POST /activities/*/ratings` in `SecurityConfig.kt`.
- The provider sees Name **and** E-Mail of participants — not just the name.
- `maxParticipants: Int?` on `VolunteerActivity` — `null` means unlimited. Signing up once the limit is reached is rejected with `409 Conflict`.
- No chat/messaging, no notifications, no waitlist, no removing participants, and **no support for Städtische Angebote** — the signup badge only ever renders when `marker.providerId != null`.
- `ErrorResponse(val error: String)` already exists in `AuthController.kt` (same package) — reuse it, do not redeclare it.

---

### Task 1: Signup data model and API

**Files:**
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/ActivitySignup.kt`
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/ActivitySignupRepository.kt`
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/SignupController.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/SignupControllerTest.kt`

**Interfaces:**
- Consumes: `VolunteerActivityRepository`, `UserRepository` (existing), `ErrorResponse` (existing, from `AuthController.kt`).
- Produces: `ActivitySignup(id, user, activity, createdAt)` entity. `ActivitySignupRepository` with `findByUserAndActivity(user, activity): ActivitySignup?`, `findByActivity(activity): List<ActivitySignup>`, `countByActivity(activity): Long`. `VolunteerActivity.maxParticipants: Int?`. These are consumed by Task 2 (`MainController`/`Marker`).

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/kotlin/com/example/VoloMap/server/SignupControllerTest.kt`:

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class SignupControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var activitySignupRepository: ActivitySignupRepository

    @BeforeEach
    fun cleanUp() {
        activitySignupRepository.deleteAll()
        activityRepository.deleteAll()
        userRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        activitySignupRepository.deleteAll()
    }

    private fun registerAndSession(email: String, role: String): MockHttpSession {
        val result = mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"geheim123","name":"Test","role":"$role"}""")
        ).andReturn()
        return result.request.session as MockHttpSession
    }

    private fun createActivity(
        name: String = "Testaktion",
        maxParticipants: Int? = null,
        createdBy: User? = null,
    ): Long =
        activityRepository.save(
            VolunteerActivity(name = name, maxParticipants = maxParticipants, createdBy = createdBy)
        ).id

    @Test
    fun `signs up and count reflects it`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        val activityId = createActivity()

        mockMvc.perform(post("/activities/$activityId/signup").session(session))
            .andExpect(status().isOk)

        mockMvc.perform(get("/activities/$activityId/signups").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.signedUp").value(true))
    }

    @Test
    fun `signing up twice is a no-op, not a duplicate`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        val activityId = createActivity()

        mockMvc.perform(post("/activities/$activityId/signup").session(session)).andExpect(status().isOk)
        mockMvc.perform(post("/activities/$activityId/signup").session(session)).andExpect(status().isOk)

        mockMvc.perform(get("/activities/$activityId/signups").session(session))
            .andExpect(jsonPath("$.count").value(1))
    }

    @Test
    fun `withdraws a signup`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        val activityId = createActivity()
        mockMvc.perform(post("/activities/$activityId/signup").session(session)).andExpect(status().isOk)

        mockMvc.perform(delete("/activities/$activityId/signup").session(session))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/activities/$activityId/signups").session(session))
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.signedUp").value(false))
    }

    @Test
    fun `withdrawing without a prior signup is a no-op`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        val activityId = createActivity()

        mockMvc.perform(delete("/activities/$activityId/signup").session(session))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `signup is rejected once maxParticipants is reached`() {
        val activityId = createActivity(maxParticipants = 1)
        val sessionA = registerAndSession("a@example.com", "USER")
        val sessionB = registerAndSession("b@example.com", "USER")

        mockMvc.perform(post("/activities/$activityId/signup").session(sessionA))
            .andExpect(status().isOk)

        mockMvc.perform(post("/activities/$activityId/signup").session(sessionB))
            .andExpect(status().isConflict)

        mockMvc.perform(get("/activities/$activityId/signups"))
            .andExpect(jsonPath("$.count").value(1))
    }

    @Test
    fun `signing up without a session is rejected`() {
        val activityId = createActivity()
        mockMvc.perform(post("/activities/$activityId/signup"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `signing up as ANBIETER is rejected`() {
        val session = registerAndSession("anbieter@example.com", "ANBIETER")
        val activityId = createActivity()
        mockMvc.perform(post("/activities/$activityId/signup").session(session))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `signing up for a nonexistent activity returns 404`() {
        val session = registerAndSession("volunteer@example.com", "USER")
        mockMvc.perform(post("/activities/999999/signup").session(session))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `participants list is only visible to the activity's own provider`() {
        val ownerSession = registerAndSession("anbieter@example.com", "ANBIETER")
        val owner = userRepository.findByEmail("anbieter@example.com")!!
        val activityId = createActivity(createdBy = owner)

        val volunteerSession = registerAndSession("volunteer@example.com", "USER")
        mockMvc.perform(post("/activities/$activityId/signup").session(volunteerSession))
            .andExpect(status().isOk)

        mockMvc.perform(get("/activities/$activityId/signups").session(ownerSession))
            .andExpect(jsonPath("$.participants[0].name").value("Test"))
            .andExpect(jsonPath("$.participants[0].email").value("volunteer@example.com"))

        mockMvc.perform(get("/activities/$activityId/signups").session(volunteerSession))
            .andExpect(jsonPath("$.participants.length()").value(0))

        mockMvc.perform(get("/activities/$activityId/signups"))
            .andExpect(jsonPath("$.participants.length()").value(0))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.SignupControllerTest"`
Expected: FAIL — compile error, since `ActivitySignup`, `ActivitySignupRepository`, the `/activities/*/signup(s)` endpoints, and `VolunteerActivity.maxParticipants` don't exist yet.

- [ ] **Step 3: Add the `maxParticipants` field to `VolunteerActivity`**

Open `backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt`. Find the last constructor parameter, `createdBy` (currently the last lines before the closing paren):

```kotlin
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "created_by")
    var createdBy: User? = null,
)
```

Replace with:

```kotlin
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "created_by")
    var createdBy: User? = null,

    // Optionale Teilnehmer-Obergrenze für die Anmeldefunktion. null = unbegrenzt.
    var maxParticipants: Int? = null,
)
```

- [ ] **Step 4: Create the `ActivitySignup` entity**

Create `backend/src/main/kotlin/com/example/VoloMap/server/ActivitySignup.kt`:

```kotlin
package com.example.VoloMap.server

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
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
    name = "activity_signups",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "activity_id"])]
)
class ActivitySignup(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    var activity: VolunteerActivity,

    var createdAt: Instant = Instant.now(),
)
```

- [ ] **Step 5: Create the `ActivitySignupRepository`**

Create `backend/src/main/kotlin/com/example/VoloMap/server/ActivitySignupRepository.kt`:

```kotlin
package com.example.VoloMap.server

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ActivitySignupRepository : JpaRepository<ActivitySignup, Long> {
    fun findByUserAndActivity(user: User, activity: VolunteerActivity): ActivitySignup?
    fun findByActivity(activity: VolunteerActivity): List<ActivitySignup>
    fun countByActivity(activity: VolunteerActivity): Long
}
```

- [ ] **Step 6: Create the `SignupController`**

Create `backend/src/main/kotlin/com/example/VoloMap/server/SignupController.kt`:

```kotlin
package com.example.VoloMap.server

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

data class SignupEntry(val name: String, val email: String)

data class SignupStatusResponse(
    val count: Int,
    val maxParticipants: Int?,
    val signedUp: Boolean,
    val participants: List<SignupEntry>,
)

@RestController
class SignupController(
    private val activityRepository: VolunteerActivityRepository,
    private val userRepository: UserRepository,
    private val activitySignupRepository: ActivitySignupRepository,
) {

    @PostMapping("/activities/{id}/signup")
    fun signUp(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<*> {
        val activity = activityRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
        val user = userRepository.findByEmail(authentication.name)!!

        if (activitySignupRepository.findByUserAndActivity(user, activity) != null) {
            return ResponseEntity.ok().build<Any>()
        }

        val max = activity.maxParticipants
        if (max != null && activitySignupRepository.countByActivity(activity) >= max) {
            return ResponseEntity.status(409).body(ErrorResponse("Diese Aktivität ist bereits ausgebucht."))
        }

        activitySignupRepository.save(ActivitySignup(user = user, activity = activity))
        return ResponseEntity.ok().build<Any>()
    }

    @DeleteMapping("/activities/{id}/signup")
    fun withdraw(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val activity = activityRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val user = userRepository.findByEmail(authentication.name)!!

        activitySignupRepository.findByUserAndActivity(user, activity)?.let {
            activitySignupRepository.delete(it)
        }
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/activities/{id}/signups")
    fun getSignups(
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<SignupStatusResponse> {
        val activity = activityRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val me = authentication?.let { userRepository.findByEmail(it.name) }
        val signedUp = me?.let { activitySignupRepository.findByUserAndActivity(it, activity) != null } ?: false
        val isOwner = me != null && activity.createdBy?.id == me.id

        val signups = activitySignupRepository.findByActivity(activity)
        val participants = if (isOwner) signups.map { SignupEntry(it.user.name, it.user.email) } else emptyList()

        return ResponseEntity.ok(
            SignupStatusResponse(
                count = signups.size,
                maxParticipants = activity.maxParticipants,
                signedUp = signedUp,
                participants = participants,
            )
        )
    }
}
```

- [ ] **Step 7: Wire the new endpoints into `SecurityConfig`**

Open `backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt`. Find the `authorizeHttpRequests` block (currently lines 72-79):

```kotlin
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/", "/markers", "/categories", "/activities/*/ratings", "/providers/*/ratings").permitAll()
                it.requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                it.requestMatchers(HttpMethod.POST, "/add", "/add-recurring").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.PUT, "/activities/*").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.DELETE, "/activities/*").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.POST, "/activities/*/ratings", "/providers/*/ratings").hasRole("USER")
                it.anyRequest().authenticated()
            }
```

Replace with:

```kotlin
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/", "/markers", "/categories", "/activities/*/ratings", "/providers/*/ratings", "/activities/*/signups").permitAll()
                it.requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                it.requestMatchers(HttpMethod.POST, "/add", "/add-recurring").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.PUT, "/activities/*").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.DELETE, "/activities/*").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.POST, "/activities/*/ratings", "/providers/*/ratings", "/activities/*/signup").hasRole("USER")
                it.requestMatchers(HttpMethod.DELETE, "/activities/*/signup").hasRole("USER")
                it.anyRequest().authenticated()
            }
```

(`/activities/*` only matches one path segment after `/activities/`, so the existing `DELETE /activities/*` rule for `hasRole("ANBIETER")` — which governs deleting the activity itself — does not match `/activities/{id}/signup` and is unaffected.)

- [ ] **Step 8: Run the tests to verify they pass**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.SignupControllerTest"`
Expected: PASS (all 9 tests).

- [ ] **Step 9: Run the full backend test suite**

Run: `cd backend && ./gradlew.bat test`
Expected: PASS — this task doesn't touch `MainController`, `Marker`, or any file that other tests construct, so nothing else should be affected.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/ActivitySignup.kt backend/src/main/kotlin/com/example/VoloMap/server/ActivitySignupRepository.kt backend/src/main/kotlin/com/example/VoloMap/server/SignupController.kt backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt backend/src/test/kotlin/com/example/VoloMap/server/SignupControllerTest.kt
git commit -m "feat: add activity signup data model and API"
```

---

### Task 2: Expose signup counts through the markers API and wire capacity into add/edit

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerDeleteActivityTest.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddRecurringActivityTest.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditActivityTest.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt` (constructor-arg fix only, no new test)

**Interfaces:**
- Consumes: `ActivitySignupRepository` (from Task 1), `VolunteerActivity.maxParticipants` (from Task 1).
- Produces: `Marker.signupCount: Int`, `Marker.maxParticipants: Int?`. Task 3's frontend reads these two fields by these exact names via the `/markers` JSON response.

This task adds a 6th constructor parameter to `MainController`. Every existing test file that constructs `MainController(...)` directly with 5 positional arguments needs a `mock()` appended as the 6th argument — there are 42 such call sites across 5 test files (`MainControllerAddActivityTest.kt`, `MainControllerAddRecurringActivityTest.kt`, `MainControllerDeleteActivityTest.kt`, `MainControllerEditActivityTest.kt`, `MainControllerMarkersTest.kt`). Step 5 below fixes all of them mechanically with `sed`.

- [ ] **Step 1: Write the new failing tests**

Open `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt`. Add this test right after the existing `provider photo and website are null without an owner` test (uses the file's existing `activity(...)` helper):

```kotlin
    @Test
    fun `includes signupCount and maxParticipants for an activity`() {
        val repository = mock<VolunteerActivityRepository>()
        val activitySignupRepository = mock<ActivitySignupRepository>()
        val provider = User(id = 7, email = "anbieter@example.com", passwordHash = "x", name = "Anbieter Anna", role = Role.ANBIETER)
        val withLimit = activity(
            name = "Begrenzte Aktion",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        ).also { it.createdBy = provider; it.maxParticipants = 5 }
        whenever(repository.findAll()).thenReturn(listOf(withLimit))
        whenever(activitySignupRepository.findAll()).thenReturn(
            listOf(
                ActivitySignup(user = mock(), activity = withLimit),
                ActivitySignup(user = mock(), activity = withLimit),
            )
        )

        val controller = MainController(repository, mock(), mock(), mock(), mock(), activitySignupRepository)
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals(2, result[0].signupCount)
        assertEquals(5, result[0].maxParticipants)
    }

    @Test
    fun `signupCount is zero and maxParticipants is null without any signups or limit`() {
        val repository = mock<VolunteerActivityRepository>()
        val activitySignupRepository = mock<ActivitySignupRepository>()
        val unlimited = activity(
            name = "Offene Aktion",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        whenever(repository.findAll()).thenReturn(listOf(unlimited))
        whenever(activitySignupRepository.findAll()).thenReturn(emptyList())

        val controller = MainController(repository, mock(), mock(), mock(), mock(), activitySignupRepository)
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals(0, result[0].signupCount)
        assertNull(result[0].maxParticipants)
    }
```

Open `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerDeleteActivityTest.kt`. Add this test right after the existing `owner can delete their activity, ratings are removed first` test:

```kotlin
    @Test
    fun `owner can delete their activity, signups are removed first`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val activitySignupRepository: ActivitySignupRepository = mock()
        val existing = VolunteerActivity(id = 5, name = "Alt", createdBy = owner)
        val signups = listOf(
            ActivitySignup(id = 10, user = owner, activity = existing)
        )
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(activitySignupRepository.findByActivity(existing)).thenReturn(signups)

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock(), activitySignupRepository)
        val result = controller.deleteActivity(5, authenticationFor(owner.email))

        assertEquals(204, result.statusCode.value())
        verify(activitySignupRepository).deleteAll(signups)
        verify(repository).delete(existing)
    }
```

Open `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddRecurringActivityTest.kt`. Add this test right after the existing `weekly interval produces one occurrence per week within the 3-month horizon` test:

```kotlin
    @Test
    fun `passes maxParticipants through to every created occurrence`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock(), mock())
        val req = AddRecurringActivityRequest(
            name = "Sprachcafé",
            dateTime = LocalDateTime.parse("2026-08-15T10:00:00"),
            recurrenceIntervalDays = 7,
            maxParticipants = 3
        )

        @Suppress("UNCHECKED_CAST")
        val result = controller.addRecurringActivity(req, authenticationFor(provider.email))
        val activities = result.body as List<VolunteerActivity>

        assertTrue(activities.all { it.maxParticipants == 3 })
    }
```

Open `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditActivityTest.kt`. Add this test right after the existing `owner can update name without touching coordinates when address is unchanged` test:

```kotlin
    @Test
    fun `updates maxParticipants`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        val existing = VolunteerActivity(id = 5, name = "Alt", createdBy = owner)
        whenever(repository.findById(5)).thenReturn(Optional.of(existing))
        whenever(userRepository.findByEmail(owner.email)).thenReturn(owner)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository, mock(), mock(), mock())
        val req = UpdateActivityRequest(name = "Neu", maxParticipants = 8)

        val result = controller.updateActivity(5, req, authenticationFor(owner.email))

        val body = result.body as UpdateActivityResponse
        assertEquals(8, body.activity.maxParticipants)
    }
```

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerMarkersTest" --tests "com.example.VoloMap.server.MainControllerDeleteActivityTest" --tests "com.example.VoloMap.server.MainControllerAddRecurringActivityTest" --tests "com.example.VoloMap.server.MainControllerEditActivityTest"`
Expected: FAIL — compile errors (`Marker.signupCount`/`maxParticipants` don't exist, `MainController` doesn't take 6 arguments, `AddRecurringActivityRequest`/`UpdateActivityRequest` don't have `maxParticipants`).

- [ ] **Step 3: Add `signupCount`/`maxParticipants` to `Marker`**

Open `backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt`. Find the last two properties (currently the end of the class, lines 23-24):

```kotlin
    val sourceContactPhone: String?,
)
```

Replace with:

```kotlin
    val sourceContactPhone: String?,
    val signupCount: Int,
    val maxParticipants: Int?,
)
```

- [ ] **Step 4: Wire everything into `MainController`**

Open `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`.

Add the new constructor parameter (currently lines 18-24):

```kotlin
class MainController(
    private val repository: VolunteerActivityRepository,
    private val geocodingService: GeocodingService,
    private val userRepository: UserRepository,
    private val activityRatingRepository: ActivityRatingRepository,
    private val providerRatingRepository: ProviderRatingRepository,
) {
```

becomes:

```kotlin
class MainController(
    private val repository: VolunteerActivityRepository,
    private val geocodingService: GeocodingService,
    private val userRepository: UserRepository,
    private val activityRatingRepository: ActivityRatingRepository,
    private val providerRatingRepository: ProviderRatingRepository,
    private val activitySignupRepository: ActivitySignupRepository,
) {
```

In `markers()`, find the two `groupBy` lines (currently lines 41-42):

```kotlin
        val activityRatingsByActivityId = activityRatingRepository.findAll().groupBy { it.activity.id }
        val providerRatingsByProviderId = providerRatingRepository.findAll().groupBy { it.provider.id }
```

Add a third line right after:

```kotlin
        val activityRatingsByActivityId = activityRatingRepository.findAll().groupBy { it.activity.id }
        val providerRatingsByProviderId = providerRatingRepository.findAll().groupBy { it.provider.id }
        val signupsByActivityId = activitySignupRepository.findAll().groupBy { it.activity.id }
```

Inside the `.map { activity -> ... }` block, find where `activityRatings` is computed (currently line 48) and add a `signups` line right after it:

```kotlin
                val activityRatings = activityRatingsByActivityId[activity.id].orEmpty()
```

becomes:

```kotlin
                val activityRatings = activityRatingsByActivityId[activity.id].orEmpty()
                val signups = signupsByActivityId[activity.id].orEmpty()
```

Find the `Marker(...)` construction's last two lines (currently `sourceContactPhone = activity.sourceContactPhone,` followed by `)`):

```kotlin
                    sourceContactPhone = activity.sourceContactPhone,
                )
```

Replace with:

```kotlin
                    sourceContactPhone = activity.sourceContactPhone,
                    signupCount = signups.size,
                    maxParticipants = activity.maxParticipants,
                )
```

In `deleteActivity()`, find the existing rating cleanup line (currently line 223):

```kotlin
        activityRatingRepository.deleteAll(activityRatingRepository.findByActivity(activity))
        repository.delete(activity)
```

Replace with:

```kotlin
        activityRatingRepository.deleteAll(activityRatingRepository.findByActivity(activity))
        activitySignupRepository.deleteAll(activitySignupRepository.findByActivity(activity))
        repository.delete(activity)
```

Find `UpdateActivityRequest` (currently lines 242-249):

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

Replace with:

```kotlin
data class UpdateActivityRequest(
    val name: String,
    val description: String? = null,
    val addressText: String? = null,
    val category: String? = null,
    val dateTime: LocalDateTime? = null,
    val photoUrls: String? = null,
    val maxParticipants: Int? = null,
)
```

In `updateActivity()`, find where `photoUrls` is assigned (currently line 181):

```kotlin
        activity.photoUrls = normalizePhotoUrls(req.photoUrls)
```

Add a line right after it:

```kotlin
        activity.photoUrls = normalizePhotoUrls(req.photoUrls)
        activity.maxParticipants = req.maxParticipants
```

Find `AddRecurringActivityRequest` (currently lines 255-263):

```kotlin
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

Replace with:

```kotlin
data class AddRecurringActivityRequest(
    val name: String,
    val description: String? = null,
    val addressText: String? = null,
    val category: String? = null,
    val dateTime: LocalDateTime,
    val photoUrls: String? = null,
    val recurrenceIntervalDays: Int,
    val maxParticipants: Int? = null,
)
```

In `addRecurringActivity()`, find the `VolunteerActivity(...)` construction inside `occurrenceDates.map { ... }` (currently lines 148-158):

```kotlin
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
```

Replace with:

```kotlin
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
                    maxParticipants = req.maxParticipants,
                )
```

Note: `addActivity()` (`POST /add`) needs no code change — it binds `VolunteerActivity` directly from the request body, so `maxParticipants` is already accepted once the entity field exists (from Task 1).

- [ ] **Step 5: Fix the 39 existing `MainController(...)` call sites**

Every one of these calls currently passes exactly 5 positional arguments; each needs `mock()` appended as the 6th. Run these commands from the repo root (Git Bash):

```bash
sed -i 's/MainController(repository, geocodingService, userRepository, mock(), mock())/MainController(repository, geocodingService, userRepository, mock(), mock(), mock())/g' backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt

sed -i 's/MainController(repository, geocodingService, userRepository, mock(), mock())/MainController(repository, geocodingService, userRepository, mock(), mock(), mock())/g' backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddRecurringActivityTest.kt

sed -i 's/MainController(repository, geocodingService, userRepository, activityRatingRepository, mock())/MainController(repository, geocodingService, userRepository, activityRatingRepository, mock(), mock())/g' backend/src/test/kotlin/com/example/VoloMap/server/MainControllerDeleteActivityTest.kt
sed -i 's/MainController(repository, geocodingService, userRepository, mock(), mock())/MainController(repository, geocodingService, userRepository, mock(), mock(), mock())/g' backend/src/test/kotlin/com/example/VoloMap/server/MainControllerDeleteActivityTest.kt

sed -i 's/MainController(repository, geocodingService, userRepository, activityRatingRepository, mock())/MainController(repository, geocodingService, userRepository, activityRatingRepository, mock(), mock())/g' backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditActivityTest.kt
sed -i 's/MainController(repository, geocodingService, userRepository, mock(), mock())/MainController(repository, geocodingService, userRepository, mock(), mock(), mock())/g' backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditActivityTest.kt

sed -i 's/MainController(repository, mock(), mock(), mock(), mock())/MainController(repository, mock(), mock(), mock(), mock(), mock())/g' backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt
sed -i 's/MainController(repository, mock(), mock(), activityRatingRepository, providerRatingRepository)/MainController(repository, mock(), mock(), activityRatingRepository, providerRatingRepository, mock())/g' backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt
```

The two new tests you wrote in Step 1 for `MainControllerMarkersTest.kt`, `MainControllerDeleteActivityTest.kt`, `MainControllerAddRecurringActivityTest.kt`, and `MainControllerEditActivityTest.kt` already use the correct 6-argument form directly and are untouched by these substitutions (their call sites don't match the 5-argument patterns above).

After running these, verify no old 5-argument call sites remain:

```bash
grep -rn "MainController(repository, geocodingService, userRepository, mock(), mock())$" backend/src/test/
grep -rn "MainController(repository, mock(), mock(), mock(), mock())$" backend/src/test/
```

Expected: no output (both greps find nothing).

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerMarkersTest" --tests "com.example.VoloMap.server.MainControllerDeleteActivityTest" --tests "com.example.VoloMap.server.MainControllerAddRecurringActivityTest" --tests "com.example.VoloMap.server.MainControllerEditActivityTest"`
Expected: PASS (all tests in all four files, including the new ones).

- [ ] **Step 7: Run the full backend test suite**

Run: `cd backend && ./gradlew.bat test`
Expected: PASS — no regressions anywhere else.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerDeleteActivityTest.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddRecurringActivityTest.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerEditActivityTest.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt
git commit -m "feat: expose signup counts and participant limits through the markers API"
```

---

### Task 3: Frontend — sign-up UI

**Files:**
- Create: `frontend/src/lib/SignupModal.svelte`
- Modify: `frontend/src/lib/PinDetailPanel.svelte`
- Modify: `frontend/src/lib/AddActivity.svelte`
- Modify: `frontend/src/lib/EditActivityModal.svelte`

**Interfaces:**
- Consumes: `GET /activities/{id}/signups` → `{count, maxParticipants, signedUp, participants: [{name, email}]}`; `POST /activities/{id}/signup`; `DELETE /activities/{id}/signup` (all from Task 1). `marker.signupCount`/`marker.maxParticipants`/`marker.providerId` (from Task 2's `/markers` response). `$currentUser` store from `../auth` (existing).
- Produces: nothing consumed by later tasks — this is the last task in the plan.

- [ ] **Step 1: Create `SignupModal.svelte`**

Create `frontend/src/lib/SignupModal.svelte`:

```svelte
<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import Link from "./Link.svelte";
    import { currentUser, fetchWithSessionCheck } from "../auth";

    export let activityId: number;
    export let isOwner: boolean;

    const dispatch = createEventDispatcher<{ close: void; changed: void }>();

    interface SignupEntry {
        name: string;
        email: string;
    }

    interface SignupStatusResponse {
        count: number;
        maxParticipants: number | null;
        signedUp: boolean;
        participants: SignupEntry[];
    }

    let count = 0;
    let maxParticipants: number | null = null;
    let signedUp = false;
    let participants: SignupEntry[] = [];
    let loading = true;
    let loadError: string | null = null;
    let submitting = false;
    let submitError: string | null = null;

    async function loadStatus() {
        loading = true;
        loadError = null;
        try {
            const res = await fetch(`http://localhost:8080/activities/${activityId}/signups`, { credentials: "include" });
            if (!res.ok) throw new Error("Request failed");
            const data: SignupStatusResponse = await res.json();
            count = data.count;
            maxParticipants = data.maxParticipants;
            signedUp = data.signedUp;
            participants = data.participants;
        } catch (e) {
            loadError = "Teilnahme-Status konnte nicht geladen werden.";
        } finally {
            loading = false;
        }
    }

    loadStatus();

    $: full = maxParticipants != null && count >= maxParticipants && !signedUp;

    async function handleToggle() {
        submitting = true;
        submitError = null;
        try {
            const res = await fetchWithSessionCheck(`http://localhost:8080/activities/${activityId}/signup`, {
                method: signedUp ? "DELETE" : "POST",
                credentials: "include",
            });
            if (!res.ok) {
                submitError = signedUp
                    ? "Abmelden fehlgeschlagen. Bitte versuche es erneut."
                    : res.status === 409
                        ? "Diese Aktivität ist bereits ausgebucht."
                        : "Anmelden fehlgeschlagen. Bitte versuche es erneut.";
                return;
            }
            await loadStatus();
            dispatch("changed");
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
        <h3>Teilnahme</h3>
        <button class="close" on:click={() => dispatch("close")} aria-label="Schließen">×</button>
    </div>

    {#if loading}
        <p>Lädt…</p>
    {:else if loadError}
        <p class="warning">{loadError}</p>
    {:else}
        <p class="summary">
            {#if maxParticipants != null}
                {count} von {maxParticipants} Plätzen belegt
            {:else}
                {count} Teilnehmende
            {/if}
        </p>

        {#if isOwner}
            {#if participants.length === 0}
                <p class="notice">Noch niemand angemeldet.</p>
            {:else}
                <div class="participant-list">
                    {#each participants as p}
                        <div class="participant-entry">
                            <strong>{p.name}</strong>
                            <span class="participant-email">{p.email}</span>
                        </div>
                    {/each}
                </div>
            {/if}
        {:else if $currentUser?.role === "USER"}
            <button type="button" on:click={handleToggle} disabled={submitting || full}>
                {#if submitting}
                    Speichert…
                {:else if signedUp}
                    Angemeldet ✓ — Zurückziehen
                {:else if full}
                    Ausgebucht
                {:else}
                    Ich mache mit
                {/if}
            </button>
            {#if submitError}<p class="warning">{submitError}</p>{/if}
        {:else}
            <p class="notice">
                Nur eingeloggte User können sich anmelden.
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

    .participant-list {
        display: flex;
        flex-direction: column;
        gap: 8px;
        max-height: 200px;
        overflow-y: auto;
    }

    .participant-entry {
        display: flex;
        flex-direction: column;
        border-top: 1px solid var(--color-border);
        padding-top: 8px;
        font-size: 0.85rem;
    }

    .participant-entry:first-child {
        border-top: none;
        padding-top: 0;
    }

    .participant-email {
        color: var(--color-text-muted);
        font-size: 0.8rem;
    }

    button[type="button"] {
        align-self: flex-start;
    }

    button[type="button"]:disabled {
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

- [ ] **Step 2: Wire the signup badge into `PinDetailPanel.svelte`**

Open `frontend/src/lib/PinDetailPanel.svelte`. Add the import (right after the existing `EditActivityModal` import, currently line 4):

```svelte
    import EditActivityModal from "./EditActivityModal.svelte";
```

becomes:

```svelte
    import EditActivityModal from "./EditActivityModal.svelte";
    import SignupModal from "./SignupModal.svelte";
```

Add two new fields to the `marker` type (right after `sourceContactPhone`, currently line 21):

```svelte
        sourceContactPhone: string | null;
        activityRating: number | null;
```

becomes:

```svelte
        sourceContactPhone: string | null;
        signupCount: number;
        maxParticipants: number | null;
        activityRating: number | null;
```

Add a `showSignup` state flag right after the existing `openRating` declaration (currently line 34):

```svelte
    let openRating: { target: "activity" | "provider"; targetId: number; targetLabel: string } | null = null;
```

becomes:

```svelte
    let openRating: { target: "activity" | "provider"; targetId: number; targetLabel: string } | null = null;
    let showSignup = false;
```

Add the badge in the template, right after the existing rating badge (currently lines 128-130):

```svelte
    <button class="rating-badge" on:click={openActivityRating}>
        {marker.activityRating != null ? `★ ${marker.activityRating.toFixed(1)} (${marker.activityRatingCount})` : "Noch keine Bewertung"}
    </button>
```

becomes:

```svelte
    <button class="rating-badge" on:click={openActivityRating}>
        {marker.activityRating != null ? `★ ${marker.activityRating.toFixed(1)} (${marker.activityRatingCount})` : "Noch keine Bewertung"}
    </button>

    {#if marker.providerId != null}
        <button class="rating-badge" on:click={() => (showSignup = true)}>
            {marker.maxParticipants != null
                ? `👥 ${marker.signupCount}/${marker.maxParticipants} Teilnehmende`
                : `👥 ${marker.signupCount} Teilnehmende`}
        </button>
    {/if}
```

Add the modal render block right after the existing `{#if openRating}` block closes (currently lines 150-158, ending `{/if}`):

```svelte
{#if openRating}
    <RatingModal
        target={openRating.target}
        targetId={openRating.targetId}
        targetLabel={openRating.targetLabel}
        on:close={() => (openRating = null)}
        on:rated={handleRated}
    />
{/if}
```

Add right after it:

```svelte
{#if showSignup}
    <SignupModal
        activityId={marker.id}
        isOwner={isOwner}
        on:close={() => (showSignup = false)}
        on:changed={() => dispatch("refresh")}
    />
{/if}
```

- [ ] **Step 3: Add the `maxParticipants` field to `AddActivity.svelte`**

Open `frontend/src/lib/AddActivity.svelte`. Add state (right after `let photoUrlsText = "";`, currently line 10):

```svelte
    let photoUrlsText = "";
```

becomes:

```svelte
    let photoUrlsText = "";
    let maxParticipants = "";
```

Add it to `baseBody` (currently lines 41-48):

```svelte
        const baseBody = {
            name,
            description: description || null,
            addressText: addressText || null,
            category: category || null,
            dateTime: dateTime ? dateTime + ":00" : undefined,
            photoUrls: photoUrlsText.trim() || undefined,
        };
```

becomes:

```svelte
        const baseBody = {
            name,
            description: description || null,
            addressText: addressText || null,
            category: category || null,
            dateTime: dateTime ? dateTime + ":00" : undefined,
            photoUrls: photoUrlsText.trim() || undefined,
            maxParticipants: maxParticipants ? Number(maxParticipants) : null,
        };
```

Reset it after a successful submit, alongside the other field resets (currently lines 95-103):

```svelte
            name = "";
            description = "";
            addressText = "";
            category = "";
            dateTime = "";
            photoUrlsText = "";
            isRecurring = false;
            recurrenceCount = 1;
            recurrenceUnit = "weeks";
```

becomes:

```svelte
            name = "";
            description = "";
            addressText = "";
            category = "";
            dateTime = "";
            photoUrlsText = "";
            maxParticipants = "";
            isRecurring = false;
            recurrenceCount = 1;
            recurrenceUnit = "weeks";
```

Add the field to the template, right after the photo-URLs field (currently lines 151-154):

```svelte
            <label>
                Foto-URLs (eine pro Zeile)
                <textarea bind:value={photoUrlsText} rows="3" placeholder={"https://...\nhttps://..."}></textarea>
            </label>
```

becomes:

```svelte
            <label>
                Foto-URLs (eine pro Zeile)
                <textarea bind:value={photoUrlsText} rows="3" placeholder={"https://...\nhttps://..."}></textarea>
            </label>

            <label>
                Maximale Teilnehmerzahl (optional)
                <input type="number" min="1" bind:value={maxParticipants} placeholder="unbegrenzt" />
            </label>
```

- [ ] **Step 4: Add the `maxParticipants` field to `EditActivityModal.svelte`**

Open `frontend/src/lib/EditActivityModal.svelte`. Add `maxParticipants` to the `marker` prop type (currently lines 5-13):

```svelte
    export let marker: {
        id: number;
        name: string;
        description: string;
        address: string;
        category: string;
        dateTime: string | null;
        photoUrls: string[];
    };
```

becomes:

```svelte
    export let marker: {
        id: number;
        name: string;
        description: string;
        address: string;
        category: string;
        dateTime: string | null;
        photoUrls: string[];
        maxParticipants: number | null;
    };
```

Add state (right after `let photoUrlsText = marker.photoUrls.join("\n");`, currently line 22):

```svelte
    let photoUrlsText = marker.photoUrls.join("\n");
```

becomes:

```svelte
    let photoUrlsText = marker.photoUrls.join("\n");
    let maxParticipants = marker.maxParticipants != null ? String(marker.maxParticipants) : "";
```

Add it to the PUT body (currently lines 43-50):

```svelte
                body: JSON.stringify({
                    name,
                    description: description || null,
                    addressText: addressText || null,
                    category: category || null,
                    dateTime: dateTime ? dateTime + ":00" : undefined,
                    photoUrls: photoUrlsText.trim() || undefined,
                }),
```

becomes:

```svelte
                body: JSON.stringify({
                    name,
                    description: description || null,
                    addressText: addressText || null,
                    category: category || null,
                    dateTime: dateTime ? dateTime + ":00" : undefined,
                    photoUrls: photoUrlsText.trim() || undefined,
                    maxParticipants: maxParticipants ? Number(maxParticipants) : null,
                }),
```

Add the field to the template, right after the photo-URLs field (currently lines 113-116):

```svelte
        <label>
            Foto-URLs (eine pro Zeile)
            <textarea bind:value={photoUrlsText} rows="3" placeholder={"https://...\nhttps://..."}></textarea>
        </label>
```

becomes:

```svelte
        <label>
            Foto-URLs (eine pro Zeile)
            <textarea bind:value={photoUrlsText} rows="3" placeholder={"https://...\nhttps://..."}></textarea>
        </label>

        <label>
            Maximale Teilnehmerzahl (optional)
            <input type="number" min="1" bind:value={maxParticipants} placeholder="unbegrenzt" />
        </label>
```

Finally, back in `PinDetailPanel.svelte`, the `EditActivityModal` is instantiated with an explicit marker object literal (currently line 162):

```svelte
        marker={{ id: marker.id, name: marker.name, description: marker.description, address: marker.address, category: marker.category, dateTime: marker.dateTime, photoUrls: marker.photoUrls }}
```

Replace with:

```svelte
        marker={{ id: marker.id, name: marker.name, description: marker.description, address: marker.address, category: marker.category, dateTime: marker.dateTime, photoUrls: marker.photoUrls, maxParticipants: marker.maxParticipants }}
```

- [ ] **Step 5: Type-check the frontend**

Run: `cd frontend && npm run check`
Expected: PASS — 0 new errors (baseline is 1 pre-existing error in `FilterBar.svelte` and 2 pre-existing warnings in `ClusterMarker.svelte`/`Router.svelte`; nothing from the files this task touches).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/SignupModal.svelte frontend/src/lib/PinDetailPanel.svelte frontend/src/lib/AddActivity.svelte frontend/src/lib/EditActivityModal.svelte
git commit -m "feat: let volunteers sign up for activities in the app"
```

- [ ] **Step 7: Manual verification**

1. Start the backend (`cd backend && ./gradlew.bat bootRun`) and frontend (`cd frontend && npm run dev`).
2. Register two accounts: one `ANBIETER`, one `USER`.
3. As the `ANBIETER`, add an activity with "Maximale Teilnehmerzahl" set to `1`.
4. As the `USER`, open the activity's detail panel, confirm the new "👥 0/1 Teilnehmende" badge is visible, click it, click "Ich mache mit" — confirm it changes to "Angemeldet ✓ — Zurückziehen" and the count updates to `0/1`... (`1/1`).
5. Register a second `USER` account, try to sign up for the same activity — confirm it's rejected with "Diese Aktivität ist bereits ausgebucht."
6. Log back in as the `ANBIETER`, open the same activity, open the signup badge — confirm the first `USER`'s name and email are listed.
7. Confirm the signup badge does **not** appear on a Städtische-Angebote (Köln) pin (toggle "Städtische Angebote (Köln)" on in the filter bar, open one of those pins).
