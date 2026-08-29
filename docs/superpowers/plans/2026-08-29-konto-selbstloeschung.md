# Konto-Selbstlöschung Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let any logged-in user (Ehrenamtler or Anbieter) permanently delete their own account, with password re-confirmation and full cascade cleanup of their dependent data.

**Architecture:** Two new endpoints on the existing `AuthController` — a read-only impact preview and the actual deletion, which re-verifies the password, cascades deletes across the affected repositories (mirroring `MainController.deleteActivity()`'s existing per-activity cascade), deletes the user row, and invalidates the session. The frontend opens this up on the existing `Profile.svelte` page (currently Anbieter-only) to any logged-in user, adding a password-confirmed "Konto löschen" section.

**Tech Stack:** Kotlin/Spring Boot backend (Spring Data JPA, Spring Security), Svelte 5 + TypeScript frontend.

## Global Constraints

- `DELETE /auth/me` requires the current password in the request body and returns `401` with `ErrorResponse("Passwort ist falsch.")` if it doesn't match — nothing is deleted in that case.
- Deleting an Anbieter cascades: each of their `VolunteerActivity` rows (and that activity's `ActivityRating`s and `ActivitySignup`s, exactly like `MainController.deleteActivity()` already does), plus every `ProviderRating` where they are the `provider`.
- Deleting any user (either role) also removes their own `ActivityRating`, `ProviderRating`, and `ActivitySignup` rows (as the `user`, not as the entity being rated/signed-up-for).
- After deletion, the session is invalidated the same way `POST /auth/logout` does it — a reused session must no longer be authenticated.
- No `SecurityConfig` changes — both new endpoints only require being logged in (any role), which the existing `.anyRequest().authenticated()` catch-all already covers.
- No account-deletion UI element is gated by role beyond what's specified below: the "Konto löschen" section is visible to every logged-in user; the existing photo/website form stays Anbieter-only.

---

### Task 1: Backend — deletion-impact and account-deletion endpoints

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/AuthController.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivityRepository.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/ActivityRatingRepository.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/ProviderRatingRepository.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/ActivitySignupRepository.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/AccountDeletionTest.kt`

**Interfaces:**
- Produces: `GET /auth/me/deletion-impact` → `200 OK` with body `{ "activityCount": <int> }`.
- Produces: `DELETE /auth/me` with body `{ "password": "<string>" }` → `204 No Content` on success, `401` with `{ "error": "Passwort ist falsch." }` on wrong password.
- Consumes: nothing from other tasks. Task 2 (frontend) consumes these two endpoints' exact request/response shapes above.

- [ ] **Step 1: Add the new repository query methods**

In `backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivityRepository.kt`, the interface currently reads:

```kotlin
package com.example.VoloMap.server

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface VolunteerActivityRepository : JpaRepository<VolunteerActivity, Long> {
    // Spring generates the SQL automatically from the method name -> no SQL cod required
    fun existsBySourceUrl(sourceUrl: String): Boolean

    // Pessimistic write lock so concurrent sign-up requests for the same activity are
    // serialized — prevents overbooking a maxParticipants-limited activity and prevents
    // a duplicate-signup race from hitting the DB unique constraint as an uncaught 500.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from VolunteerActivity a where a.id = :id")
    fun findByIdForUpdate(id: Long): VolunteerActivity?
}
```

Add one method to the interface body, right after `existsBySourceUrl`:

```kotlin
    fun findByCreatedBy(user: User): List<VolunteerActivity>
```

In `backend/src/main/kotlin/com/example/VoloMap/server/ActivityRatingRepository.kt`, the interface currently reads:

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

Add one method, right after `findByActivity`:

```kotlin
    fun findByUser(user: User): List<ActivityRating>
```

In `backend/src/main/kotlin/com/example/VoloMap/server/ProviderRatingRepository.kt`, the interface currently reads:

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

Add one method, right after `findByProvider`:

```kotlin
    fun findByUser(user: User): List<ProviderRating>
```

In `backend/src/main/kotlin/com/example/VoloMap/server/ActivitySignupRepository.kt`, the interface currently reads:

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

Add one method, right after `countByActivity`:

```kotlin
    fun findByUser(user: User): List<ActivitySignup>
```

- [ ] **Step 2: Add the two endpoints to `AuthController`**

`backend/src/main/kotlin/com/example/VoloMap/server/AuthController.kt` currently starts with these imports:

```kotlin
package com.example.VoloMap.server

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
```

Add one import, alphabetically among the others:

```kotlin
import org.springframework.web.bind.annotation.DeleteMapping
```

Also add this import (needed for `@Transactional` on the new delete endpoint, matching the pattern already used on `MainController.deleteActivity()`):

```kotlin
import org.springframework.transaction.annotation.Transactional
```

The data class declarations currently read:

```kotlin
data class RegisterRequest(
    @field:Email val email: String,
    @field:Size(min = 8, max = 72) val password: String,
    @field:NotBlank val name: String,
    val role: Role
)
data class LoginRequest(val email: String, val password: String)
data class UserResponse(
    val id: Long,
    val email: String,
    val name: String,
    val role: Role,
    val photoUrl: String? = null,
    val websiteUrl: String? = null,
)
data class UpdateProfileRequest(val photoUrl: String? = null, val websiteUrl: String? = null)
data class ErrorResponse(val error: String)
```

Add two more data classes right after `ErrorResponse`:

```kotlin
data class DeletionImpactResponse(val activityCount: Int)
data class DeleteAccountRequest(val password: String)
```

The `AuthController` class currently starts:

```kotlin
@RestController
@RequestMapping("/auth")
class AuthController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val securityContextRepository: SecurityContextRepository
) {
```

Add four more constructor parameters, right after `securityContextRepository`:

```kotlin
@RestController
@RequestMapping("/auth")
class AuthController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val securityContextRepository: SecurityContextRepository,
    private val volunteerActivityRepository: VolunteerActivityRepository,
    private val activityRatingRepository: ActivityRatingRepository,
    private val providerRatingRepository: ProviderRatingRepository,
    private val activitySignupRepository: ActivitySignupRepository,
) {
```

Finally, the `updateProfile` method currently ends the class's public endpoints, right before the private `establishSession` helper:

```kotlin
    @PutMapping("/me")
    fun updateProfile(
        @RequestBody req: UpdateProfileRequest,
        authentication: Authentication
    ): ResponseEntity<UserResponse> {
        val user = userRepository.findByEmail(authentication.name)!!
        user.photoUrl = req.photoUrl?.trim()?.ifBlank { null }
        user.websiteUrl = req.websiteUrl?.trim()?.ifBlank { null }
            ?.let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        userRepository.save(user)
        return ResponseEntity.ok(UserResponse(user.id, user.email, user.name, user.role, user.photoUrl, user.websiteUrl))
    }

    private fun establishSession(
```

Insert the two new endpoints between `updateProfile` and `establishSession`:

```kotlin
    @PutMapping("/me")
    fun updateProfile(
        @RequestBody req: UpdateProfileRequest,
        authentication: Authentication
    ): ResponseEntity<UserResponse> {
        val user = userRepository.findByEmail(authentication.name)!!
        user.photoUrl = req.photoUrl?.trim()?.ifBlank { null }
        user.websiteUrl = req.websiteUrl?.trim()?.ifBlank { null }
            ?.let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        userRepository.save(user)
        return ResponseEntity.ok(UserResponse(user.id, user.email, user.name, user.role, user.photoUrl, user.websiteUrl))
    }

    @GetMapping("/me/deletion-impact")
    fun deletionImpact(authentication: Authentication): ResponseEntity<DeletionImpactResponse> {
        val user = userRepository.findByEmail(authentication.name)!!
        val count = volunteerActivityRepository.findByCreatedBy(user).size
        return ResponseEntity.ok(DeletionImpactResponse(count))
    }

    @Transactional
    @DeleteMapping("/me")
    fun deleteAccount(
        @RequestBody req: DeleteAccountRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ): ResponseEntity<*> {
        val user = userRepository.findByEmail(authentication.name)!!
        if (!passwordEncoder.matches(req.password, user.passwordHash)) {
            return ResponseEntity.status(401).body(ErrorResponse("Passwort ist falsch."))
        }

        for (activity in volunteerActivityRepository.findByCreatedBy(user)) {
            activityRatingRepository.deleteAll(activityRatingRepository.findByActivity(activity))
            activitySignupRepository.deleteAll(activitySignupRepository.findByActivity(activity))
            volunteerActivityRepository.delete(activity)
        }
        providerRatingRepository.deleteAll(providerRatingRepository.findByProvider(user))
        activityRatingRepository.deleteAll(activityRatingRepository.findByUser(user))
        providerRatingRepository.deleteAll(providerRatingRepository.findByUser(user))
        activitySignupRepository.deleteAll(activitySignupRepository.findByUser(user))

        userRepository.delete(user)

        SecurityContextLogoutHandler().logout(request, response, authentication)
        return ResponseEntity.noContent().build()
    }

    private fun establishSession(
```

- [ ] **Step 3: Write the tests**

Create `backend/src/test/kotlin/com/example/VoloMap/server/AccountDeletionTest.kt`:

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
class AccountDeletionTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var activityRatingRepository: ActivityRatingRepository

    @Autowired
    lateinit var providerRatingRepository: ProviderRatingRepository

    @Autowired
    lateinit var activitySignupRepository: ActivitySignupRepository

    @BeforeEach
    fun cleanUp() {
        activitySignupRepository.deleteAll()
        activityRatingRepository.deleteAll()
        providerRatingRepository.deleteAll()
        activityRepository.deleteAll()
        userRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        activitySignupRepository.deleteAll()
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

    @Test
    fun `wrong password returns 401 and deletes nothing`() {
        val session = registerAndSession("wrongpw@example.com", "USER")

        mockMvc.perform(
            delete("/auth/me")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"falschesPasswort"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Passwort ist falsch."))

        assertTrue(userRepository.existsByEmail("wrongpw@example.com"))
    }

    @Test
    fun `deleting a USER account removes their own ratings and signups`() {
        val providerSession = registerAndSession("provider1@example.com", "ANBIETER")
        val provider = userRepository.findByEmail("provider1@example.com")!!
        val activityId = activityRepository.save(
            VolunteerActivity(name = "Testaktion", createdBy = provider)
        ).id

        val userSession = registerAndSession("volunteer1@example.com", "USER")
        val user = userRepository.findByEmail("volunteer1@example.com")!!

        mockMvc.perform(post("/activities/$activityId/signup").session(userSession))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":5}""")
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/providers/${provider.id}/ratings")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":4}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            delete("/auth/me")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"geheim123"}""")
        ).andExpect(status().isNoContent)

        assertTrue(activitySignupRepository.findByUser(user).isEmpty())
        assertTrue(activityRatingRepository.findByUser(user).isEmpty())
        assertTrue(providerRatingRepository.findByUser(user).isEmpty())
        assertNull(userRepository.findByEmail("volunteer1@example.com"))
        // The activity and the provider's own account are untouched by a USER's deletion
        assertTrue(activityRepository.findById(activityId).isPresent)
        assertTrue(userRepository.existsByEmail("provider1@example.com"))
    }

    @Test
    fun `deleting an ANBIETER account removes their activities, dependent data, and received provider ratings`() {
        val providerSession = registerAndSession("provider2@example.com", "ANBIETER")
        val provider = userRepository.findByEmail("provider2@example.com")!!
        val activityId = activityRepository.save(
            VolunteerActivity(name = "Testaktion 2", createdBy = provider)
        ).id

        val userSession = registerAndSession("volunteer2@example.com", "USER")

        mockMvc.perform(post("/activities/$activityId/signup").session(userSession))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/activities/$activityId/ratings")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":5}""")
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/providers/${provider.id}/ratings")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stars":3}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            delete("/auth/me")
                .session(providerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"geheim123"}""")
        ).andExpect(status().isNoContent)

        assertTrue(activityRepository.findById(activityId).isEmpty)
        assertNull(userRepository.findByEmail("provider2@example.com"))
        // Dependent rows tied to the deleted activity/provider are gone too
        assertTrue(activitySignupRepository.findAll().isEmpty())
        assertTrue(activityRatingRepository.findAll().isEmpty())
        assertTrue(providerRatingRepository.findAll().isEmpty())
        // The rating user's own account is untouched by the provider's deletion
        assertTrue(userRepository.existsByEmail("volunteer2@example.com"))
    }

    @Test
    fun `deletion invalidates the session`() {
        val session = registerAndSession("invalidate@example.com", "USER")

        mockMvc.perform(
            delete("/auth/me")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"geheim123"}""")
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/auth/me").session(session))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deletion-impact reflects the caller's own activity count`() {
        val providerSession = registerAndSession("provider3@example.com", "ANBIETER")
        val provider = userRepository.findByEmail("provider3@example.com")!!

        mockMvc.perform(get("/auth/me/deletion-impact").session(providerSession))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activityCount").value(0))

        activityRepository.save(VolunteerActivity(name = "A", createdBy = provider))
        activityRepository.save(VolunteerActivity(name = "B", createdBy = provider))

        mockMvc.perform(get("/auth/me/deletion-impact").session(providerSession))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activityCount").value(2))

        val userSession = registerAndSession("volunteer3@example.com", "USER")
        mockMvc.perform(get("/auth/me/deletion-impact").session(userSession))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activityCount").value(0))
    }
}
```

- [ ] **Step 4: Run the backend test suite**

Run (from `backend/`): `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, all tests pass including the five new ones in `AccountDeletionTest`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/AuthController.kt backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivityRepository.kt backend/src/main/kotlin/com/example/VoloMap/server/ActivityRatingRepository.kt backend/src/main/kotlin/com/example/VoloMap/server/ProviderRatingRepository.kt backend/src/main/kotlin/com/example/VoloMap/server/ActivitySignupRepository.kt backend/src/test/kotlin/com/example/VoloMap/server/AccountDeletionTest.kt
git commit -m "feat: add account self-deletion endpoints"
```

---

### Task 2: Frontend — "Konto löschen" in Profile, opened up to both roles

**Files:**
- Modify: `frontend/src/auth.ts`
- Modify: `frontend/src/lib/Profile.svelte` (full-file replacement)
- Modify: `frontend/src/lib/NavBar.svelte`

**Interfaces:**
- Consumes: `GET /auth/me/deletion-impact` → `{ activityCount: number }` and `DELETE /auth/me` with body `{ password: string }` → `204` or `401` with `{ error: string }`, from Task 1.
- Produces: `getDeletionImpact(): Promise<{ activityCount: number }>` and `deleteAccount(password: string): Promise<string | null>`, exported from `frontend/src/auth.ts`. Both used only by `Profile.svelte` in this task; no later task depends on them.

- [ ] **Step 1: Add the two auth helpers**

`frontend/src/auth.ts` currently ends with `fetchWithSessionCheck` (the last function in the file). Add these two new functions right after the existing `logout` function (before `fetchWithSessionCheck`):

```typescript
export async function getDeletionImpact(): Promise<{ activityCount: number }> {
    const res = await fetch(`${API_BASE}/auth/me/deletion-impact`, { credentials: "include" });
    if (!res.ok) {
        return { activityCount: 0 };
    }
    return res.json();
}

export async function deleteAccount(password: string): Promise<string | null> {
    const res = await fetch(`${API_BASE}/auth/me`, {
        method: "DELETE",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ password }),
    });
    if (!res.ok) {
        return extractError(res, "Löschen fehlgeschlagen.");
    }
    currentUser.set(null);
    return null;
}
```

- [ ] **Step 2: Open the "Mein Profil" nav link to every logged-in user**

`frontend/src/lib/NavBar.svelte` currently has this block inside `<div class="links">`:

```svelte
        {#if $currentUser?.role === "ANBIETER"}
            <Link href="/add" activeClass="active">Aktivität hinzufügen</Link>
            <Link href="/profile" activeClass="active">Mein Profil</Link>
        {/if}
```

Replace it with:

```svelte
        {#if $currentUser?.role === "ANBIETER"}
            <Link href="/add" activeClass="active">Aktivität hinzufügen</Link>
        {/if}
        {#if $currentUser}
            <Link href="/profile" activeClass="active">Mein Profil</Link>
        {/if}
```

Nothing else in this file changes.

- [ ] **Step 3: Rewrite Profile.svelte**

Replace the full contents of `frontend/src/lib/Profile.svelte` with:

```svelte
<script lang="ts">
    import { currentUser, authChecked, fetchWithSessionCheck, deleteAccount, getDeletionImpact } from "../auth";
    import { navigate } from "../router";

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

    let deleteExpanded = false;
    let deleteActivityCount = 0;
    let deletePassword = "";
    let deleteSubmitting = false;
    let deleteError: string | null = null;

    async function openDelete() {
        deleteExpanded = true;
        deleteError = null;
        const impact = await getDeletionImpact();
        deleteActivityCount = impact.activityCount;
    }

    function cancelDelete() {
        deleteExpanded = false;
        deletePassword = "";
        deleteError = null;
    }

    async function confirmDelete() {
        deleteSubmitting = true;
        deleteError = null;
        const error = await deleteAccount(deletePassword);
        deleteSubmitting = false;
        if (error) {
            deleteError = error;
            return;
        }
        navigate("/");
    }
</script>

{#if !$authChecked}
    <div class="page"><p>Lädt…</p></div>
{:else if !$currentUser}
    <div class="page">
        <p class="notice">Nur eingeloggte Nutzer haben ein Konto.</p>
    </div>
{:else}
    <div class="page">
        <div class="stack">
            {#if $currentUser.role === "ANBIETER"}
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
            {/if}

            <div class="danger-zone">
                <h3>Konto löschen</h3>
                {#if !deleteExpanded}
                    <button type="button" class="danger" on:click={openDelete}>Konto löschen</button>
                {:else}
                    <p>Diese Aktion ist unwiderruflich.</p>
                    {#if deleteActivityCount > 0}
                        <p class="warning">
                            Du hast {deleteActivityCount} {deleteActivityCount === 1 ? "Aktivität" : "Aktivitäten"} — diese werden mitgelöscht.
                        </p>
                    {/if}
                    <label>
                        Passwort zur Bestätigung
                        <input type="password" bind:value={deletePassword} />
                    </label>
                    <div class="delete-actions">
                        <button type="button" class="danger" disabled={deleteSubmitting} on:click={confirmDelete}>
                            {deleteSubmitting ? "Löscht…" : "Endgültig löschen"}
                        </button>
                        <button type="button" on:click={cancelDelete} disabled={deleteSubmitting}>Abbrechen</button>
                    </div>
                    {#if deleteError}
                        <p class="warning">{deleteError}</p>
                    {/if}
                {/if}
            </div>
        </div>
    </div>
{/if}

<style>
    .page {
        flex: 1;
        display: flex;
        justify-content: center;
        padding: 24px 16px;
    }

    .stack {
        display: flex;
        flex-direction: column;
        gap: 16px;
        width: 100%;
        max-width: 420px;
    }

    form {
        display: flex;
        flex-direction: column;
        gap: 12px;
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

    .danger-zone {
        display: flex;
        flex-direction: column;
        gap: 10px;
        background: var(--color-surface);
        border: 1px solid var(--color-error);
        border-radius: var(--radius-lg);
        padding: 20px;
        box-shadow: var(--shadow-panel);
    }

    .danger-zone h3 {
        margin: 0;
        font-size: 1rem;
        color: var(--color-error);
    }

    .delete-actions {
        display: flex;
        gap: 8px;
    }

    button.danger {
        background: var(--color-error);
        color: var(--color-primary-text);
    }
</style>
```

- [ ] **Step 4: Run the frontend type-check**

Run (from `frontend/`): `npm run check`
Expected: no new errors (there is one known pre-existing, unrelated type error in `FilterBar.svelte` — fine to see, don't fix, out of scope).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/auth.ts frontend/src/lib/Profile.svelte frontend/src/lib/NavBar.svelte
git commit -m "feat: let any logged-in user delete their own account"
```
