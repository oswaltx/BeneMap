# Passwort-Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user who forgot their password request a reset link by email and set a new password with it, with rate limiting and full session invalidation.

**Architecture:** New `PasswordResetController` with two public endpoints (`/auth/forgot-password`, `/auth/reset-password`), backed by a new `PasswordResetToken` entity, an in-memory rate limiter, and `spring-boot-starter-mail`. Resetting invalidates every other active session of the account via a newly-wired Spring Security `SessionRegistry`. Two new Svelte pages drive the flow from the frontend.

**Tech Stack:** Kotlin/Spring Boot backend (Spring Security 7, Spring Data JPA, `spring-boot-starter-mail`), Svelte 5 + TypeScript frontend.

## Global Constraints

- Token: `UUID.randomUUID()`, 30 minutes validity, single-use (deleted on successful reset). Requesting a new reset link deletes any previously-issued, still-open token for that account.
- `POST /auth/forgot-password` always responds `200 OK` regardless of whether the submitted email belongs to a real account — never leak account existence. This applies even when rate-limited: the rate limiter itself operates on the raw submitted email string, before any account lookup.
- Rate limiting (per normalized email, in-memory, `ForgotPasswordRateLimiter`): 1st request always allowed; a 2nd request is rejected with `429` unless at least 60 seconds have passed since the previous one; every request after the 2nd is rejected with `429` unless at least 5 minutes have passed since the previous one. A `429` response body is `ErrorResponse` with a ready-to-display German message including the remaining wait in seconds, e.g. `"Bitte warte noch 45 Sekunden, bevor du es erneut versuchst."` — the frontend displays this string unmodified.
- Reset invalidates every currently-tracked session of the target account (not just the session that performed the reset, since resetting does not require being logged in) — via Spring Security's `SessionRegistry`, configured with `maximumSessions(-1)` (explicitly unlimited; this must not cap how many devices a user can be logged in on simultaneously under normal operation).
- Mail is sent via `spring-boot-starter-mail`. SMTP credentials come from environment variables (`MAIL_SMTP_USERNAME`, `MAIL_SMTP_PASSWORD`), never committed. A send failure is caught and logged server-side and never changes the HTTP response — the app must keep working even before real SMTP credentials are configured.
- Reset link is hardcoded to `http://localhost:5173/reset-password?token=<token>`, matching the existing project-wide convention of hardcoded frontend/backend URLs (see `SecurityConfig.kt`'s CORS origin, all frontend `fetch` base URLs).
- No rate-limit-counter decay/expiry beyond what's described above. No live countdown timer in the frontend — the wait time is shown once, as a static number, from the server's error message.

---

### Task 1: Mail infrastructure, token storage, and the request-reset endpoint

**Files:**
- Modify: `backend/build.gradle.kts`
- Modify: `backend/src/main/resources/application.properties`
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/PasswordResetToken.kt`
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/PasswordResetTokenRepository.kt`
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/ForgotPasswordRateLimiter.kt`
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/PasswordResetController.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt:76`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/PasswordResetTest.kt`

**Interfaces:**
- Produces: `PasswordResetToken` entity (`id: Long`, `user: User`, `token: String`, `expiresAt: Instant`). `PasswordResetTokenRepository` with `findByToken(token: String): PasswordResetToken?` and `findByUser(user: User): List<PasswordResetToken>` — Task 2 uses both, and `AuthController`'s `deleteAccount()` (Task 2) uses `findByUser`.
- Produces: `ForgotPasswordRateLimiter` (Spring `@Component`) with `fun checkAndRecord(email: String): Long?` — returns `null` if allowed (and records the attempt), or the number of seconds to wait if rate-limited. No other task depends on this directly (only `PasswordResetController` uses it).
- Produces: `POST /auth/forgot-password` with body `{ "email": "<string>" }` → always `200 OK` (unless rate-limited: `429` with `ErrorResponse`).
- Produces: `PasswordResetController` class in package `com.example.VoloMap.server`, constructor params `userRepository: UserRepository, passwordResetTokenRepository: PasswordResetTokenRepository, passwordEncoder: PasswordEncoder, mailSender: JavaMailSender, rateLimiter: ForgotPasswordRateLimiter` — Task 2 adds a `sessionRegistry: SessionRegistry` parameter and a second endpoint to this exact class.

- [ ] **Step 1: Add the mail starter dependency**

`backend/build.gradle.kts` currently has this dependency block:

```kotlin
    dependencies {
        implementation("org.springframework.boot:spring-boot-starter")
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.springframework.boot:spring-boot-starter-web")
        implementation("org.springframework.boot:spring-boot-starter-data-jpa")
        implementation("org.springframework.boot:spring-boot-starter-security")
        implementation("org.springframework.boot:spring-boot-starter-validation")
        implementation("org.jsoup:jsoup:1.17.2") //Scraping
        implementation("org.json:json:20240303")
        implementation("tools.jackson.module:jackson-module-kotlin:3.0.0")
        runtimeOnly("com.h2database:h2")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.springframework.boot:spring-boot-webmvc-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }
```

Add one line, right after `spring-boot-starter-validation`:

```kotlin
        implementation("org.springframework.boot:spring-boot-starter-mail")
```

- [ ] **Step 2: Add mail configuration**

`backend/src/main/resources/application.properties` currently reads:

```properties
spring.application.name=demo

# JDBC URL (H2 file-based database — persists across restarts)
spring.datasource.url=jdbc:h2:file:./data/volomap
spring.datasource.username=sa
spring.datasource.password=

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# Session-Cookie: SameSite=Lax ist der alleinige CSRF-Schutz dieses Projekts (siehe Spec)
server.servlet.session.cookie.same-site=Lax
```

Append this block at the end:

```properties

# SMTP (Brevo) — Zugangsdaten kommen aus Umgebungsvariablen, niemals committen.
# Ohne gesetzte MAIL_SMTP_USERNAME/MAIL_SMTP_PASSWORD startet die App normal,
# nur der tatsächliche Mail-Versand schlägt fehl (wird geloggt, siehe PasswordResetController).
spring.mail.host=${MAIL_SMTP_HOST:smtp-relay.brevo.com}
spring.mail.port=${MAIL_SMTP_PORT:587}
spring.mail.username=${MAIL_SMTP_USERNAME:}
spring.mail.password=${MAIL_SMTP_PASSWORD:}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

- [ ] **Step 3: Create the token entity**

Create `backend/src/main/kotlin/com/example/VoloMap/server/PasswordResetToken.kt`:

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
import java.time.Instant

@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(unique = true, nullable = false)
    var token: String,

    var expiresAt: Instant,
)
```

- [ ] **Step 4: Create the repository**

Create `backend/src/main/kotlin/com/example/VoloMap/server/PasswordResetTokenRepository.kt`:

```kotlin
package com.example.VoloMap.server

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, Long> {
    fun findByToken(token: String): PasswordResetToken?
    fun findByUser(user: User): List<PasswordResetToken>
}
```

- [ ] **Step 5: Create the rate limiter**

Create `backend/src/main/kotlin/com/example/VoloMap/server/ForgotPasswordRateLimiter.kt`:

```kotlin
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
```

- [ ] **Step 6: Create the controller with the forgot-password endpoint**

Create `backend/src/main/kotlin/com/example/VoloMap/server/PasswordResetController.kt`:

```kotlin
package com.example.VoloMap.server

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ForgotPasswordRequest(val email: String)

@RestController
class PasswordResetController(
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val mailSender: JavaMailSender,
    private val rateLimiter: ForgotPasswordRateLimiter,
) {
    private val logger = LoggerFactory.getLogger(PasswordResetController::class.java)

    @PostMapping("/auth/forgot-password")
    fun forgotPassword(@RequestBody req: ForgotPasswordRequest): ResponseEntity<*> {
        val email = req.email.trim().lowercase()
        val waitSeconds = rateLimiter.checkAndRecord(email)
        if (waitSeconds != null) {
            return ResponseEntity.status(429)
                .body(ErrorResponse("Bitte warte noch $waitSeconds Sekunden, bevor du es erneut versuchst."))
        }

        val user = userRepository.findByEmail(email)
        if (user != null) {
            passwordResetTokenRepository.deleteAll(passwordResetTokenRepository.findByUser(user))
            val token = PasswordResetToken(
                user = user,
                token = UUID.randomUUID().toString(),
                expiresAt = Instant.now().plus(Duration.ofMinutes(30)),
            )
            passwordResetTokenRepository.save(token)
            sendResetEmail(user.email, token.token)
        }
        return ResponseEntity.ok().build<Unit>()
    }

    private fun sendResetEmail(email: String, token: String) {
        try {
            val message = SimpleMailMessage()
            message.setTo(email)
            message.setSubject("Passwort zurücksetzen — Benemap")
            message.setText(
                "Hallo,\n\n" +
                    "du hast angefragt, dein Passwort für Benemap zurückzusetzen. " +
                    "Klicke auf den folgenden Link, um ein neues Passwort zu setzen " +
                    "(gültig für 30 Minuten):\n\n" +
                    "http://localhost:5173/reset-password?token=$token\n\n" +
                    "Falls du das nicht warst, kannst du diese E-Mail ignorieren."
            )
            mailSender.send(message)
        } catch (e: Exception) {
            logger.warn("Failed to send password reset email to $email", e)
        }
    }
}
```

- [ ] **Step 7: Permit the new endpoint without login**

`backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt` currently has this line inside `authorizeHttpRequests`:

```kotlin
                it.requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
```

Replace it with:

```kotlin
                it.requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/forgot-password").permitAll()
```

Nothing else in this file changes in this task.

- [ ] **Step 8: Write the tests**

Create `backend/src/test/kotlin/com/example/VoloMap/server/PasswordResetTest.kt`:

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var passwordResetTokenRepository: PasswordResetTokenRepository

    @MockitoBean
    lateinit var mailSender: JavaMailSender

    @BeforeEach
    fun cleanUp() {
        passwordResetTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun register(email: String) {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"geheim123","name":"Test","role":"USER"}""")
        )
    }

    @Test
    fun `requesting a reset for an existing user creates a token and sends an email`() {
        register("resetreq1@example.com")
        val user = userRepository.findByEmail("resetreq1@example.com")!!

        mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"resetreq1@example.com"}""")
        ).andExpect(status().isOk)

        val tokens = passwordResetTokenRepository.findByUser(user)
        assertEquals(1, tokens.size)
        verify(mailSender).send(any<SimpleMailMessage>())
    }

    @Test
    fun `requesting a reset for a nonexistent email still returns 200 and sends nothing`() {
        mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"nosuchaccount1@example.com"}""")
        ).andExpect(status().isOk)

        assertTrue(passwordResetTokenRepository.findAll().isEmpty())
        verify(mailSender, never()).send(any<SimpleMailMessage>())
    }

    @Test
    fun `a second request within 60 seconds is rate-limited`() {
        register("ratelimit1@example.com")

        mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ratelimit1@example.com"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ratelimit1@example.com"}""")
        ).andExpect(status().`is`(429))
    }

    @Test
    fun `requesting again replaces a previously issued token`() {
        register("replacetoken1@example.com")
        val user = userRepository.findByEmail("replacetoken1@example.com")!!
        val staleToken = passwordResetTokenRepository.save(
            PasswordResetToken(user = user, token = "stale-token-value", expiresAt = Instant.now().plus(Duration.ofMinutes(30)))
        )

        mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"replacetoken1@example.com"}""")
        ).andExpect(status().isOk)

        val tokens = passwordResetTokenRepository.findByUser(user)
        assertEquals(1, tokens.size)
        assertTrue(tokens[0].id != staleToken.id)
    }
}
```

- [ ] **Step 9: Run the backend test suite**

Run (from `backend/`): `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, all tests pass including the four new ones in `PasswordResetTest`.

- [ ] **Step 10: Commit**

```bash
git add backend/build.gradle.kts backend/src/main/resources/application.properties backend/src/main/kotlin/com/example/VoloMap/server/PasswordResetToken.kt backend/src/main/kotlin/com/example/VoloMap/server/PasswordResetTokenRepository.kt backend/src/main/kotlin/com/example/VoloMap/server/ForgotPasswordRateLimiter.kt backend/src/main/kotlin/com/example/VoloMap/server/PasswordResetController.kt backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt backend/src/test/kotlin/com/example/VoloMap/server/PasswordResetTest.kt
git commit -m "feat: add password reset request flow with rate limiting"
```

---

### Task 2: Reset-password endpoint, session invalidation, and account-deletion cascade

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/PasswordResetController.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/AuthController.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/PasswordResetTest.kt` (append)

**Interfaces:**
- Consumes: `PasswordResetTokenRepository.findByToken`/`findByUser` and `PasswordResetController`'s existing constructor params from Task 1.
- Produces: `POST /auth/reset-password` with body `{ "token": "<string>", "newPassword": "<string>" }` → `204 No Content` on success, `400` with `ErrorResponse("Link ist ungültig oder abgelaufen.")` on invalid/expired token.

- [ ] **Step 1: Add the reset-password endpoint**

`backend/src/main/kotlin/com/example/VoloMap/server/PasswordResetController.kt` (after Task 1) currently reads:

```kotlin
package com.example.VoloMap.server

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ForgotPasswordRequest(val email: String)

@RestController
class PasswordResetController(
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val mailSender: JavaMailSender,
    private val rateLimiter: ForgotPasswordRateLimiter,
) {
    private val logger = LoggerFactory.getLogger(PasswordResetController::class.java)

    @PostMapping("/auth/forgot-password")
    fun forgotPassword(@RequestBody req: ForgotPasswordRequest): ResponseEntity<*> {
        val email = req.email.trim().lowercase()
        val waitSeconds = rateLimiter.checkAndRecord(email)
        if (waitSeconds != null) {
            return ResponseEntity.status(429)
                .body(ErrorResponse("Bitte warte noch $waitSeconds Sekunden, bevor du es erneut versuchst."))
        }

        val user = userRepository.findByEmail(email)
        if (user != null) {
            passwordResetTokenRepository.deleteAll(passwordResetTokenRepository.findByUser(user))
            val token = PasswordResetToken(
                user = user,
                token = UUID.randomUUID().toString(),
                expiresAt = Instant.now().plus(Duration.ofMinutes(30)),
            )
            passwordResetTokenRepository.save(token)
            sendResetEmail(user.email, token.token)
        }
        return ResponseEntity.ok().build<Unit>()
    }

    private fun sendResetEmail(email: String, token: String) {
        try {
            val message = SimpleMailMessage()
            message.setTo(email)
            message.setSubject("Passwort zurücksetzen — Benemap")
            message.setText(
                "Hallo,\n\n" +
                    "du hast angefragt, dein Passwort für Benemap zurückzusetzen. " +
                    "Klicke auf den folgenden Link, um ein neues Passwort zu setzen " +
                    "(gültig für 30 Minuten):\n\n" +
                    "http://localhost:5173/reset-password?token=$token\n\n" +
                    "Falls du das nicht warst, kannst du diese E-Mail ignorieren."
            )
            mailSender.send(message)
        } catch (e: Exception) {
            logger.warn("Failed to send password reset email to $email", e)
        }
    }
}
```

Replace the full file with:

```kotlin
package com.example.VoloMap.server

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ForgotPasswordRequest(val email: String)
data class ResetPasswordRequest(val token: String, @field:Size(min = 8, max = 72) val newPassword: String)

@RestController
class PasswordResetController(
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val mailSender: JavaMailSender,
    private val rateLimiter: ForgotPasswordRateLimiter,
    private val sessionRegistry: SessionRegistry,
) {
    private val logger = LoggerFactory.getLogger(PasswordResetController::class.java)

    @PostMapping("/auth/forgot-password")
    fun forgotPassword(@RequestBody req: ForgotPasswordRequest): ResponseEntity<*> {
        val email = req.email.trim().lowercase()
        val waitSeconds = rateLimiter.checkAndRecord(email)
        if (waitSeconds != null) {
            return ResponseEntity.status(429)
                .body(ErrorResponse("Bitte warte noch $waitSeconds Sekunden, bevor du es erneut versuchst."))
        }

        val user = userRepository.findByEmail(email)
        if (user != null) {
            passwordResetTokenRepository.deleteAll(passwordResetTokenRepository.findByUser(user))
            val token = PasswordResetToken(
                user = user,
                token = UUID.randomUUID().toString(),
                expiresAt = Instant.now().plus(Duration.ofMinutes(30)),
            )
            passwordResetTokenRepository.save(token)
            sendResetEmail(user.email, token.token)
        }
        return ResponseEntity.ok().build<Unit>()
    }

    @Transactional
    @PostMapping("/auth/reset-password")
    fun resetPassword(@Valid @RequestBody req: ResetPasswordRequest): ResponseEntity<*> {
        val resetToken = passwordResetTokenRepository.findByToken(req.token)
        if (resetToken == null || resetToken.expiresAt.isBefore(Instant.now())) {
            return ResponseEntity.status(400).body(ErrorResponse("Link ist ungültig oder abgelaufen."))
        }

        val user = resetToken.user
        user.passwordHash = passwordEncoder.encode(req.newPassword)!!
        userRepository.save(user)
        passwordResetTokenRepository.delete(resetToken)

        sessionRegistry.allPrincipals
            .filterIsInstance<UserDetails>()
            .filter { it.username == user.email }
            .forEach { principal ->
                sessionRegistry.getAllSessions(principal, false).forEach { it.expireNow() }
            }

        return ResponseEntity.noContent().build<Unit>()
    }

    private fun sendResetEmail(email: String, token: String) {
        try {
            val message = SimpleMailMessage()
            message.setTo(email)
            message.setSubject("Passwort zurücksetzen — Benemap")
            message.setText(
                "Hallo,\n\n" +
                    "du hast angefragt, dein Passwort für Benemap zurückzusetzen. " +
                    "Klicke auf den folgenden Link, um ein neues Passwort zu setzen " +
                    "(gültig für 30 Minuten):\n\n" +
                    "http://localhost:5173/reset-password?token=$token\n\n" +
                    "Falls du das nicht warst, kannst du diese E-Mail ignorieren."
            )
            mailSender.send(message)
        } catch (e: Exception) {
            logger.warn("Failed to send password reset email to $email", e)
        }
    }
}
```

- [ ] **Step 2: Wire the SessionRegistry into Spring Security**

`backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt` (after Task 1) currently reads:

```kotlin
package com.example.VoloMap.server

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val userRepository: UserRepository
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun userDetailsService(): UserDetailsService = UserDetailsService { email ->
        val user = userRepository.findByEmail(email)
            ?: throw UsernameNotFoundException("Unbekannte E-Mail: $email")
        org.springframework.security.core.userdetails.User
            .withUsername(user.email)
            .password(user.passwordHash)
            .authorities(SimpleGrantedAuthority("ROLE_${user.role}"))
            .build()
    }

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = listOf("http://localhost:5173")
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        securityContextRepository: SecurityContextRepository
    ): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .securityContext { it.securityContextRepository(securityContextRepository) }
            .addFilterAfter(UserExistsFilter(userRepository), SecurityContextHolderFilter::class.java)
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/", "/markers", "/categories", "/activities/*/ratings", "/providers/*/ratings", "/activities/*/signups").permitAll()
                it.requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/forgot-password").permitAll()
                it.requestMatchers(HttpMethod.POST, "/add", "/add-recurring").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.PUT, "/activities/*").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.DELETE, "/activities/*").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.POST, "/activities/*/ratings", "/providers/*/ratings", "/activities/*/signup").hasRole("USER")
                it.requestMatchers(HttpMethod.DELETE, "/activities/*/signup").hasRole("USER")
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
        return http.build()
    }
}
```

Replace the full file with:

```kotlin
package com.example.VoloMap.server

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.session.HttpSessionEventPublisher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val userRepository: UserRepository
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun userDetailsService(): UserDetailsService = UserDetailsService { email ->
        val user = userRepository.findByEmail(email)
            ?: throw UsernameNotFoundException("Unbekannte E-Mail: $email")
        org.springframework.security.core.userdetails.User
            .withUsername(user.email)
            .password(user.passwordHash)
            .authorities(SimpleGrantedAuthority("ROLE_${user.role}"))
            .build()
    }

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    // Verfolgt alle aktiven Sessions pro Nutzer, damit Passwort-Reset gezielt
    // andere Sessions desselben Kontos invalidieren kann. maximumSessions(-1)
    // in securityFilterChain bedeutet ausdrücklich "unbegrenzt" — es wird
    // keine Obergrenze für gleichzeitige Sessions eingeführt.
    @Bean
    fun sessionRegistry(): SessionRegistry = SessionRegistryImpl()

    @Bean
    fun httpSessionEventPublisher(): HttpSessionEventPublisher = HttpSessionEventPublisher()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = listOf("http://localhost:5173")
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        securityContextRepository: SecurityContextRepository,
        sessionRegistry: SessionRegistry
    ): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .securityContext { it.securityContextRepository(securityContextRepository) }
            .addFilterAfter(UserExistsFilter(userRepository), SecurityContextHolderFilter::class.java)
            .sessionManagement {
                it.maximumSessions(-1).sessionRegistry(sessionRegistry)
            }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/", "/markers", "/categories", "/activities/*/ratings", "/providers/*/ratings", "/activities/*/signups").permitAll()
                it.requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/forgot-password", "/auth/reset-password").permitAll()
                it.requestMatchers(HttpMethod.POST, "/add", "/add-recurring").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.PUT, "/activities/*").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.DELETE, "/activities/*").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.POST, "/activities/*/ratings", "/providers/*/ratings", "/activities/*/signup").hasRole("USER")
                it.requestMatchers(HttpMethod.DELETE, "/activities/*/signup").hasRole("USER")
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
        return http.build()
    }
}
```

Note on Step 2: `SessionManagementConfigurer.maximumSessions(int)` returns a `ConcurrencyControlConfigurer<HttpSecurity>`, whose own `.sessionRegistry(SessionRegistry)` method returns itself again — so `it.maximumSessions(-1).sessionRegistry(sessionRegistry)` is one fluent chain, not two separate statements. If this specific chain doesn't compile against the project's exact Spring Security version, check `SessionManagementConfigurer`/`ConcurrencyControlConfigurer`'s Javadoc for the installed version rather than guessing — the two classes' exact method signatures are the only real risk in this step.

- [ ] **Step 3: Clean up password reset tokens when an account is deleted**

`backend/src/main/kotlin/com/example/VoloMap/server/AuthController.kt` currently has this constructor:

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

Add one more constructor parameter, right after `activitySignupRepository`:

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
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
) {
```

Then, in the same file, `deleteAccount` currently reads:

```kotlin
        val userActivitySignups: List<ActivitySignup> = activitySignupRepository.findByUser(user)
        activitySignupRepository.deleteAll(userActivitySignups)

        userRepository.delete(user)
```

Replace it with (adds one line before `userRepository.delete(user)`):

```kotlin
        val userActivitySignups: List<ActivitySignup> = activitySignupRepository.findByUser(user)
        activitySignupRepository.deleteAll(userActivitySignups)
        passwordResetTokenRepository.deleteAll(passwordResetTokenRepository.findByUser(user))

        userRepository.delete(user)
```

- [ ] **Step 4: Append the reset-password tests**

Append these test methods inside the `PasswordResetTest` class in `backend/src/test/kotlin/com/example/VoloMap/server/PasswordResetTest.kt` (right before the closing `}` of the class), and add the imports listed below the tests:

```kotlin
    @Test
    fun `full flow - request reset, use token, login with new password, old password stops working`() {
        register("fullflow1@example.com")
        val user = userRepository.findByEmail("fullflow1@example.com")!!

        mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"fullflow1@example.com"}""")
        ).andExpect(status().isOk)

        val resetToken = passwordResetTokenRepository.findByUser(user)[0].token

        mockMvc.perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$resetToken","newPassword":"neuesPasswort456"}""")
        ).andExpect(status().isNoContent)

        assertNull(passwordResetTokenRepository.findByToken(resetToken))

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"fullflow1@example.com","password":"geheim123"}""")
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"fullflow1@example.com","password":"neuesPasswort456"}""")
        ).andExpect(status().isOk)
    }

    @Test
    fun `reset with an invalid token is rejected`() {
        mockMvc.perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"does-not-exist","newPassword":"neuesPasswort456"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error").value("Link ist ungültig oder abgelaufen."))
    }

    @Test
    fun `reset with an expired token is rejected`() {
        register("expiredtoken1@example.com")
        val user = userRepository.findByEmail("expiredtoken1@example.com")!!
        val expired = passwordResetTokenRepository.save(
            PasswordResetToken(user = user, token = "already-expired-token", expiresAt = Instant.now().minus(Duration.ofMinutes(1)))
        )

        mockMvc.perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${expired.token}","newPassword":"neuesPasswort456"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `resetting the password invalidates every other active session of the account`() {
        val firstSession = register("resetsessions1@example.com")
        val secondLoginResult = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"resetsessions1@example.com","password":"geheim123"}""")
        ).andReturn()
        val secondSession = secondLoginResult.request.session as MockHttpSession

        val user = userRepository.findByEmail("resetsessions1@example.com")!!
        val token = passwordResetTokenRepository.save(
            PasswordResetToken(user = user, token = "session-invalidation-token", expiresAt = Instant.now().plus(Duration.ofMinutes(30)))
        )

        mockMvc.perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${token.token}","newPassword":"neuesPasswort456"}""")
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/auth/me").session(firstSession)).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/auth/me").session(secondSession)).andExpect(status().isUnauthorized)
    }
```

This task's tests need a `register` helper that returns the `MockHttpSession`, unlike Task 1's void-returning `register` — and a `get` static import. Replace the whole helper-and-imports section at the top of the file (from the `package` line down to the end of the `cleanUp` method) with:

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var passwordResetTokenRepository: PasswordResetTokenRepository

    @MockitoBean
    lateinit var mailSender: JavaMailSender

    @BeforeEach
    fun cleanUp() {
        passwordResetTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun register(email: String): MockHttpSession {
        val result = mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"geheim123","name":"Test","role":"USER"}""")
        ).andReturn()
        return result.request.session as MockHttpSession
    }
```

(This changes `register` from returning `Unit` to returning `MockHttpSession` — Task 1's existing tests that call `register("...")` without using the return value keep compiling unchanged, since a discarded return value is always legal Kotlin. Every other line inside the class — Task 1's four test methods and the new `Step 3` closing brace — stays exactly as it already is.)

- [ ] **Step 5: Run the backend test suite**

Run (from `backend/`): `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, all tests pass including the four new tests appended in this task (eight `PasswordResetTest` tests total).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/PasswordResetController.kt backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt backend/src/main/kotlin/com/example/VoloMap/server/AuthController.kt backend/src/test/kotlin/com/example/VoloMap/server/PasswordResetTest.kt
git commit -m "feat: add password reset consumption endpoint with session invalidation"
```

---

### Task 3: Frontend — forgot-password and reset-password pages

**Files:**
- Modify: `frontend/src/auth.ts`
- Create: `frontend/src/pages/ForgotPassword.svelte`
- Create: `frontend/src/pages/ResetPassword.svelte`
- Modify: `frontend/src/router.ts`
- Modify: `frontend/src/pages/Login.svelte`

**Interfaces:**
- Consumes: `POST /auth/forgot-password` (`{email}` → `200` or `429` with `{error}`) and `POST /auth/reset-password` (`{token, newPassword}` → `204` or `400` with `{error}`) from Tasks 1 and 2.
- Produces: `requestPasswordReset(email: string): Promise<string | null>` and `resetPassword(token: string, newPassword: string): Promise<string | null>`, exported from `frontend/src/auth.ts`. Used only by the two new pages in this task.

- [ ] **Step 1: Add the two auth helpers**

`frontend/src/auth.ts` currently ends with `logout` followed by `fetchWithSessionCheck`. Add these two new functions right after `logout` (before `fetchWithSessionCheck`):

```typescript
export async function requestPasswordReset(email: string): Promise<string | null> {
    const res = await fetch(`${API_BASE}/auth/forgot-password`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
    });
    if (!res.ok) {
        return extractError(res, "Anfrage fehlgeschlagen.");
    }
    return null;
}

export async function resetPassword(token: string, newPassword: string): Promise<string | null> {
    const res = await fetch(`${API_BASE}/auth/reset-password`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, newPassword }),
    });
    if (!res.ok) {
        return extractError(res, "Zurücksetzen fehlgeschlagen.");
    }
    return null;
}
```

- [ ] **Step 2: Create the forgot-password page**

Create `frontend/src/pages/ForgotPassword.svelte`:

```svelte
<script lang="ts">
    import { requestPasswordReset } from "../auth";

    let email = "";
    let submitting = false;
    let message: string | null = null;
    let messageIsWarning = false;

    async function handleSubmit() {
        submitting = true;
        message = null;
        const error = await requestPasswordReset(email);
        submitting = false;
        if (error) {
            message = error;
            messageIsWarning = true;
        } else {
            message = "Falls ein Konto mit dieser E-Mail existiert, wurde eine E-Mail mit einem Link zum Zurücksetzen verschickt.";
            messageIsWarning = false;
        }
    }
</script>

<div class="page">
    <form on:submit|preventDefault={handleSubmit}>
        <h2>Passwort vergessen</h2>

        <label>
            E-Mail
            <input type="email" bind:value={email} required />
        </label>

        <button type="submit" disabled={submitting}>
            {submitting ? "Wird gesendet…" : "Link anfordern"}
        </button>

        {#if message}
            <p class:warning={messageIsWarning}>{message}</p>
        {/if}
    </form>
</div>

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
</style>
```

- [ ] **Step 3: Create the reset-password page**

Create `frontend/src/pages/ResetPassword.svelte`. Note: the app's router (`frontend/src/router.ts`) only tracks `window.location.pathname`, not query strings — this page reads its `token` directly from `window.location.search` instead, and shows its success state inline (rather than calling the router's `navigate()` with a query string, which the router does not support):

```svelte
<script lang="ts">
    import { resetPassword } from "../auth";
    import Link from "../lib/Link.svelte";

    const token = new URLSearchParams(window.location.search).get("token") ?? "";

    let newPassword = "";
    let confirmPassword = "";
    let submitting = false;
    let errorMessage: string | null = null;
    let success = false;

    async function handleSubmit() {
        if (newPassword !== confirmPassword) {
            errorMessage = "Die Passwörter stimmen nicht überein.";
            return;
        }
        submitting = true;
        errorMessage = null;
        const error = await resetPassword(token, newPassword);
        submitting = false;
        if (error) {
            errorMessage = error;
        } else {
            success = true;
        }
    }
</script>

<div class="page">
    {#if success}
        <p class="notice">
            Dein Passwort wurde geändert. Du kannst dich jetzt einloggen.
            <Link href="/login">Zum Login</Link>
        </p>
    {:else if !token}
        <p class="notice">
            Ungültiger Link. Bitte fordere einen neuen an.
            <Link href="/forgot-password">Passwort vergessen</Link>
        </p>
    {:else}
        <form on:submit|preventDefault={handleSubmit}>
            <h2>Neues Passwort setzen</h2>

            <label>
                Neues Passwort
                <input type="password" bind:value={newPassword} required minlength="8" />
            </label>

            <label>
                Passwort bestätigen
                <input type="password" bind:value={confirmPassword} required minlength="8" />
            </label>

            <button type="submit" disabled={submitting}>
                {submitting ? "Setzt zurück…" : "Passwort setzen"}
            </button>

            {#if errorMessage}
                <p class="warning">{errorMessage}</p>
            {/if}
        </form>
    {/if}
</div>

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

- [ ] **Step 4: Register the two new routes**

`frontend/src/router.ts` currently reads:

```typescript
import { writable } from "svelte/store";
import type { Component } from "svelte";

import Home from "./pages/Home.svelte";
import About from "./pages/About.svelte";
import AddActivity from "./lib/AddActivity.svelte";
import Profile from "./lib/Profile.svelte";
import Login from "./pages/Login.svelte";
import Register from "./pages/Register.svelte";
import Impressum from "./pages/Impressum.svelte";
import Datenschutz from "./pages/Datenschutz.svelte";

export const route = writable<string>(window.location.pathname);

export const routes: Record<string, Component> = {
    "/": Home,
    "/about": About,
    "/add": AddActivity,
    "/profile": Profile,
    "/login": Login,
    "/register": Register,
    "/impressum": Impressum,
    "/datenschutz": Datenschutz,
};

export function navigate(path: string) {
    history.pushState({}, "", path);
    route.set(path);
}

window.addEventListener("popstate", () => {
    route.set(window.location.pathname);
});
```

Replace it with:

```typescript
import { writable } from "svelte/store";
import type { Component } from "svelte";

import Home from "./pages/Home.svelte";
import About from "./pages/About.svelte";
import AddActivity from "./lib/AddActivity.svelte";
import Profile from "./lib/Profile.svelte";
import Login from "./pages/Login.svelte";
import Register from "./pages/Register.svelte";
import Impressum from "./pages/Impressum.svelte";
import Datenschutz from "./pages/Datenschutz.svelte";
import ForgotPassword from "./pages/ForgotPassword.svelte";
import ResetPassword from "./pages/ResetPassword.svelte";

export const route = writable<string>(window.location.pathname);

export const routes: Record<string, Component> = {
    "/": Home,
    "/about": About,
    "/add": AddActivity,
    "/profile": Profile,
    "/login": Login,
    "/register": Register,
    "/impressum": Impressum,
    "/datenschutz": Datenschutz,
    "/forgot-password": ForgotPassword,
    "/reset-password": ResetPassword,
};

export function navigate(path: string) {
    history.pushState({}, "", path);
    route.set(path);
}

window.addEventListener("popstate", () => {
    route.set(window.location.pathname);
});
```

- [ ] **Step 5: Link to it from the login page**

`frontend/src/pages/Login.svelte` currently reads:

```svelte
<script lang="ts">
    import { login } from "../auth";
    import { navigate } from "../router";

    let email = "";
    let password = "";
    let submitting = false;
    let errorMessage: string | null = null;

    async function handleSubmit() {
        submitting = true;
        errorMessage = null;
        const error = await login(email, password);
        submitting = false;
        if (error) {
            errorMessage = error;
        } else {
            navigate("/");
        }
    }
</script>

<div class="page">
    <form on:submit|preventDefault={handleSubmit}>
        <h2>Login</h2>

        <label>
            E-Mail
            <input type="email" bind:value={email} required />
        </label>

        <label>
            Passwort
            <input type="password" bind:value={password} required />
        </label>

        <button type="submit" disabled={submitting}>
            {submitting ? "Wird geprüft…" : "Einloggen"}
        </button>

        {#if errorMessage}
            <p class="warning">{errorMessage}</p>
        {/if}
    </form>
</div>
```

Replace the `<script>` block's imports and the form's button/error section as follows. The imports change from:

```svelte
<script lang="ts">
    import { login } from "../auth";
    import { navigate } from "../router";
```

to:

```svelte
<script lang="ts">
    import { login } from "../auth";
    import { navigate } from "../router";
    import Link from "../lib/Link.svelte";
```

And the button/error section changes from:

```svelte
        <button type="submit" disabled={submitting}>
            {submitting ? "Wird geprüft…" : "Einloggen"}
        </button>

        {#if errorMessage}
            <p class="warning">{errorMessage}</p>
        {/if}
```

to:

```svelte
        <button type="submit" disabled={submitting}>
            {submitting ? "Wird geprüft…" : "Einloggen"}
        </button>

        <Link href="/forgot-password">Passwort vergessen?</Link>

        {#if errorMessage}
            <p class="warning">{errorMessage}</p>
        {/if}
```

The rest of the file (styles, the rest of the form) is unchanged.

- [ ] **Step 6: Run the frontend type-check**

Run (from `frontend/`): `npm run check`
Expected: no new errors (there is one known pre-existing, unrelated type error in `FilterBar.svelte` — fine to see, don't fix, out of scope).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/auth.ts frontend/src/pages/ForgotPassword.svelte frontend/src/pages/ResetPassword.svelte frontend/src/router.ts frontend/src/pages/Login.svelte
git commit -m "feat: add forgot-password and reset-password pages"
```
