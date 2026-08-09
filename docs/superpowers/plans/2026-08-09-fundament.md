# Fundament fertigstellen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring VoloMap's existing foundation (search, filter, persistence, activity creation, navigation) to a consistent, working state before building the larger board features on top of it.

**Architecture:** Backend stays Spring Boot 4 / Kotlin / JPA with H2, switched from in-memory to file-based storage; a new `GeocodingService` is extracted out of `Scraper` so both the scraper and the manual "add activity" endpoint can geocode addresses. Frontend stays Svelte 5; a shared query-state object in `Map.svelte` replaces the two competing fetch-parameter paths, and the existing but unused `Router`/`AddActivity` scaffolding gets wired up.

**Tech Stack:** Kotlin 2.2.21, Spring Boot 4.0.3 (Jackson 3 / `tools.jackson.*`), H2 2.4.240, Gradle 9.3.1, Svelte 5.45, Vite 7, sveaflet (Leaflet wrapper), Mockito Kotlin 6.3 (new, tests only).

## Global Constraints

- Spring Boot 4.0.3 uses **Jackson 3** (`tools.jackson.*` packages), not classic Jackson 2 (`com.fasterxml.jackson.*`). Any Jackson-related dependency must use the `tools.jackson.*` coordinates.
- CORS is currently hard-restricted to `http://localhost:5173` in `MainController` (`@CrossOrigin(origins = ["http://localhost:5173"])`) — this matches Vite's default dev port and must keep working for the new `/add` traffic from `AddActivity.svelte`.
- No test framework exists for the Svelte frontend; do not introduce one as part of this plan (out of scope per spec's Non-Ziele). Frontend verification is manual/browser-based.
- Follow existing code patterns: SearchBar/FilterBar-style `<script lang="ts">` with `createEventDispatcher` + plain reactive `let` bindings for new Svelte components (not the newer runes style used only in `Link.svelte`).

---

### Task 1: Repo-Hygiene — Gradle-Cache aus Git-Tracking entfernen

**Context:** `backend/.gradle/**` (build cache lock/hash files) is currently tracked in git even though `.gitignore` already excludes `/backend/.gradle/` — the files were committed before the ignore rule existed, so git keeps tracking them and every local build now shows as unrelated binary diffs. This would pollute every commit in the tasks below, so it's fixed first.

**Files:**
- Modify: (git index only, no source file changes)

**Interfaces:**
- Consumes: nothing
- Produces: a clean git index so later tasks' `git status`/`git diff` only show their own changes

- [ ] **Step 1: Confirm what's tracked**

Run: `git ls-files backend/.gradle`
Expected: a list of ~13 files under `backend/.gradle/...` (checksums, fileHashes, executionHistory, buildOutputCleanup, file-system.probe).

- [ ] **Step 2: Untrack them (keep local files on disk)**

```bash
git rm -r --cached backend/.gradle
```

- [ ] **Step 3: Verify `.gitignore` already covers it (it does — no edit needed)**

Run: `git check-ignore -v backend/.gradle/file-system.probe`
Expected: prints the matching rule `.gitignore:5:/backend/.gradle/`.

- [ ] **Step 4: Commit**

```bash
git add -u backend/.gradle
git commit -m "chore: stop tracking backend/.gradle build cache (already gitignored)"
```

---

### Task 2: GeocodingService extrahieren

**Context:** `Scraper.geocode()` currently contains the only Nominatim-lookup logic. The `/add` endpoint (Task 4) needs the same lookup, so it's extracted into its own Spring component instead of being duplicated.

**Files:**
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/GeocodingService.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/Scraper.kt:1-22,68-133`

**Interfaces:**
- Produces: `GeocodingService.geocode(address: String): Pair<Double, Double>?` — returns `(lat, lng)` or `null` if Nominatim finds no match. Same behavior/signature as the old `Scraper.geocode`, just on its own `@Component`.

- [ ] **Step 1: Create `GeocodingService.kt`**

```kotlin
package com.example.VoloMap.server

import org.jsoup.Jsoup
import org.springframework.stereotype.Component

@Component
class GeocodingService {
    fun geocode(address: String): Pair<Double, Double>? {
        val encoded = java.net.URLEncoder.encode(address, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1"

        val response = Jsoup.connect(url)
            .userAgent("VoloMap-Scraper/1.0 (TH Köln; david_ari_ikerimma.oswalt@smail.th-koeln.de)")
            .ignoreContentType(true)
            .get()
            .body()
            .text()

        val json = org.json.JSONArray(response)
        if (json.length() == 0) return null

        val first = json.getJSONObject(0)
        return Pair(first.getDouble("lat"), first.getDouble("lon"))
    }
}
```

- [ ] **Step 2: Update `Scraper.kt` to use it instead of its own `geocode` method**

In the constructor, add the new dependency:

```kotlin
@Component
class Scraper(
    private val repository: VolunteerActivityRepository,
    private val geocodingService: GeocodingService
) {
```

Delete the entire `fun geocode(address: String): Pair<Double, Double>? { ... }` method (the one using `Jsoup.connect(...)` + `org.json.JSONArray`, currently the last method in the file before `fakeScraper`).

In `scrapeEhrenamtDetails`, change the call site:

```kotlin
val coords = data["Adresse der Vermittlungsstelle"]?.let {
    Thread.sleep(1100) // Nominatim rate limit: 1 req/s
    geocodingService.geocode(it)
}
```

- [ ] **Step 3: Compile and confirm Spring wiring still works**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.DemoApplicationTests"`
Expected: `BUILD SUCCESSFUL` — this loads the full Spring context, which fails fast if `Scraper`'s new `GeocodingService` dependency can't be autowired.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/GeocodingService.kt backend/src/main/kotlin/com/example/VoloMap/server/Scraper.kt
git commit -m "refactor: extract GeocodingService out of Scraper"
```

---

### Task 3: Backend-Konfiguration — Persistenz, Seed-Once, Dependency-Fixes

**Context:** Three independent, low-risk config fixes bundled into one task because none needs its own test cycle:
1. DB is in-memory (`jdbc:h2:mem:testdb`) and `VoloMapApp.main()` unconditionally calls `fakeScraper(30)` on every start — every restart wipes and regenerates data. Switch to file-based H2 and only seed when empty.
2. `build.gradle.kts` has `runtimeOnly("com.h2database:h2")` three times (lines 29, 40, 41) — dedupe to one.
3. **Verified bug:** `POST /add` currently returns `400 Bad Request` for any request body, even a fully-populated one (confirmed by running the backend and curling it — the H2/Jackson error log shows `Cannot map 'null' into type 'long'`). Root cause: Spring Boot 4 ships Jackson 3 (`tools.jackson.*`), and Jackson 3's Kotlin module (`tools.jackson.module:jackson-module-kotlin`) is a separate, not-yet-auto-wired dependency — without it, Jackson can't construct Kotlin data/entity classes from JSON at all. This blocks Task 4 and Task 6 (both depend on `/add` actually working), so it's fixed here.

**Files:**
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/VoloMapApp.kt`
- Modify: `backend/build.gradle.kts`
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/JacksonConfig.kt`
- Modify: `.gitignore` (repo root)

**Interfaces:**
- Produces: a working `JsonMapper` that can deserialize `VolunteerActivity` from partial JSON (used by Task 4's controller changes and Task 6's frontend form).

- [ ] **Step 1: Switch to file-based H2 in `application.properties`**

```properties
spring.application.name=demo

# JDBC URL (H2 file-based database — persists across restarts)
spring.datasource.url=jdbc:h2:file:./data/volomap
spring.datasource.username=sa
spring.datasource.password=

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

- [ ] **Step 2: Ignore the new DB file directory**

Add to `.gitignore` under the `Backend / Spring Boot` section (after `/backend/logs/`):

```
/backend/data/
```

- [ ] **Step 3: Only seed mock data when the DB is empty, in `VoloMapApp.kt`**

```kotlin
package com.example.VoloMap

import com.example.VoloMap.server.Scraper
import com.example.VoloMap.server.VolunteerActivityRepository
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VoloMapApp

fun main(args: Array<String>) {
    val context = runApplication<VoloMapApp>(*args)

    val repository = context.getBean(VolunteerActivityRepository::class.java)
    if (repository.count() == 0L) {
        val scraper = context.getBean(Scraper::class.java)
        scraper.fakeScraper(30)
    }
    /*
    scraper.scrapeWebsite(
        "https://engagementdatenbank.stadt-koeln.de/ergebnisse?fulltext=&id=&area_of_activity=All&target_group=All&postal_code=&page=1",
        "page", 20
    )*/
}
```

- [ ] **Step 4: Dedupe the `h2` dependency and add the Jackson Kotlin module + test-mocking library in `build.gradle.kts`**

Replace the `dependencies { ... }` block with:

```kotlin
    dependencies {
        implementation("org.springframework.boot:spring-boot-starter")
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.springframework.boot:spring-boot-starter-web")
        implementation("org.springframework.boot:spring-boot-starter-data-jpa")
        implementation("org.jsoup:jsoup:1.17.2") //Scraping
        implementation("org.json:json:20240303")
        implementation("tools.jackson.module:jackson-module-kotlin:3.0.0")
        runtimeOnly("com.h2database:h2")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }
```

- [ ] **Step 5: Register the Kotlin module explicitly (belt-and-braces)**

Spring Boot 4's Jackson 3 auto-registration of discovered modules has had bugs in early releases; registering it explicitly as a `Module` bean is the documented, reliable path (`JacksonAutoConfiguration` auto-registers all `Module` beans onto the auto-configured `JsonMapper`). Create:

```kotlin
package com.example.VoloMap.server

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.module.kotlin.KotlinModule

@Configuration
class JacksonConfig {
    @Bean
    fun kotlinModule(): KotlinModule = KotlinModule.Builder().build()
}
```

If this doesn't compile as-is (API surface differs slightly from the Jackson 2 version), run `cd backend && ./gradlew.bat compileKotlin` and adjust the constructor call to whatever `tools.jackson.module.kotlin.KotlinModule` actually exposes (the class ships a public builder or no-arg constructor — the compiler error will name the available option).

- [ ] **Step 6: Verify the fix end-to-end**

```bash
cd backend && ./gradlew.bat bootRun
```

In a second terminal, once it logs `Started VoloMapAppKt`:

```bash
curl -s -i -X POST "http://localhost:8080/add" -H "Content-Type: application/json" -d "{\"name\":\"Testeintrag\",\"description\":\"Testbeschreibung\",\"addressText\":\"Domkloster 4, Koeln\",\"category\":\"Test\"}"
```

Expected: `HTTP/1.1 200` with a JSON body containing the saved activity (previously this returned `400`).

Then stop the app (`Ctrl+C`), start it again, and confirm via `curl -s http://localhost:8080/markers` that the count of returned markers did **not** reset to a fresh batch of 30 random ones (i.e. your test entry from above is still present, and re-running doesn't duplicate the mock set).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/application.properties backend/src/main/kotlin/com/example/VoloMap/VoloMapApp.kt backend/build.gradle.kts backend/src/main/kotlin/com/example/VoloMap/server/JacksonConfig.kt .gitignore
git commit -m "fix: persist H2 to disk, seed mock data only when empty, fix Jackson Kotlin deserialization for POST /add, dedupe h2 dependency"
```

---

### Task 4: `/add` geocodiert fehlende Koordinaten + Tests

**Context:** Manually-added activities have no `latitude`/`longitude`. `GET /markers` filters out any activity without coordinates (`.filter { it.latitude != null && it.longitude != null }`), so without this, anything added via the new form (Task 6) would silently never appear on the map.

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt`

**Interfaces:**
- Consumes: `GeocodingService.geocode(address: String): Pair<Double, Double>?` (Task 2)
- Produces: `POST /add` now returns the saved `VolunteerActivity` with `latitude`/`longitude` populated whenever `addressText` is set and geocoding succeeds; unchanged (still saved, still `200`) when it fails.

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt`:

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MainControllerAddActivityTest {

    @Test
    fun `geocodes address when coordinates are missing`() {
        val repository = mock<VolunteerActivityRepository>()
        val geocodingService = mock<GeocodingService>()
        whenever(geocodingService.geocode("Domkloster 4, Köln")).thenReturn(Pair(50.9413, 6.9583))
        whenever(repository.save(any())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService)
        val activity = VolunteerActivity(name = "Test", addressText = "Domkloster 4, Köln")

        val result = controller.addActivity(activity)

        assertEquals(50.9413, result.body?.latitude)
        assertEquals(6.9583, result.body?.longitude)
        verify(geocodingService).geocode("Domkloster 4, Köln")
    }

    @Test
    fun `saves activity without coordinates when geocoding finds nothing`() {
        val repository = mock<VolunteerActivityRepository>()
        val geocodingService = mock<GeocodingService>()
        whenever(geocodingService.geocode(any())).thenReturn(null)
        whenever(repository.save(any())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService)
        val activity = VolunteerActivity(name = "Test", addressText = "Nonexistent Place XYZ")

        val result = controller.addActivity(activity)

        assertEquals(200, result.statusCode.value())
        assertNull(result.body?.latitude)
        assertNull(result.body?.longitude)
    }

    @Test
    fun `does not call geocoding when coordinates are already set`() {
        val repository = mock<VolunteerActivityRepository>()
        val geocodingService = mock<GeocodingService>()
        whenever(repository.save(any())).thenAnswer { it.arguments[0] }

        val controller = MainController(repository, geocodingService)
        val activity = VolunteerActivity(name = "Test", latitude = 1.0, longitude = 2.0)

        controller.addActivity(activity)

        verify(geocodingService, org.mockito.kotlin.never()).geocode(any())
    }
}
```

- [ ] **Step 2: Run the tests to see them fail (compile error — `MainController` doesn't take a `GeocodingService` yet, and `addActivity` doesn't geocode)**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerAddActivityTest"`
Expected: `FAILED` (compilation error referencing the 2-arg `MainController` constructor not existing yet).

- [ ] **Step 3: Implement it in `MainController.kt`**

Add the constructor dependency and update `addActivity`:

```kotlin
@CrossOrigin(origins = ["http://localhost:5173"])
@RestController
class MainController(
    private val repository: VolunteerActivityRepository,
    private val geocodingService: GeocodingService
) {
```

```kotlin
@PostMapping("/add")
fun addActivity(
    @RequestBody activity: VolunteerActivity
): ResponseEntity<VolunteerActivity> {
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
```

- [ ] **Step 4: Run the tests again to confirm they pass**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerAddActivityTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerAddActivityTest.kt
git commit -m "feat: geocode address on manual activity creation when coordinates are missing"
```

---

### Task 5: Regressionstest für kombinierte `/markers`-Filter

**Context:** `MainController.markers()` already combines `category`/`date`/`timeFrom`/`timeTo`/`search` correctly server-side — this task adds a test to lock that behavior in before Task 6/7 build more UI on top of it, and to protect it once Task 8 changes how the frontend calls it.

**Files:**
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt`

**Interfaces:**
- Consumes: `MainController(repository, geocodingService)` constructor (Task 4)

- [ ] **Step 1: Write the test**

Create `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt`:

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class MainControllerMarkersTest {

    private fun activity(
        name: String,
        category: String,
        addressText: String,
        dateTime: LocalDateTime,
        lat: Double = 50.0,
        lng: Double = 6.0
    ) = VolunteerActivity(
        name = name,
        category = category,
        addressText = addressText,
        dateTime = dateTime,
        latitude = lat,
        longitude = lng
    )

    @Test
    fun `combines category, search and time range filters with AND semantics`() {
        val repository = mock<VolunteerActivityRepository>()
        val matching = activity(
            name = "Umweltaktion Park",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        val wrongCategory = activity(
            name = "Umweltaktion Wald",
            category = "Soziales",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        val wrongTime = activity(
            name = "Umweltaktion Abends",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 20, 0)
        )
        val wrongSearch = activity(
            name = "Seniorenbegleitung",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        whenever(repository.findAll()).thenReturn(
            listOf(matching, wrongCategory, wrongTime, wrongSearch)
        )

        val controller = MainController(repository, mock())
        val result = controller.markers(
            category = "Umwelt",
            date = null,
            timeFrom = 8,
            timeTo = 12,
            search = "Umweltaktion"
        )

        assertEquals(1, result.size)
        assertEquals("Umweltaktion Park", result[0].name)
    }

    @Test
    fun `search matches name, address or description case-insensitively`() {
        val repository = mock<VolunteerActivityRepository>()
        val byDescription = VolunteerActivity(
            name = "Projekt A",
            description = "Hilfe im TIERHEIM Köln",
            latitude = 50.0,
            longitude = 6.0
        )
        val noMatch = VolunteerActivity(
            name = "Projekt B",
            description = "Nachbarschaftshilfe",
            latitude = 50.0,
            longitude = 6.0
        )
        whenever(repository.findAll()).thenReturn(listOf(byDescription, noMatch))

        val controller = MainController(repository, mock())
        val result = controller.markers(
            category = null, date = null, timeFrom = null, timeTo = null, search = "tierheim"
        )

        assertEquals(1, result.size)
        assertEquals("Projekt A", result[0].name)
    }
}
```

- [ ] **Step 2: Run the tests to confirm they pass against the existing (already-correct) filter logic**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerMarkersTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed. (No production code change expected here — this documents and locks in existing behavior.)

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt
git commit -m "test: lock in combined /markers filter behavior"
```

---

### Task 6: Frontend — Suche und Filter in `Map.svelte` kombinieren

**Context:** `handleSearch` and `handleFilter` each call `fetchMarkers` with only their own values, blanking out the other's. Replace with one shared query object.

**Files:**
- Modify: `frontend/src/lib/Map.svelte`

**Interfaces:**
- Consumes: `FilterBar`'s `filter` event (`{date, category, timeFrom, timeTo}`, unchanged), `SearchBar`'s `search` event (`string`, unchanged)
- Produces: `GET /markers` always called with the full current combination of both

- [ ] **Step 1: Replace the state and handlers in `Map.svelte`**

Replace the `<script>` block's state/fetch logic (everything from `let markers = ...` through `handleFilter`'s closing brace) with:

```ts
    let markers = [{category: "Brono", id: 1, lat: 50.9375, lng: 6.9603, name: "Erroror 1", address: "Erroror 1", dateTime: "2023-01-01T00:00:00Z" }];
    let categories = ["Brono", "Kino", "Kultur", "Sport"];

    let query = {
        date: "",
        category: "",
        timeFrom: "",
        timeTo: "",
        search: "",
    };

    onMount(async () => {
        const res = await fetch("http://localhost:8080/categories");
        categories = await res.json();
        fetchMarkers();
    });

    async function fetchMarkers() {
        const params = new URLSearchParams();

        if (query.date) params.append("date", query.date);
        if (query.category) params.append("category", query.category);
        if (query.timeFrom) params.append("timeFrom", query.timeFrom);
        if (query.timeTo) params.append("timeTo", query.timeTo);
        if (query.search) params.append("search", query.search);

        const res = await fetch(
            "http://localhost:8080/markers?" + params.toString()
        );

        markers = await res.json();
    }

    function handleSearch(event: CustomEvent<string>) {
        query = { ...query, search: event.detail };
        fetchMarkers();
    }

    function handleFilter(event: CustomEvent<{
        date: string | null;
        category: string | null;
        timeFrom: number | null;
        timeTo: number | null;
    }>) {
        const { date, category, timeFrom, timeTo } = event.detail;
        query = {
            ...query,
            date: date ?? "",
            category: category ?? "",
            timeFrom: timeFrom?.toString() ?? "",
            timeTo: timeTo?.toString() ?? "",
        };
        fetchMarkers();
    }
```

Remove the now-unused standalone `let currentSearch = "";` line further down in the file (it's superseded by `query.search`).

- [ ] **Step 2: Manual verification in the browser**

```bash
cd backend && ./gradlew.bat bootRun
```

In another terminal:

```bash
cd frontend && npm run dev
```

Open the printed local URL. Set a category filter, then type a search term that only matches entries **within** that category (check `VolunteerList` below the map). Confirm the marker list narrows to entries matching both, and clearing the search keeps the category filter applied (previously it would silently drop back to only the search results or only the filter results).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/Map.svelte
git commit -m "fix: combine search and filter state instead of overwriting each other"
```

---

### Task 7: Frontend — `AddActivity.svelte`-Formular

**Context:** Backend `/add` now works end-to-end (Task 3, Task 4). Build the form that was left as an empty placeholder file.

**Files:**
- Modify: `frontend/src/lib/AddActivity.svelte` (currently empty)

**Interfaces:**
- Consumes: `POST http://localhost:8080/add` (body: `{name, description, addressText, category, dateTime}`, returns saved `VolunteerActivity` including `latitude`/`longitude` or `null`)
- Produces: nothing consumed by other tasks directly; Task 8 links to this component by route.

- [ ] **Step 1: Implement the form**

```svelte
<script lang="ts">
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
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    name,
                    description: description || null,
                    addressText: addressText || null,
                    category: category || null,
                    dateTime: dateTime ? new Date(dateTime).toISOString().slice(0, 19) : undefined,
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
        } finally {
            submitting = false;
        }
    }
</script>

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

<style>
    form {
        display: flex;
        flex-direction: column;
        gap: 10px;
        max-width: 420px;
        margin: 16px 0;
    }

    label {
        display: flex;
        flex-direction: column;
        gap: 4px;
        font-size: 0.9rem;
    }

    input,
    textarea {
        font-family: inherit;
        font-size: 0.9rem;
        padding: 6px 8px;
        border: 1px solid #ccc;
        border-radius: 4px;
    }

    button {
        align-self: flex-start;
        font-family: inherit;
        font-size: 0.9rem;
        padding: 6px 14px;
        border: 1px solid #333;
        border-radius: 4px;
        background: white;
        cursor: pointer;
    }

    button:disabled {
        opacity: 0.6;
        cursor: default;
    }

    p.warning {
        color: #a15c00;
    }
</style>
```

- [ ] **Step 2: Manual verification**

With backend (`./gradlew.bat bootRun`) and frontend (`npm run dev`) running, temporarily render `<AddActivity />` somewhere reachable (this gets wired into routing properly in Task 8 — for this step it's fine to drop `import AddActivity from "./lib/AddActivity.svelte";` and `<AddActivity />` into `Home.svelte` temporarily to test in isolation, then let Task 8 supersede it). Submit the form with a real Cologne address (e.g. `Domkloster 4, Köln`) and confirm the success message appears and a new marker shows up on the map after a refresh/refetch. Submit again with an address like `asdkjaslkdj123 nonexistent` and confirm the "konnte nicht gefunden werden" warning appears.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/AddActivity.svelte
git commit -m "feat: implement AddActivity form"
```

---

### Task 8: Frontend — Router verdrahten

**Context:** `App.svelte` renders `Map` directly and ignores the existing `Router`/`router.ts`/`Link` scaffolding; `Home.svelte` is an empty placeholder. Wire them together and add the `/add` route.

**Files:**
- Modify: `frontend/src/App.svelte`
- Modify: `frontend/src/router.ts`
- Modify: `frontend/src/pages/Home.svelte` (currently empty)
- Modify: `frontend/src/lib/NavBar.svelte`

**Interfaces:**
- Consumes: `Router.svelte`, `Link.svelte`, `route`/`routes`/`navigate` from `router.ts` (all pre-existing, unchanged), `AddActivity.svelte` (Task 7), `Map.svelte` (Task 6)
- Produces: working navigation between `/`, `/about`, `/add`

- [ ] **Step 1: Move the `Map` rendering into `Home.svelte`**

```svelte
<script lang="ts">
    import Map from "../lib/Map.svelte";
</script>

<Map />
```

- [ ] **Step 2: Add the `/add` route in `router.ts`**

```ts
import { writable } from "svelte/store";
import type { Component } from "svelte";

import Home from "./pages/Home.svelte";
import About from "./pages/About.svelte";
import AddActivity from "./lib/AddActivity.svelte";

export const route = writable<string>(window.location.pathname);

export const routes: Record<string, Component> = {
    "/": Home,
    "/about": About,
    "/add": AddActivity
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

- [ ] **Step 3: Render `NavBar` + `Router` in `App.svelte` instead of `Map` directly**

```svelte
<script>
    import NavBar from "./lib/NavBar.svelte";
    import Router from "./lib/Router.svelte";
</script>
<NavBar />
<h1>Benemap</h1>
<Router />
```

- [ ] **Step 4: Fix the dead `/map` link and add `/add` in `NavBar.svelte`**

`/map` isn't a registered route (the map lives at `/`), so replace it with the new `/add` link:

```svelte
<script lang="ts">
    import Link from "./Link.svelte";
</script>

<nav>
    <Link href="/">Home</Link>
    <Link href="/add">Aktivität hinzufügen</Link>
    <Link href="/about">About</Link>
</nav>
```

- [ ] **Step 5: Manual verification**

```bash
cd frontend && npm run dev
```

Open the app: confirm the map renders on `/`, clicking "Aktivität hinzufügen" navigates to `/add` and shows the form from Task 7 without a full page reload (URL bar updates via `history.pushState`), and clicking "Home" navigates back to the map. Confirm "About" still routes (even though its page content is a pre-existing empty placeholder — not part of this plan).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/App.svelte frontend/src/router.ts frontend/src/pages/Home.svelte frontend/src/lib/NavBar.svelte
git commit -m "feat: wire up client-side routing (add /add route, fix dead /map link)"
```

---

### Task 9: End-to-End-Verifikation des gesamten Bausteins

**Context:** Final pass tying all eight prior tasks together, matching the spec's "Ziel" section.

**Files:** none (verification only)

- [ ] **Step 1: Full backend test suite**

Run: `cd backend && ./gradlew.bat test`
Expected: `BUILD SUCCESSFUL`, all tests pass (including the pre-existing `DemoApplicationTests` context-load test).

- [ ] **Step 2: Persistence across restart**

```bash
cd backend && ./gradlew.bat bootRun
```

Add an activity via the `/add` form at `http://localhost:5173/add` (with `npm run dev` running). Note its name. Stop the backend (`Ctrl+C`), restart it, and confirm via the map/`VolunteerList` that the entry is still there and the mock dataset wasn't regenerated/duplicated.

- [ ] **Step 3: Combined search + filter**

On `/`, pick a category filter, then also type a search term. Confirm the result set respects both simultaneously (per Task 6), and that clearing one keeps the other applied.

- [ ] **Step 4: Navigation**

Click through `Home → Aktivität hinzufügen → About → Home` via the nav bar links and confirm each renders the right content without a full page reload.

- [ ] **Step 5: Report back**

Summarize pass/fail for steps 1-4. If everything passes, this sub-project is done — mark task #1 in the tracked task list (`Fundament fertigstellen`) as completed and move to sub-project #2 (`Mehrere Aktivitäten an einem Ort anzeigen`) in a fresh brainstorming/design pass.
