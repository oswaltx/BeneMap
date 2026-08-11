# Login für Anbieter und User — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein Konto-/Login-System für zwei Rollen (Anbieter, User), sodass nur eingeloggte Anbieter über `POST /add` Aktivitäten anlegen können. Die Karte bleibt ohne Login lesbar.

**Architektur:** Backend: Spring Security mit sitzungsbasierter (Cookie-)Authentifizierung, eine neue `User`-Entität mit Rollen-Enum, ein `createdBy`-Besitzfeld auf `VolunteerActivity`. Frontend: ein globaler Auth-Store (`svelte/store`, analog zu `router.ts`), zwei neue Seiten (Login/Register), bedingtes Rendering in `NavBar` und `AddActivity` je nach Login-Status/Rolle.

**Tech Stack:** Spring Boot 4 / Kotlin (Backend, wie bestehend), Spring Security (neu), Svelte 5 legacy style mit `svelte/store` (Frontend, wie bestehend).

## Global Constraints

- Beide Rollen (`ANBIETER`, `USER`) können sich frei und sofort aktiv registrieren — keine manuelle Freischaltung.
- Auth-Mechanismus: Spring-Security-Session-Cookie (`SameSite=Lax`), kein JWT.
- Passwort-Hashing: BCrypt (`BCryptPasswordEncoder`).
- Kein separater CSRF-Token-Mechanismus — `csrf { it.disable() }`, Schutz kommt allein über `SameSite=Lax`. Siehe Spec für Begründung.
- `/`, `/markers`, `/categories` bleiben öffentlich (`permitAll`), keine Änderung an ihrem Verhalten.
- Kein Bewertungssystem, keine Activity-Edits, kein Passwort-Reset, keine E-Mail-Verifizierung, kein iCal-Sync — alles außerhalb dieses Vorhabens (siehe Board).
- `VolunteerActivity.createdBy` ist nullable (bestehende Aktivitäten ohne Besitzer bleiben gültig) und wird beim Serialisieren nie mit ausgegeben (`@JsonIgnore`) — verhindert, dass ein verschachteltes `User`-Objekt (inkl. Passwort-Hash) über `/add`'s Response geleakt wird.
- Kein Frontend-Test-Framework — Verifikation ist manuell/visuell im Browser (bestehende Projekt-Konvention).
- CORS bleibt auf `http://localhost:5173` beschränkt, jetzt zentral in der Security-Konfiguration mit `allowCredentials = true` statt über `@CrossOrigin` auf dem Controller.

---

### Task 1: `User`-Entität, `Role`-Enum, `UserRepository`

**Context:** Fundament für alles Weitere — die neue Konto-Tabelle und ihr Repository. Reine Datenmodell-Arbeit, kein Verhalten zu testen (analog zu `VolunteerActivity.kt`, das auch keine eigene Testdatei hat).

**Files:**
- Modify: `backend/build.gradle.kts`
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/User.kt`
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/UserRepository.kt`

**Interfaces:**
- Produces: `enum class Role { ANBIETER, USER }`; `class User(id: Long, email: String, passwordHash: String, name: String, role: Role, createdAt: Instant)`; `interface UserRepository : JpaRepository<User, Long>` mit `findByEmail(email: String): User?` und `existsByEmail(email: String): Boolean`. Alle folgenden Tasks nutzen genau diese Namen/Signaturen.

- [ ] **Step 1: Spring-Security-Abhängigkeit hinzufügen**

In `backend/build.gradle.kts`, im `dependencies { ... }`-Block, direkt nach der Zeile
`implementation("org.springframework.boot:spring-boot-starter-data-jpa")` folgende Zeile einfügen:

```kotlin
    implementation("org.springframework.boot:spring-boot-starter-security")
```

- [ ] **Step 2: `User.kt` anlegen**

```kotlin
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

    var createdAt: Instant = Instant.now(),
)
```

- [ ] **Step 3: `UserRepository.kt` anlegen**

```kotlin
package com.example.VoloMap.server

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}
```

- [ ] **Step 4: Verify**

Run (from `backend/`): `./gradlew.bat compileKotlin`
Expected: `BUILD SUCCESSFUL` (reine Kompilierprüfung, es gibt noch keine Tests für diese Dateien).

- [ ] **Step 5: Commit**

```bash
git add backend/build.gradle.kts backend/src/main/kotlin/com/example/VoloMap/server/User.kt backend/src/main/kotlin/com/example/VoloMap/server/UserRepository.kt
git commit -m "feat: add User entity, Role enum and UserRepository"
```

---

### Task 2: `SecurityConfig` — CORS, Passwort-Hashing, Autorisierungsregeln

**Context:** Zentrale Sicherheitskonfiguration. Ab dieser Task ist `POST /add` bereits auf die Rolle `ANBIETER` beschränkt — es gibt zu diesem Zeitpunkt noch keinen Weg, sich einzuloggen (kommt in Task 3), aber der erste Test dieser Task prüft bereits den einfachsten Fall: ein komplett anonymer Request wird abgelehnt.

**Files:**
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerSecurityTest.kt`

**Interfaces:**
- Consumes: `UserRepository` aus Task 1.
- Produces: Bean `PasswordEncoder` (BCrypt), Bean `UserDetailsService`, Bean `AuthenticationManager`, Bean `SecurityContextRepository` (Typ `HttpSessionSecurityContextRepository`) — Task 3 (`AuthController`) injiziert `AuthenticationManager` und `SecurityContextRepository` per Konstruktor.

- [ ] **Step 1: Failing Test schreiben**

Create `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerSecurityTest.kt`:

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class MainControllerSecurityTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @BeforeEach
    fun cleanUp() {
        activityRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `unauthenticated POST add is rejected`() {
        mockMvc.perform(
            post("/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Testaktion"}""")
        ).andExpect(status().isUnauthorized)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run (from `backend/`): `./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerSecurityTest"`
Expected: FAIL — der Request bekommt aktuell `200 OK` (kein Security-Layer vorhanden), erwartet wird `401`.

- [ ] **Step 3: `SecurityConfig.kt` anlegen**

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
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/", "/markers", "/categories").permitAll()
                it.requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                it.requestMatchers(HttpMethod.POST, "/add").hasRole("ANBIETER")
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
        return http.build()
    }
}
```

- [ ] **Step 4: Test erneut laufen lassen, Erfolg bestätigen**

Run (from `backend/`): `./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerSecurityTest"`
Expected: PASS.

- [ ] **Step 5: Volle Testsuite laufen lassen**

Run (from `backend/`): `./gradlew.bat test`
Expected: `BUILD SUCCESSFUL` — die bestehenden `MainControllerAddActivityTest`- und `MainControllerMarkersTest`-Tests instanziieren `MainController` direkt ohne Spring-Kontext und sind von der Security-Konfiguration nicht betroffen; `DemoApplicationTests` (Kontext-Ladetest) muss weiterhin grün sein.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/SecurityConfig.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerSecurityTest.kt
git commit -m "feat: add Spring Security config with session auth and role-gated /add"
```

---

### Task 3: `AuthController` — Registrierung, Login, Logout, `/auth/me`

**Context:** Die eigentlichen Auth-Endpunkte. Registrierung und Login etablieren beide eine Session (per manuellem `SecurityContext`-Save in die HTTP-Session), damit man nach der Registrierung sofort eingeloggt ist, ohne zusätzlich `/auth/login` aufrufen zu müssen.

**Files:**
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/AuthController.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/AuthControllerTest.kt`

**Interfaces:**
- Consumes: `UserRepository`, `Role` (Task 1); `AuthenticationManager`, `SecurityContextRepository` (Task 2, als Spring Beans injiziert).
- Produces: `data class UserResponse(email: String, name: String, role: Role)` — Frontend-Task 6 (`auth.ts`) erwartet exakt dieses JSON-Shape (`email`, `name`, `role`) von `/auth/register`, `/auth/login`, `/auth/me`.

- [ ] **Step 1: Failing Tests schreiben**

Create `backend/src/test/kotlin/com/example/VoloMap/server/AuthControllerTest.kt`:

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
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
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @BeforeEach
    fun cleanUp() {
        userRepository.deleteAll()
    }

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
    }

    @Test
    fun `rejects registration with an already-used email`() {
        val payload = """{"email":"dup@example.com","password":"geheim123","name":"Dup","role":"USER"}"""
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk)

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isConflict)
    }

    @Test
    fun `logs in with correct credentials and rejects wrong password`() {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ben@example.com","password":"richtig123","name":"Ben","role":"ANBIETER"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ben@example.com","password":"falsch"}""")
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ben@example.com","password":"richtig123"}""")
        ).andExpect(status().isOk)
    }

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

        mockMvc.perform(post("/auth/logout").session(session))
            .andExpect(status().isOk)

        mockMvc.perform(get("/auth/me").session(session))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `me without a session is unauthorized`() {
        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized)
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

Run (from `backend/`): `./gradlew.bat test --tests "com.example.VoloMap.server.AuthControllerTest"`
Expected: FAIL — `AuthController` existiert noch nicht, Kompilierfehler bzw. 404 auf alle `/auth/*`-Pfade.

- [ ] **Step 3: `AuthController.kt` anlegen**

```kotlin
package com.example.VoloMap.server

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RegisterRequest(val email: String, val password: String, val name: String, val role: Role)
data class LoginRequest(val email: String, val password: String)
data class UserResponse(val email: String, val name: String, val role: Role)
data class ErrorResponse(val error: String)

@RestController
@RequestMapping("/auth")
class AuthController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val securityContextRepository: SecurityContextRepository
) {

    @PostMapping("/register")
    fun register(
        @RequestBody req: RegisterRequest,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<*> {
        if (userRepository.existsByEmail(req.email)) {
            return ResponseEntity.status(409).body(ErrorResponse("E-Mail bereits registriert."))
        }
        userRepository.save(
            User(
                email = req.email,
                passwordHash = passwordEncoder.encode(req.password),
                name = req.name,
                role = req.role
            )
        )
        establishSession(req.email, req.password, request, response)
        val user = userRepository.findByEmail(req.email)!!
        return ResponseEntity.ok(UserResponse(user.email, user.name, user.role))
    }

    @PostMapping("/login")
    fun login(
        @RequestBody req: LoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<*> {
        try {
            establishSession(req.email, req.password, request, response)
        } catch (e: AuthenticationException) {
            return ResponseEntity.status(401).body(ErrorResponse("E-Mail oder Passwort falsch."))
        }
        val user = userRepository.findByEmail(req.email)!!
        return ResponseEntity.ok(UserResponse(user.email, user.name, user.role))
    }

    @PostMapping("/logout")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ): ResponseEntity<Void> {
        SecurityContextLogoutHandler().logout(request, response, authentication)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/me")
    fun me(authentication: Authentication): ResponseEntity<UserResponse> {
        val user = userRepository.findByEmail(authentication.name)!!
        return ResponseEntity.ok(UserResponse(user.email, user.name, user.role))
    }

    private fun establishSession(
        email: String,
        password: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val authRequest = UsernamePasswordAuthenticationToken(email, password)
        val authResult = authenticationManager.authenticate(authRequest)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authResult
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)
    }
}
```

- [ ] **Step 4: Tests erneut laufen lassen, Erfolg bestätigen**

Run (from `backend/`): `./gradlew.bat test --tests "com.example.VoloMap.server.AuthControllerTest"`
Expected: PASS (alle 5 Tests).

- [ ] **Step 5: Volle Testsuite laufen lassen**

Run (from `backend/`): `./gradlew.bat test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/AuthController.kt backend/src/test/kotlin/com/example/VoloMap/server/AuthControllerTest.kt
git commit -m "feat: add register/login/logout/me auth endpoints"
```

---

### Task 4: `/add` an Anbieter-Rolle binden, `createdBy` setzen

**Context:** Verknüpft alles: `VolunteerActivity` bekommt das Besitzfeld, `MainController.addActivity` setzt es aus der Session, und die bestehenden Unit-Tests werden an die neue Signatur angepasst. Zusätzlich wird geprüft, dass das verschachtelte `User`-Objekt (inkl. Passwort-Hash!) niemals über die `/add`-Response ausgeliefert wird.

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`
- Modify: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt`
- Modify: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerSecurityTest.kt`

**Interfaces:**
- Consumes: `User`, `Role`, `UserRepository` (Task 1); `SecurityConfig`s `hasRole("ANBIETER")`-Regel (Task 2); `AuthController`s `/auth/register` (Task 3, genutzt vom Test-Helper unten).
- Produces: `VolunteerActivity.createdBy: User?` (nullable, `@JsonIgnore`); `MainController(repository, geocodingService, userRepository)` — neuer dritter Konstruktorparameter; `addActivity(activity: VolunteerActivity, authentication: Authentication): ResponseEntity<VolunteerActivity>` — neuer zweiter Parameter.

- [ ] **Step 1: Failing Tests schreiben — zwei neue Testfälle in `MainControllerSecurityTest.kt`**

`backend/src/test/kotlin/com/example/VoloMap/server/MainControllerSecurityTest.kt` komplett ersetzen (Step 1 aus Task 2 bleibt erhalten, zwei neue Tests kommen dazu):

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class MainControllerSecurityTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @BeforeEach
    fun cleanUp() {
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
    fun `unauthenticated POST add is rejected`() {
        mockMvc.perform(
            post("/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Testaktion"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `logged-in USER cannot add an activity`() {
        val session = registerAndSession("user@example.com", "USER")
        mockMvc.perform(
            post("/add")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Testaktion"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `logged-in ANBIETER can add an activity, becomes its owner, and no password hash leaks`() {
        val session = registerAndSession("anbieter@example.com", "ANBIETER")
        mockMvc.perform(
            post("/add")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Testaktion"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Testaktion"))
            .andExpect(jsonPath("$.createdBy").doesNotExist())

        val saved = activityRepository.findAll().first()
        assertEquals("anbieter@example.com", saved.createdBy?.email)
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

Run (from `backend/`): `./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerSecurityTest"`
Expected: FAIL — `logged-in USER cannot add an activity` bekommt `200` statt `403` (noch keine Rollenbindung an konkrete Daten), `createdBy` existiert noch nicht auf `VolunteerActivity` (Kompilierfehler in `saved.createdBy`).

- [ ] **Step 3: `createdBy`-Feld zu `VolunteerActivity.kt` hinzufügen**

In `backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt`, die Imports am Dateianfang (Zeilen 1-10) erweitern:

```kotlin
package com.example.VoloMap.server

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDateTime
```

Und am Ende der Konstruktor-Property-Liste (nach `var dateTime: LocalDateTime = LocalDateTime.now(),`) folgendes Feld ergänzen:

```kotlin

    // Anbieter, der diese Aktivität angelegt hat — null für bestehende gescrapte/geseedete Einträge.
    // @JsonIgnore verhindert, dass das verschachtelte User-Objekt (inkl. Passwort-Hash) über die
    // API ausgeliefert wird.
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "created_by")
    var createdBy: User? = null,
```

- [ ] **Step 4: `MainController.kt` anpassen**

`backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt` komplett ersetzen:

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
    private val userRepository: UserRepository
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

        return repository.findAll()
            .filter { category == null || it.category == category }
            .filter { it.latitude != null && it.longitude != null }
            .map { activity ->
                Marker(
                    id = activity.id,
                    lat = activity.latitude!!,
                    lng = activity.longitude!!,
                    name = activity.name,
                    address = activity.addressText ?: "",
                    category = activity.category ?: "",
                    description = activity.description ?: "",
                    dateTime = activity.dateTime
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

(Entfernt: das `@CrossOrigin(origins = ["http://localhost:5173"])` auf der Klasse — CORS läuft jetzt zentral über `SecurityConfig`.)

- [ ] **Step 5: Bestehenden Unit-Test an neue Signatur anpassen**

`backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt` komplett ersetzen:

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication

class MainControllerAddActivityTest {

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
    fun `geocodes address when coordinates are missing`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(geocodingService.geocode("Domkloster 4, Köln")).thenReturn(Pair(50.9413, 6.9583))
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository)
        val activity = VolunteerActivity(name = "Test", addressText = "Domkloster 4, Köln")

        val result = controller.addActivity(activity, authenticationFor(provider.email))

        assertEquals(50.9413, result.body?.latitude)
        assertEquals(6.9583, result.body?.longitude)
        assertEquals(provider, result.body?.createdBy)
        verify(geocodingService).geocode("Domkloster 4, Köln")
    }

    @Test
    fun `saves activity without coordinates when geocoding finds nothing`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(geocodingService.geocode(any<String>())).thenReturn(null)
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository)
        val activity = VolunteerActivity(name = "Test", addressText = "Nonexistent Place XYZ")

        val result = controller.addActivity(activity, authenticationFor(provider.email))

        assertEquals(200, result.statusCode.value())
        assertNull(result.body?.latitude)
        assertNull(result.body?.longitude)
    }

    @Test
    fun `does not call geocoding when coordinates are already set`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        val userRepository: UserRepository = mock()
        whenever(userRepository.findByEmail(provider.email)).thenReturn(provider)
        whenever(repository.save(any<VolunteerActivity>())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService, userRepository)
        val activity = VolunteerActivity(name = "Test", latitude = 1.0, longitude = 2.0)

        controller.addActivity(activity, authenticationFor(provider.email))

        verify(geocodingService, org.mockito.kotlin.never()).geocode(any<String>())
    }
}
```

- [ ] **Step 6: Alle Tests laufen lassen, Erfolg bestätigen**

Run (from `backend/`): `./gradlew.bat test`
Expected: `BUILD SUCCESSFUL` — alle Tests grün, inklusive der drei aus Step 1.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerSecurityTest.kt
git commit -m "feat: bind /add to ANBIETER role and set VolunteerActivity.createdBy"
```

---

### Task 5: Backend-Gesamtverifikation

**Context:** Abschließende Prüfung, dass Backend-Auth als Ganzes funktioniert, bevor das Frontend darauf aufbaut.

**Files:** keine (nur Verifikation)

- [ ] **Step 1: Volle Testsuite**

Run (from `backend/`): `./gradlew.bat test`
Expected: `BUILD SUCCESSFUL`, alle Tests grün (inkl. `DemoApplicationTests`, `MainControllerMarkersTest`, `MainControllerAddActivityTest`, `MainControllerSecurityTest`, `AuthControllerTest`).

- [ ] **Step 2: Manueller Smoke-Test mit laufendem Server**

Run: `cd backend && ./gradlew.bat bootRun`

In einem zweiten Terminal (Windows `curl` unterstützt `-c`/`-b` für Cookie-Jar):

```bash
curl -i -c cookies.txt -X POST http://localhost:8080/auth/register -H "Content-Type: application/json" -d "{\"email\":\"test@example.com\",\"password\":\"geheim123\",\"name\":\"Test\",\"role\":\"ANBIETER\"}"
curl -i -b cookies.txt http://localhost:8080/auth/me
curl -i -b cookies.txt -X POST http://localhost:8080/add -H "Content-Type: application/json" -d "{\"name\":\"Curl-Test\"}"
curl -i http://localhost:8080/markers
```

Expected: Register → `200` mit `{"email":"test@example.com","name":"Test","role":"ANBIETER"}`; `/auth/me` → `200` mit denselben Daten; `/add` → `200`, gespeicherte Aktivität; `/markers` (ohne Cookie) → `200`, weiterhin frei zugänglich.

- [ ] **Step 3: Report**

Pass/Fail für Step 1-2 zusammenfassen. Server danach stoppen (Strg+C im `bootRun`-Terminal).

---

### Task 6: Frontend — `auth.ts` Store

**Context:** Zentraler Anlaufpunkt für den Login-Status im Frontend, analog zu `router.ts`s `route`-Store. Alle folgenden Frontend-Tasks importieren daraus.

**Files:**
- Create: `frontend/src/auth.ts`

**Interfaces:**
- Produces: `type Role = "ANBIETER" | "USER"`; `interface AuthUser { email: string; name: string; role: Role }`; Stores `currentUser: Writable<AuthUser | null>`, `authChecked: Writable<boolean>`; Funktionen `fetchCurrentUser(): Promise<void>`, `login(email: string, password: string): Promise<string | null>` (gibt Fehlermeldung oder `null` bei Erfolg zurück), `register(email: string, password: string, name: string, role: Role): Promise<string | null>`, `logout(): Promise<void>`. Task 7 (Login/Register-Seiten), Task 8 (NavBar) und Task 9 (AddActivity) nutzen genau diese Namen/Signaturen.

- [ ] **Step 1: `auth.ts` anlegen**

```typescript
import { writable } from "svelte/store";

export type Role = "ANBIETER" | "USER";

export interface AuthUser {
    email: string;
    name: string;
    role: Role;
}

export const currentUser = writable<AuthUser | null>(null);
export const authChecked = writable<boolean>(false);

const API_BASE = "http://localhost:8080";

async function extractError(res: Response, fallback: string): Promise<string> {
    try {
        const body = await res.json();
        return body?.error ?? fallback;
    } catch {
        return fallback;
    }
}

export async function fetchCurrentUser(): Promise<void> {
    try {
        const res = await fetch(`${API_BASE}/auth/me`, { credentials: "include" });
        currentUser.set(res.ok ? await res.json() : null);
    } catch {
        currentUser.set(null);
    } finally {
        authChecked.set(true);
    }
}

export async function login(email: string, password: string): Promise<string | null> {
    const res = await fetch(`${API_BASE}/auth/login`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
    });
    if (!res.ok) {
        return extractError(res, "Login fehlgeschlagen.");
    }
    currentUser.set(await res.json());
    return null;
}

export async function register(
    email: string,
    password: string,
    name: string,
    role: Role
): Promise<string | null> {
    const res = await fetch(`${API_BASE}/auth/register`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, name, role }),
    });
    if (!res.ok) {
        return extractError(res, "Registrierung fehlgeschlagen.");
    }
    currentUser.set(await res.json());
    return null;
}

export async function logout(): Promise<void> {
    await fetch(`${API_BASE}/auth/logout`, {
        method: "POST",
        credentials: "include",
    });
    currentUser.set(null);
}
```

- [ ] **Step 2: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler (die Datei wird von keiner Komponente importiert, bis Task 7).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/auth.ts
git commit -m "feat: add frontend auth store"
```

---

### Task 7: Frontend — `Login.svelte`, `Register.svelte`, Router

**Files:**
- Create: `frontend/src/pages/Login.svelte`
- Create: `frontend/src/pages/Register.svelte`
- Modify: `frontend/src/router.ts`

**Interfaces:**
- Consumes: `login`, `register` aus `auth.ts` (Task 6); `navigate` aus `router.ts` (bestehend).
- Produces: Routen `/login`, `/register`.

- [ ] **Step 1: `Login.svelte` anlegen**

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

- [ ] **Step 2: `Register.svelte` anlegen**

```svelte
<script lang="ts">
    import { register } from "../auth";
    import { navigate } from "../router";
    import type { Role } from "../auth";

    let email = "";
    let password = "";
    let name = "";
    let role: Role = "USER";
    let submitting = false;
    let errorMessage: string | null = null;

    async function handleSubmit() {
        submitting = true;
        errorMessage = null;
        const error = await register(email, password, name, role);
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
        <h2>Registrieren</h2>

        <label>
            Name
            <input type="text" bind:value={name} required />
        </label>

        <label>
            E-Mail
            <input type="email" bind:value={email} required />
        </label>

        <label>
            Passwort
            <input type="password" bind:value={password} required minlength="8" />
        </label>

        <label>
            Ich bin...
            <select bind:value={role}>
                <option value="USER">Freiwillige:r (möchte Aktivitäten finden)</option>
                <option value="ANBIETER">Anbieter (möchte Aktivitäten einstellen)</option>
            </select>
        </label>

        <button type="submit" disabled={submitting}>
            {submitting ? "Wird angelegt…" : "Registrieren"}
        </button>

        {#if errorMessage}
            <p class="warning">{errorMessage}</p>
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

    input,
    select {
        font-family: inherit;
        font-size: 0.9rem;
        padding: 8px 10px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        color: var(--color-text);
    }

    input:focus,
    select:focus {
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

- [ ] **Step 3: `router.ts` um die neuen Routen erweitern**

`frontend/src/router.ts` komplett ersetzen:

```typescript
import { writable } from "svelte/store";
import type { Component } from "svelte";

import Home from "./pages/Home.svelte";
import About from "./pages/About.svelte";
import AddActivity from "./lib/AddActivity.svelte";
import Login from "./pages/Login.svelte";
import Register from "./pages/Register.svelte";

export const route = writable<string>(window.location.pathname);

export const routes: Record<string, Component> = {
    "/": Home,
    "/about": About,
    "/add": AddActivity,
    "/login": Login,
    "/register": Register,
};

export function navigate(path: string) {
    history.pushState({}, "", path);
    route.set(path);
    (window as any)._paq?.push(['setCustomUrl', path]);
    (window as any)._paq?.push(['trackPageView']);
}

window.addEventListener("popstate", () => {
    route.set(window.location.pathname);
    (window as any)._paq?.push(['setCustomUrl', window.location.pathname]);
    (window as any)._paq?.push(['trackPageView']);
});
```

- [ ] **Step 4: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/Login.svelte frontend/src/pages/Register.svelte frontend/src/router.ts
git commit -m "feat: add Login and Register pages"
```

---

### Task 8: Frontend — `NavBar` und App-Start an Login-Status koppeln

**Files:**
- Modify: `frontend/src/lib/NavBar.svelte`
- Modify: `frontend/src/App.svelte`

**Interfaces:**
- Consumes: `currentUser`, `logout`, `fetchCurrentUser` aus `auth.ts` (Task 6).

- [ ] **Step 1: `NavBar.svelte` komplett ersetzen**

```svelte
<script lang="ts">
    import Link from "./Link.svelte";
    import { currentUser, logout } from "../auth";
    import { navigate } from "../router";

    async function handleLogout() {
        await logout();
        navigate("/");
    }
</script>

<nav>
    <span class="brand">Benemap</span>
    <div class="links">
        <Link href="/" activeClass="active">Home</Link>
        {#if $currentUser?.role === "ANBIETER"}
            <Link href="/add" activeClass="active">Aktivität hinzufügen</Link>
        {/if}
        <Link href="/about" activeClass="active">About</Link>
        {#if $currentUser}
            <span class="user-info">Hallo {$currentUser.name}</span>
            <button class="logout" on:click={handleLogout}>Abmelden</button>
        {:else}
            <Link href="/login" activeClass="active">Login</Link>
            <Link href="/register" activeClass="active">Registrieren</Link>
        {/if}
    </div>
</nav>

<style>
    nav {
        display: flex;
        align-items: center;
        justify-content: space-between;
        background: var(--color-primary);
        color: var(--color-primary-text);
        padding: 12px 20px;
        flex-wrap: wrap;
        gap: 8px;
    }

    .brand {
        font-weight: 700;
        font-size: 1.2rem;
        color: var(--color-primary-text);
    }

    .links {
        display: flex;
        align-items: center;
        gap: 16px;
        flex-wrap: wrap;
    }

    .links :global(a) {
        color: var(--color-primary-text);
        font-weight: 500;
        font-size: 0.9rem;
        opacity: 0.85;
    }

    .links :global(a:hover),
    .links :global(a.active) {
        opacity: 1;
        text-decoration: underline;
    }

    .user-info {
        color: var(--color-primary-text);
        font-size: 0.9rem;
        opacity: 0.85;
    }

    .logout {
        background: none;
        border: 1px solid var(--color-primary-text);
        color: var(--color-primary-text);
        padding: 4px 10px;
        font-size: 0.85rem;
    }

    .logout:hover {
        filter: none;
        opacity: 1;
        background: rgba(255, 255, 255, 0.1);
    }
</style>
```

- [ ] **Step 2: `App.svelte` — Login-Status beim Start laden**

`frontend/src/App.svelte` komplett ersetzen:

```svelte
<script>
    import { onMount } from "svelte";
    import NavBar from "./lib/NavBar.svelte";
    import Router from "./lib/Router.svelte";
    import { fetchCurrentUser } from "./auth";

    onMount(() => {
        fetchCurrentUser();
    });
</script>
<NavBar />
<Router />
```

- [ ] **Step 3: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler in `NavBar.svelte` oder `App.svelte`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/NavBar.svelte frontend/src/App.svelte
git commit -m "feat: reflect login status in NavBar, load session on app start"
```

---

### Task 9: Frontend — `AddActivity` an Anbieter-Rolle binden

**Context:** Die Seite bleibt über die Route `/add` erreichbar, zeigt das Formular aber nur eingeloggten Anbietern; alle anderen sehen einen Hinweistext mit Links zu Login/Registrierung statt eines automatischen Redirects — technisch einfacher und für die Nutzerin am Ende gleichwertig (kein Formular sichtbar/nutzbar ohne passende Rolle).

**Files:**
- Modify: `frontend/src/lib/AddActivity.svelte`

**Interfaces:**
- Consumes: `currentUser`, `authChecked` aus `auth.ts` (Task 6).

- [ ] **Step 1: `AddActivity.svelte` komplett ersetzen**

```svelte
<script lang="ts">
    import Link from "./Link.svelte";
    import { currentUser, authChecked } from "../auth";

    let name = "";
    let description = "";
    let addressText = "";
    let category = "";
    let dateTime = "";

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
            const res = await fetch("http://localhost:8080/add", {
                method: "POST",
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
            if (saved.latitude == null || saved.longitude == null) {
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
        <p class="notice">
            Nur eingeloggte Anbieter können Aktivitäten hinzufügen.
            <Link href="/login">Jetzt einloggen</Link> oder
            <Link href="/register">registrieren</Link>.
        </p>
    </div>
{:else}
    <div class="page">
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
                {submitting ? "Speichert…" : "Aktivität hinzufügen"}
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

- [ ] **Step 2: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler in `AddActivity.svelte`.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/AddActivity.svelte
git commit -m "feat: gate AddActivity form behind logged-in Anbieter role"
```

---

### Task 10: End-to-End-Verifikation

**Context:** Kompletter manueller Rundgang durch den ganzen Auth-Flow mit laufendem Backend und Frontend, um sicherzustellen, dass alle Tasks als Ganzes zusammenpassen.

**Files:** keine (nur Verifikation)

- [ ] **Step 1: Volle svelte-check-Prüfung**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler/Warnungen gegenüber dem Stand vor diesem Plan.

- [ ] **Step 2: Backend-Tests grün**

Run: `cd backend && ./gradlew.bat test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Visueller Rundgang im Browser**

Mit laufendem Backend (`./gradlew.bat bootRun`) und Frontend (`npm run dev`):

- `/`: Karte ist ohne Login sichtbar, NavBar zeigt "Login"/"Registrieren", kein "Aktivität hinzufügen"-Link
- `/add` direkt aufgerufen (ausgeloggt): zeigt den Hinweistext statt des Formulars
- `/register`: als Rolle "Anbieter" registrieren → landet danach automatisch eingeloggt auf `/`, NavBar zeigt jetzt "Hallo {Name}", "Abmelden" und "Aktivität hinzufügen"
- `/add` aufgerufen (als eingeloggter Anbieter): Formular ist sichtbar, eine Aktivität erfolgreich anlegen → erscheint auf der Karte
- Browser-Seite neu laden: bleibt eingeloggt (Session-Cookie übersteht Reload)
- "Abmelden" klicken: NavBar zeigt wieder "Login"/"Registrieren", `/add` zeigt wieder den Hinweistext
- Neu registrieren mit Rolle "User": NavBar zeigt keinen "Aktivität hinzufügen"-Link, `/add` zeigt weiterhin den Hinweistext (falsche Rolle, nicht nur "nicht eingeloggt")
- Erneut mit dem vorherigen Anbieter-Konto einloggen (`/login`): funktioniert, führt zurück zu Rolle Anbieter mit vollem Zugriff
- Falsches Passwort beim Login: zeigt Fehlermeldung, kein Absturz

- [ ] **Step 4: Report**

Zusammenfassung Pass/Fail für Schritt 1-3.
