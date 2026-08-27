# Vermittlungsstelle-Kontaktdaten Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scrape and display the Vermittlungsstelle's (vermittelnde Organisation) Name, Homepage, E-Mail und Telefonnummer for Köln city offers, so users can contact the organization directly from the app.

**Architecture:** Four new nullable fields on `VolunteerActivity`, populated by the existing generic field-parsing in `Scraper.kt` (no new parsing logic needed — the fields are already read into a map, just not stored). The fields flow through `Marker` DTO → `MainController.markers()` → a new contact block in `PinDetailPanel.svelte`, styled like the existing provider block but structurally separate since a Vermittlungsstelle is not an app `User` account.

**Tech Stack:** Kotlin/Spring Boot/JPA (backend), Svelte 5 (frontend), JUnit 5 + Mockito-Kotlin (backend tests).

## Global Constraints

- Exactly four fields: `Name der Vermittlungsstelle`, `Homepage der Vermittlungsstelle`, `E-Mail der Vermittlungsstelle`, `Telefonnummer der Vermittlungsstelle`. The `Befristet` field is out of scope.
- New entity field names: `sourceContactName`, `sourceContactWebsite`, `sourceContactEmail`, `sourceContactPhone` — do NOT reuse `providerName`/`providerWebsiteUrl`, which are semantically tied to an app `User` account.
- No code-level backfill/update mechanism for already-imported activities — `existsBySourceUrl` dedup stays exactly as-is. Backfilling existing rows is a manual delete-and-rescrape operation, done once after this plan ships (see Task 3's rollout step).
- Each of the four fields is independently optional; a missing field just omits that line/row, never the whole feature.
- If `sourceContactName` is absent, the entire new contact block is omitted, even if other contact fields are present.

---

### Task 1: Store Vermittlungsstelle contact fields on the entity and scraper

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/Scraper.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/ScraperTest.kt`

**Interfaces:**
- Consumes: nothing new — uses the existing `data: MutableMap<String, String>` already built in `Scraper.buildActivityFromDocument` (from `document.select("div.field")`, keyed by the field's German label text).
- Produces: `VolunteerActivity` gains four new constructor properties, all `String? = null`:
  `sourceContactName`, `sourceContactWebsite`, `sourceContactEmail`, `sourceContactPhone`.
  Task 2 reads these four properties by name.

- [ ] **Step 1: Write the failing tests**

Open `backend/src/test/kotlin/com/example/VoloMap/server/ScraperTest.kt`. Add these two test methods anywhere inside the `ScraperTest` class (e.g. right after the existing `collapses a duplicated postal code in the address text` test):

```kotlin
    @Test
    fun `extracts Vermittlungsstelle contact fields from the detail page`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        whenever(geocodingService.geocode(any())).thenReturn(Pair(50.9, 6.95))

        val html = """
            <html><body>
                <div class="field">
                    <div class="field__label">Adresse der Vermittlungsstelle</div>
                    <div class="field__item">Domkloster 4, Köln</div>
                </div>
                <div class="field">
                    <div class="field__label">Name der Vermittlungsstelle</div>
                    <div class="field__item">Ceno &amp; Die Paten e.V.</div>
                </div>
                <div class="field">
                    <div class="field__label">Homepage der Vermittlungsstelle</div>
                    <div class="field__item"><a href="https://www.ceno-koeln.de/">https://www.ceno-koeln.de/</a></div>
                </div>
                <div class="field">
                    <div class="field__label">E-Mail der Vermittlungsstelle</div>
                    <div class="field__item"><a href="mailto:est@ceno-koeln.de">est@ceno-koeln.de</a></div>
                </div>
                <div class="field">
                    <div class="field__label">Telefonnummer der Vermittlungsstelle</div>
                    <div class="field__item">0221 1234567</div>
                </div>
            </body></html>
        """.trimIndent()
        val document = Jsoup.parse(html)

        val scraper = Scraper(repository, geocodingService)
        val activity = scraper.buildActivityFromDocument(
            document, "https://engagementdatenbank.stadt-koeln.de/testprojekt", "Testprojekt", "Bildung"
        )

        assertEquals("Ceno & Die Paten e.V.", activity.sourceContactName)
        assertEquals("https://www.ceno-koeln.de/", activity.sourceContactWebsite)
        assertEquals("est@ceno-koeln.de", activity.sourceContactEmail)
        assertEquals("0221 1234567", activity.sourceContactPhone)
    }

    @Test
    fun `contact fields are individually null when the detail page omits them`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        whenever(geocodingService.geocode(any())).thenReturn(Pair(50.9, 6.95))

        val html = """
            <html><body>
                <div class="field">
                    <div class="field__label">Adresse der Vermittlungsstelle</div>
                    <div class="field__item">Domkloster 4, Köln</div>
                </div>
                <div class="field">
                    <div class="field__label">Name der Vermittlungsstelle</div>
                    <div class="field__item">Ceno &amp; Die Paten e.V.</div>
                </div>
            </body></html>
        """.trimIndent()
        val document = Jsoup.parse(html)

        val scraper = Scraper(repository, geocodingService)
        val activity = scraper.buildActivityFromDocument(
            document, "https://engagementdatenbank.stadt-koeln.de/testprojekt", "Testprojekt", "Bildung"
        )

        assertEquals("Ceno & Die Paten e.V.", activity.sourceContactName)
        assertNull(activity.sourceContactWebsite)
        assertNull(activity.sourceContactEmail)
        assertNull(activity.sourceContactPhone)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.ScraperTest"`
Expected: FAIL — compile error, since `VolunteerActivity` has no `sourceContactName`/`sourceContactWebsite`/`sourceContactEmail`/`sourceContactPhone` properties yet.

- [ ] **Step 3: Add the four fields to `VolunteerActivity`**

Open `backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt`. Find the `sourceUrl` property (currently around line 35):

```kotlin
    // URL of the original listing, used for deduplication and linking back to source
    @Column(columnDefinition = "TEXT")
    var sourceUrl: String? = null,
```

Immediately after it, add:

```kotlin
    // Kontaktdaten der Vermittlungsstelle (vermittelnde Organisation), nur für
    // gescrapte Städtische Angebote befüllt — bewusst nicht providerName/
    // providerWebsiteUrl genannt, da diese an ein App-User-Konto gebunden sind.
    var sourceContactName: String? = null,
    var sourceContactWebsite: String? = null,
    var sourceContactEmail: String? = null,
    var sourceContactPhone: String? = null,
```

- [ ] **Step 4: Populate the fields in the scraper**

Open `backend/src/main/kotlin/com/example/VoloMap/server/Scraper.kt`. Find the `VolunteerActivity(...)` constructor call at the end of `buildActivityFromDocument` (currently around line 166):

```kotlin
        return VolunteerActivity(
            name = name,
            description = data["Beschreibung"],
            addressText = address,
            sourceUrl = url,
            category = category,
            latitude = coords?.first,
            longitude = coords?.second,
            dateTime = null
        )
```

Replace it with:

```kotlin
        return VolunteerActivity(
            name = name,
            description = data["Beschreibung"],
            addressText = address,
            sourceUrl = url,
            category = category,
            latitude = coords?.first,
            longitude = coords?.second,
            dateTime = null,
            sourceContactName = data["Name der Vermittlungsstelle"],
            sourceContactWebsite = data["Homepage der Vermittlungsstelle"],
            sourceContactEmail = data["E-Mail der Vermittlungsstelle"],
            sourceContactPhone = data["Telefonnummer der Vermittlungsstelle"],
        )
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.ScraperTest"`
Expected: PASS (all `ScraperTest` tests, including the two new ones).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt backend/src/main/kotlin/com/example/VoloMap/server/Scraper.kt backend/src/test/kotlin/com/example/VoloMap/server/ScraperTest.kt
git commit -m "feat: scrape and store Vermittlungsstelle contact fields"
```

---

### Task 2: Expose contact fields through the markers API

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt`

**Interfaces:**
- Consumes: `VolunteerActivity.sourceContactName/Website/Email/Phone` (from Task 1).
- Produces: `Marker` gains four new `val` properties of type `String?`, same names as the entity fields (`sourceContactName`, `sourceContactWebsite`, `sourceContactEmail`, `sourceContactPhone`). Task 3's frontend reads these by the same names via the `/markers` JSON response.

- [ ] **Step 1: Write the failing test**

Open `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt`. Add this test method inside the `MainControllerMarkersTest` class, right after the existing `sourceUrl is null for an activity added through the normal form` test:

```kotlin
    @Test
    fun `includes Vermittlungsstelle contact fields when the activity was scraped`() {
        val repository = mock<VolunteerActivityRepository>()
        val scraped = activity(
            name = "Gescraptes Angebot",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        ).also {
            it.sourceContactName = "Ceno & Die Paten e.V."
            it.sourceContactWebsite = "https://www.ceno-koeln.de/"
            it.sourceContactEmail = "est@ceno-koeln.de"
            it.sourceContactPhone = "0221 1234567"
        }
        whenever(repository.findAll()).thenReturn(listOf(scraped))

        val controller = MainController(repository, mock(), mock(), mock(), mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals("Ceno & Die Paten e.V.", result[0].sourceContactName)
        assertEquals("https://www.ceno-koeln.de/", result[0].sourceContactWebsite)
        assertEquals("est@ceno-koeln.de", result[0].sourceContactEmail)
        assertEquals("0221 1234567", result[0].sourceContactPhone)
    }

    @Test
    fun `Vermittlungsstelle contact fields are null for an activity added through the normal form`() {
        val repository = mock<VolunteerActivityRepository>()
        val ownActivity = activity(
            name = "Eigene Aktivität",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        )
        whenever(repository.findAll()).thenReturn(listOf(ownActivity))

        val controller = MainController(repository, mock(), mock(), mock(), mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertNull(result[0].sourceContactName)
        assertNull(result[0].sourceContactWebsite)
        assertNull(result[0].sourceContactEmail)
        assertNull(result[0].sourceContactPhone)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerMarkersTest"`
Expected: FAIL — compile error, `Marker` has no `sourceContactName`/`sourceContactWebsite`/`sourceContactEmail`/`sourceContactPhone` properties yet.

- [ ] **Step 3: Add the four fields to `Marker`**

Open `backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt`. Find the `sourceUrl` property (currently the last line before the closing paren, line 23):

```kotlin
    val sourceUrl: String?,
)
```

Replace with:

```kotlin
    val sourceUrl: String?,
    val sourceContactName: String?,
    val sourceContactWebsite: String?,
    val sourceContactEmail: String?,
    val sourceContactPhone: String?,
)
```

- [ ] **Step 4: Populate the fields in `MainController.markers()`**

Open `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`. Find the `sourceUrl = activity.sourceUrl,` line inside the `Marker(...)` construction (currently line 69):

```kotlin
                    sourceUrl = activity.sourceUrl,
                )
```

Replace with:

```kotlin
                    sourceUrl = activity.sourceUrl,
                    sourceContactName = activity.sourceContactName,
                    sourceContactWebsite = activity.sourceContactWebsite,
                    sourceContactEmail = activity.sourceContactEmail,
                    sourceContactPhone = activity.sourceContactPhone,
                )
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd backend && ./gradlew.bat test --tests "com.example.VoloMap.server.MainControllerMarkersTest"`
Expected: PASS (all tests in this file).

- [ ] **Step 6: Run the full backend test suite**

Run: `cd backend && ./gradlew.bat test`
Expected: PASS (no regressions in other tests — `MainController.markers()` is the only place that constructs a `Marker`, and Step 4 already updated it; `VolunteerActivity` is constructed in many test files, but its new fields default to `null`, and all existing call sites use named arguments, so they compile unchanged).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt
git commit -m "feat: expose Vermittlungsstelle contact fields via the markers API"
```

---

### Task 3: Display the contact block and roll out to real data

**Files:**
- Modify: `frontend/src/lib/PinDetailPanel.svelte`

**Interfaces:**
- Consumes: `sourceContactName`, `sourceContactWebsite`, `sourceContactEmail`, `sourceContactPhone` (all `string | null`) from the `/markers` JSON response (Task 2).
- Produces: nothing consumed by later tasks — this is the last task in the plan.

- [ ] **Step 1: Add the four fields to the `marker` prop type**

Open `frontend/src/lib/PinDetailPanel.svelte`. Find the `export let marker` type declaration (currently lines 9-26):

```svelte
    export let marker: {
        id: number;
        name: string;
        address: string;
        category: string;
        description: string;
        photoUrls: string[];
        dateTime: string | null;
        sourceUrl: string | null;
        activityRating: number | null;
        activityRatingCount: number;
        providerId: number | null;
        providerName: string | null;
        providerPhotoUrl: string | null;
        providerWebsiteUrl: string | null;
        providerRating: number | null;
        providerRatingCount: number;
    };
```

Replace with:

```svelte
    export let marker: {
        id: number;
        name: string;
        address: string;
        category: string;
        description: string;
        photoUrls: string[];
        dateTime: string | null;
        sourceUrl: string | null;
        sourceContactName: string | null;
        sourceContactWebsite: string | null;
        sourceContactEmail: string | null;
        sourceContactPhone: string | null;
        activityRating: number | null;
        activityRatingCount: number;
        providerId: number | null;
        providerName: string | null;
        providerPhotoUrl: string | null;
        providerWebsiteUrl: string | null;
        providerRating: number | null;
        providerRatingCount: number;
    };
```

- [ ] **Step 2: Add the contact block to the template**

In the same file, find the existing source-link block (currently lines 102-106):

```svelte
    {#if marker.sourceUrl && (marker.sourceUrl.startsWith("http://") || marker.sourceUrl.startsWith("https://"))}
        <a class="source-link" href={marker.sourceUrl} target="_blank" rel="noopener noreferrer">
            Mehr Infos auf der Webseite der Stadt Köln
        </a>
    {/if}
```

Immediately after it (still before the `<button class="rating-badge" ...>` line), add:

```svelte
    {#if marker.sourceContactName}
        <div class="source-contact">
            <span class="source-contact-name">{marker.sourceContactName}</span>
            {#if marker.sourceContactWebsite}
                <a class="source-contact-link" href={marker.sourceContactWebsite} target="_blank" rel="noopener noreferrer">Website besuchen</a>
            {/if}
            {#if marker.sourceContactEmail}
                <a class="source-contact-link" href={`mailto:${marker.sourceContactEmail}`}>{marker.sourceContactEmail}</a>
            {/if}
            {#if marker.sourceContactPhone}
                <span class="source-contact-phone">{marker.sourceContactPhone}</span>
            {/if}
        </div>
    {/if}
```

- [ ] **Step 3: Add styling for the new block**

In the same file, find the existing `.provider` styles (currently lines 282-314, ending with `.provider-website`). Immediately after the closing `}` of `.provider-website`, add:

```css

    .source-contact {
        display: flex;
        flex-direction: column;
        gap: 4px;
        margin-top: 4px;
        padding-top: 10px;
        border-top: 1px solid var(--color-border);
    }

    .source-contact-name {
        font-size: 0.85rem;
        font-weight: 600;
        color: var(--color-text);
    }

    .source-contact-link {
        font-size: 0.8rem;
        color: var(--color-primary);
        align-self: flex-start;
    }

    .source-contact-phone {
        font-size: 0.8rem;
        color: var(--color-text-muted);
    }
```

- [ ] **Step 4: Type-check the frontend**

Run: `cd frontend && npm run check`
Expected: PASS, no TypeScript/Svelte errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/PinDetailPanel.svelte
git commit -m "feat: show Vermittlungsstelle contact info in the detail panel"
```

- [ ] **Step 6: Manual verification — reset and re-scrape real data**

The activities already imported into the local dev database were scraped before this feature existed, so they have no contact fields stored (`existsBySourceUrl` permanently skips known listings — see Global Constraints). Reset them once so they pick up the new fields:

1. Stop any running backend process.
2. Connect to the local H2 database file (`backend/data/volomap.mv.db`, or the repo-root `data/volomap.mv.db` if that is the one actually in use — check both) and run:
   ```sql
   DELETE FROM volunteer_activities WHERE source_url IS NOT NULL;
   ```
3. Start the backend with the `--scrape` flag so it re-imports all Köln city offers, now including the four contact fields:
   `./gradlew.bat bootRun --args='--scrape'` (run from `backend/`).
4. Once scraping finishes, open the app in a browser, click on several pins that show the dashed "Städtische Angebote" border, and confirm:
   - The new contact block appears with the Vermittlungsstelle's name.
   - Where present, the homepage link opens in a new tab, and the e-mail link opens the system mail client (`mailto:`).
   - At least one activity without a phone number correctly omits that line without breaking the layout.
   - Activities without `sourceUrl` (app-native or the pre-existing fake seed data, if any remains) show no contact block at all.

---
