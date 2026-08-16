# Städtische Angebote (Köln) Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein Toggle blendet gescrapte, undatierte "Städtische Angebote" (Kölner Engagementdatenbank) zusätzlich zu den App-eigenen Aktivitäten auf Karte und Liste ein/aus.

**Architecture:** `VolunteerActivity.dateTime` wird nullable (der Scraper setzt bewusst kein Datum mehr); `Marker` bekommt ein neues `sourceUrl`-Feld, an dem das Frontend "extern" erkennt. Ein neuer Toggle in `FilterBar.svelte` steuert einen rein clientseitigen Filter in `Map.svelte` — kein neuer Server-Request beim Umschalten.

**Tech Stack:** Kotlin/Spring Boot + Hibernate/H2 (Backend), Svelte 5 legacy-style (Frontend), kein Test-Framework im Frontend (manuelle Browser-Verifikation, etablierte Konvention).

## Global Constraints

- Datenfluss: Scraper importiert vorab in die eigene DB, der Toggle filtert nur die bereits geladene `/markers`-Antwort — kein Live-Request an die Stadt-Webseite beim Umschalten.
- "Extern" wird ausschließlich aus `sourceUrl != null` abgeleitet — kein neues Boolean-Flag.
- Städtische Angebote erscheinen bei aktiviertem Toggle sowohl auf der Karte als auch im Bottom-Sheet (`VolunteerList`) — keine Sonderbehandlung dort.
- Toggle-Default: aus (Opt-in). Keine Persistenz über Reloads hinweg.
- Pin-Stil für einzelne (nicht geclusterte) externe Punkte: gestrichelter/andersfarbiger Rand statt des Standard-Kreisrands. Cluster-Pins bleiben optisch unverändert (Ring mit Anzahl).
- Kein Bearbeiten/Löschen für Städtische Angebote — ergibt sich automatisch aus `providerId == null` (bereits bestehende `isOwner`-Prüfung), keine neue Logik nötig.
- `VolunteerActivity.dateTime` wird `LocalDateTime?` (vorher `LocalDateTime`), Default bleibt `LocalDateTime.now()` — nur der Scraper übergibt explizit `null`. Jeder andere bestehende Aufrufer ist dadurch unverändert.
- Jede Stelle, die `marker.dateTime`/`member.dateTime` zum Anzeigen formatiert, muss null-sicher werden (kein "Invalid Date" in der UI).

---

### Task 1: Backend — nullable `dateTime`, `Marker.sourceUrl`, Scraper-Fix

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/Scraper.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/ScraperTest.kt` (neu)

**Interfaces:**
- Produces: `Marker.sourceUrl: String?` (neues Feld, letztes in der Datenklasse); `VolunteerActivity.dateTime: LocalDateTime?` (Default weiterhin `LocalDateTime.now()`); `Scraper.buildActivityFromDocument(document: Document, url: String): VolunteerActivity` (neue, pure/testbare Methode, kein Netzwerkzugriff).
- Consumes: nichts aus anderen Tasks dieses Plans.

- [ ] **Step 1: Fehlschlagenden Test für `Marker.sourceUrl` schreiben**

An `backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt` anhängen (vor der schließenden `}` der Klasse):

```kotlin

    @Test
    fun `includes sourceUrl when the activity was scraped`() {
        val repository = mock<VolunteerActivityRepository>()
        val scraped = activity(
            name = "Gescraptes Angebot",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            dateTime = LocalDateTime.of(2026, 8, 10, 9, 0)
        ).also { it.sourceUrl = "https://engagementdatenbank.stadt-koeln.de/testprojekt" }
        whenever(repository.findAll()).thenReturn(listOf(scraped))

        val controller = MainController(repository, mock(), mock(), mock(), mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertEquals("https://engagementdatenbank.stadt-koeln.de/testprojekt", result[0].sourceUrl)
    }

    @Test
    fun `sourceUrl is null for an activity added through the normal form`() {
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

        assertNull(result[0].sourceUrl)
    }

    @Test
    fun `dateTime is null when an activity has no scheduled appointment`() {
        val repository = mock<VolunteerActivityRepository>()
        val undated = VolunteerActivity(
            name = "Ohne Termin",
            category = "Umwelt",
            addressText = "Kölner Innenstadt",
            latitude = 50.0,
            longitude = 6.0,
            dateTime = null
        )
        whenever(repository.findAll()).thenReturn(listOf(undated))

        val controller = MainController(repository, mock(), mock(), mock(), mock())
        val result = controller.markers(category = null, date = null, timeFrom = null, timeTo = null, search = null)

        assertNull(result[0].dateTime)
    }
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `cd backend && .\gradlew.bat test --tests "com.example.VoloMap.server.MainControllerMarkersTest"`
Expected: FAIL — `Marker` hat noch kein `sourceUrl`-Feld (Kompilierfehler), `VolunteerActivity(dateTime = null)` kompiliert noch nicht (Typ ist noch nicht nullable).

- [ ] **Step 3: `VolunteerActivity.dateTime` nullable machen**

In `backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt`, die bestehende Zeile

```kotlin
    var dateTime: LocalDateTime = LocalDateTime.now(),
```

ersetzen durch:

```kotlin
    // Nullable, da nicht jede Quelle einen Termin liefert (z. B. gescrapte
    // Städtische Angebote der Kölner Engagementdatenbank, die keine
    // Termine führt). Der Default bleibt "jetzt" für alle bestehenden
    // Aufrufer, die dateTime nicht explizit setzen.
    var dateTime: LocalDateTime? = LocalDateTime.now(),
```

- [ ] **Step 4: `Marker.sourceUrl` ergänzen**

In `backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt`, die bestehende Zeile

```kotlin
    val providerRatingCount: Int,
)
```

ersetzen durch:

```kotlin
    val providerRatingCount: Int,
    val sourceUrl: String?,
)
```

- [ ] **Step 5: `MainController.markers()` befüllt `sourceUrl`**

In `backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt`, die bestehende Zeile

```kotlin
                    providerRatingCount = providerRatings.size,
                )
```

(innerhalb der `Marker(...)`-Konstruktion in `markers()`) ersetzen durch:

```kotlin
                    providerRatingCount = providerRatings.size,
                    sourceUrl = activity.sourceUrl,
                )
```

- [ ] **Step 6: Tests erneut ausführen, Erfolg bestätigen**

Run: `cd backend && .\gradlew.bat test --tests "com.example.VoloMap.server.MainControllerMarkersTest"`
Expected: PASS (9/9 — 6 bestehende + 3 neue).

- [ ] **Step 7: Fehlschlagenden Test für den Scraper-Fix schreiben**

Neue Datei `backend/src/test/kotlin/com/example/VoloMap/server/ScraperTest.kt`:

```kotlin
package com.example.VoloMap.server

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ScraperTest {

    @Test
    fun `built activity has no dateTime since the source tracks no appointment`() {
        val repository: VolunteerActivityRepository = mock()
        val geocodingService: GeocodingService = mock()
        whenever(geocodingService.geocode(any())).thenReturn(Pair(50.9413, 6.9583))

        val html = """
            <html><body>
                <div class="field">
                    <div class="field__label">Projektname</div>
                    <div class="field__item">Testprojekt</div>
                </div>
                <div class="field">
                    <div class="field__label">Adresse der Vermittlungsstelle</div>
                    <div class="field__item">Domkloster 4, Köln</div>
                </div>
            </body></html>
        """.trimIndent()
        val document = Jsoup.parse(html)

        val scraper = Scraper(repository, geocodingService)
        val activity = scraper.buildActivityFromDocument(document, "https://engagementdatenbank.stadt-koeln.de/testprojekt")

        assertNull(activity.dateTime)
        assertEquals("Testprojekt", activity.name)
        assertEquals("Domkloster 4, Köln", activity.addressText)
        assertEquals("https://engagementdatenbank.stadt-koeln.de/testprojekt", activity.sourceUrl)
        assertEquals(50.9413, activity.latitude)
        assertEquals(6.9583, activity.longitude)
    }
}
```

- [ ] **Step 8: Test ausführen, Fehlschlag bestätigen**

Run: `cd backend && .\gradlew.bat test --tests "com.example.VoloMap.server.ScraperTest"`
Expected: FAIL — `Scraper.buildActivityFromDocument` existiert noch nicht (Kompilierfehler).

- [ ] **Step 9: `Scraper.kt` — `scrapeEhrenamtDetails` in testbare Methode aufteilen, `dateTime` nicht mehr setzen**

In `backend/src/main/kotlin/com/example/VoloMap/server/Scraper.kt`, die komplette bestehende Methode

```kotlin
    fun scrapeEhrenamtDetails(url: String) {
        // Skip if already in DB
        if (repository.existsBySourceUrl(url)) {
            println("Skipping (already exists): $url")
            return
        }

        val document = getDocument(url)
        val fields = document.select("div.field")
        val data = mutableMapOf<String, String>()

        fields.forEach { field ->
            val label = field.select("div.field__label").text()
            val items = field.select("div.field__item")
            val value = items.joinToString(", ") { item ->
                val link = item.select("a")
                when {
                    link.isEmpty() -> item.text()
                    link.attr("href").startsWith("mailto:") -> item.text()
                    link.attr("href").startsWith("http") -> link.attr("href")
                    else -> item.text()
                }
            }
            if (label.isNotEmpty() && value.isNotEmpty()) {
                data[label] = value
            }
        }

        val coords = data["Adresse der Vermittlungsstelle"]?.let {
            geocodingService.geocode(it)
        }
        println("Gefundene Felder: ${data.keys}")


        val activity = VolunteerActivity(
            name = data["Projektname"] ?: "Unbekannt",
            description = data["Beschreibung"],
            addressText = data["Adresse der Vermittlungsstelle"],
            sourceUrl = url,
            category = data["Tätigkeitsbereich"] ?: listOf("Unbekannt", "Umwelthilfe", "Ehre")[ Random.nextInt(0, 2)],
            latitude = coords?.first,
            longitude = coords?.second,
            dateTime = LocalDateTime.now()
        )

        repository.save(activity)
        println("Saved: ${activity.name} (lat=${coords?.first}, lng=${coords?.second})")
    }
```

ersetzen durch:

```kotlin
    fun scrapeEhrenamtDetails(url: String) {
        // Skip if already in DB
        if (repository.existsBySourceUrl(url)) {
            println("Skipping (already exists): $url")
            return
        }

        val document = getDocument(url)
        val activity = buildActivityFromDocument(document, url)

        repository.save(activity)
        println("Saved: ${activity.name} (lat=${activity.latitude}, lng=${activity.longitude})")
    }

    fun buildActivityFromDocument(document: Document, url: String): VolunteerActivity {
        val fields = document.select("div.field")
        val data = mutableMapOf<String, String>()

        fields.forEach { field ->
            val label = field.select("div.field__label").text()
            val items = field.select("div.field__item")
            val value = items.joinToString(", ") { item ->
                val link = item.select("a")
                when {
                    link.isEmpty() -> item.text()
                    link.attr("href").startsWith("mailto:") -> item.text()
                    link.attr("href").startsWith("http") -> link.attr("href")
                    else -> item.text()
                }
            }
            if (label.isNotEmpty() && value.isNotEmpty()) {
                data[label] = value
            }
        }

        val coords = data["Adresse der Vermittlungsstelle"]?.let {
            geocodingService.geocode(it)
        }
        println("Gefundene Felder: ${data.keys}")

        // dateTime bleibt null: die Kölner Engagementdatenbank führt für
        // diese Angebote keine Termine, ein "jetzt"-Zeitstempel wäre irreführend.
        return VolunteerActivity(
            name = data["Projektname"] ?: "Unbekannt",
            description = data["Beschreibung"],
            addressText = data["Adresse der Vermittlungsstelle"],
            sourceUrl = url,
            category = data["Tätigkeitsbereich"] ?: listOf("Unbekannt", "Umwelthilfe", "Ehre")[ Random.nextInt(0, 2)],
            latitude = coords?.first,
            longitude = coords?.second,
            dateTime = null
        )
    }
```

- [ ] **Step 10: Test erneut ausführen, Erfolg bestätigen**

Run: `cd backend && .\gradlew.bat test --tests "com.example.VoloMap.server.ScraperTest"`
Expected: PASS (1/1).

- [ ] **Step 11: Vollen Backend-Testlauf ausführen**

Run: `cd backend && .\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`, keine fehlgeschlagenen Tests.

- [ ] **Step 12: Empirisch prüfen, dass die DB-Spalte tatsächlich NULL akzeptiert**

Dies ist der im Design besprochene Risiko-Check: `ddl-auto=update` fügt zuverlässig neue Spalten hinzu, ändert aber bestehende `NOT NULL`-Constraints nicht immer zuverlässig.

Backend starten: `cd backend && .\gradlew.bat bootRun`. In einem zweiten Terminal (oder über die Browser-DevTools-Konsole gegen `http://localhost:8080`) einen Request schicken, der eine Aktivität mit `dateTime: null` anlegt — am einfachsten über `fakeScraper`-artige Direktnutzung ist nicht ohne Weiteres von außen erreichbar; stattdessen den echten Scraper testweise gegen eine einzelne echte URL laufen lassen (z. B. über eine kurze, temporäre `@GetMapping`-Testroute ist nicht Teil dieses Plans) — **einfacher:** direkt einen Insert über die H2-Konsole/`ScraperTest` reicht als Nachweis, dass die Spalte softwareseitig `null` akzeptiert; der eigentliche Beweis ist der Datenbank-Insert beim Speichern.

Praktikabler Check: `cd backend && .\gradlew.bat test --tests "com.example.VoloMap.server.MainControllerMarkersTest"` deckt nur das Mapping ab (Mockito, kein echter DB-Insert). Für den echten Insert: Backend starten, dann im Log nach `Hibernate: alter table if exists volunteer_activities alter column date_time` suchen ODER — falls keine solche Zeile erscheint und stattdessen beim späteren tatsächlichen Scraper-Lauf (Task 4) ein `NULL not allowed for column "DATE_TIME"`-Fehler auftritt — die lokale Dev-Datenbank zurücksetzen:

```bash
cd backend && rm -rf data
```

Backend danach neu starten (`./gradlew.bat bootRun`) — Hibernate legt das Schema mit der jetzt korrekten (nullable) Spalte frisch an. Dieser Schritt wird nur bei Bedarf ausgeführt (wenn Task 4 tatsächlich einen `NOT NULL`-Fehler zeigt), nicht präventiv, da `data/` reine lokale Testdaten enthält und nicht versioniert ist.

- [ ] **Step 13: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/VolunteerActivity.kt backend/src/main/kotlin/com/example/VoloMap/server/Marker.kt backend/src/main/kotlin/com/example/VoloMap/server/MainController.kt backend/src/main/kotlin/com/example/VoloMap/server/Scraper.kt backend/src/test/kotlin/com/example/VoloMap/server/MainControllerMarkersTest.kt backend/src/test/kotlin/com/example/VoloMap/server/ScraperTest.kt
git commit -m "feat: make dateTime nullable and expose sourceUrl for scraped activities"
```

---

### Task 2: Frontend — Toggle-Infrastruktur (FilterBar, Map, Cluster, Gruppierung)

**Files:**
- Modify: `frontend/src/lib/FilterBar.svelte`
- Modify: `frontend/src/lib/Map.svelte`
- Modify: `frontend/src/lib/groupByLocation.ts`
- Modify: `frontend/src/lib/ClusterMarker.svelte`

**Interfaces:**
- Consumes: `Marker.sourceUrl: string | null` aus Task 1 (über `/markers` bereits im Response enthalten, kein weiteres Backend-Interface nötig).
- Produces: `FilterBar` dispatcht `toggleCityOffers: boolean`. `Map.svelte` hält `showCityOffers: boolean` und leitet `visibleMarkers` ab, das `VolunteerList` und die Karten-Punkte konsumieren (für Task 3 relevant, falls dort ebenfalls auf `visibleMarkers`/`sourceUrl` referenziert wird — ist hier aber nicht der Fall, Task 3 arbeitet nur auf dem einzelnen `marker`/`editingMarker`, das ohnehin schon `sourceUrl` mitführt).

- [ ] **Step 1: `groupByLocation.ts` — `dateTime` nullable, Sortierung null-sicher**

In `frontend/src/lib/groupByLocation.ts`, die komplette Datei ersetzen durch:

```typescript
export interface LocationGroup<T> {
    key: string;
    lat: number;
    lng: number;
    members: T[];
}

export function groupByLocation<T extends { lat: number; lng: number; dateTime: string | null }>(
    markers: T[]
): LocationGroup<T>[] {
    const groups = new Map<string, LocationGroup<T>>();

    for (const marker of markers) {
        const key = `${marker.lat.toFixed(5)},${marker.lng.toFixed(5)}`;
        let group = groups.get(key);
        if (!group) {
            group = { key, lat: marker.lat, lng: marker.lng, members: [] };
            groups.set(key, group);
        }
        group.members.push(marker);
    }

    for (const group of groups.values()) {
        group.members.sort((a, b) => {
            if (!a.dateTime) return 1;
            if (!b.dateTime) return -1;
            return a.dateTime.localeCompare(b.dateTime);
        });
    }

    return [...groups.values()];
}
```

- [ ] **Step 2: `ClusterMarker.svelte` — `dateTime` nullable, Datumszeile bedingt rendern**

In `frontend/src/lib/ClusterMarker.svelte`, die bestehende Zeile

```typescript
        dateTime: string;
```

ersetzen durch:

```typescript
        dateTime: string | null;
```

Und die bestehende Zeile

```svelte
                        <span class="cluster-row-date">{new Date(member.dateTime).toLocaleString("de-DE")}</span>
```

ersetzen durch:

```svelte
                        {#if member.dateTime}
                            <span class="cluster-row-date">{new Date(member.dateTime).toLocaleString("de-DE")}</span>
                        {/if}
```

- [ ] **Step 3: `svelte-check` laufen lassen, Fehlschlag in `Map.svelte` bestätigen**

Run: `cd frontend && npx svelte-check --tsconfig ./tsconfig.json`
Expected: neuer Fehler in `Map.svelte` — `marker.dateTime` (jetzt potenziell `string | null` über den `groupByLocation`-Generic-Typ) wird ungeprüft an `new Date(...)` übergeben. (Falls `svelte-check` wegen `any`-Typisierung von `markers` in `Map.svelte` keinen Fehler zeigt, weiter mit Step 4 — die Laufzeit-Absicherung ist trotzdem nötig.)

- [ ] **Step 4: `FilterBar.svelte` — Checkbox + `toggleCityOffers`-Event**

In `frontend/src/lib/FilterBar.svelte`, die bestehende Zeile

```typescript
    const dispatch = createEventDispatcher<{
        filter: {
            date: string | null;
            category: string | null;
            timeFrom: number | null;
            timeTo: number | null;
        };
    }>();

    let selectedCategory: string | null = null;
    let selectedDate: string | null = null;
    let selectedWeekday: number | null = null;
    let selectedTimeSlot: { label: string; from: number; to: number } | null = null;
```

ersetzen durch:

```typescript
    const dispatch = createEventDispatcher<{
        filter: {
            date: string | null;
            category: string | null;
            timeFrom: number | null;
            timeTo: number | null;
        };
        toggleCityOffers: boolean;
    }>();

    let selectedCategory: string | null = null;
    let selectedDate: string | null = null;
    let selectedWeekday: number | null = null;
    let selectedTimeSlot: { label: string; from: number; to: number } | null = null;
    let showCityOffers = false;
```

Die bestehende `reset()`-Funktion

```typescript
    function reset() {
        selectedCategory = null;
        selectedDate = null;
        selectedWeekday = null;
        selectedTimeSlot = null;
        apply();
    }
```

ersetzen durch:

```typescript
    function reset() {
        selectedCategory = null;
        selectedDate = null;
        selectedWeekday = null;
        selectedTimeSlot = null;
        showCityOffers = false;
        dispatch("toggleCityOffers", false);
        apply();
    }
```

Die bestehende Zeile

```typescript
    $: activeCount = [selectedCategory, selectedWeekday, selectedTimeSlot].filter(
        (v) => v !== null
    ).length;
```

ersetzen durch:

```typescript
    $: activeCount = [selectedCategory, selectedWeekday, selectedTimeSlot, showCityOffers ? true : null].filter(
        (v) => v !== null
    ).length;
```

Im Markup die bestehende Stelle

```svelte
            <button class="reset" on:click={reset}>Filter zurücksetzen</button>
```

ersetzen durch:

```svelte
            <div class="group">
                <label class="checkbox-row">
                    <input
                        type="checkbox"
                        bind:checked={showCityOffers}
                        on:change={() => dispatch("toggleCityOffers", showCityOffers)}
                    />
                    Städtische Angebote (Köln) anzeigen
                </label>
            </div>

            <button class="reset" on:click={reset}>Filter zurücksetzen</button>
```

Im `<style>`-Bereich, direkt nach dem bestehenden `.group-label { ... }`-Block einfügen:

```css
    .checkbox-row {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 0.85rem;
        color: var(--color-text);
        cursor: pointer;
    }
```

- [ ] **Step 5: `Map.svelte` — `showCityOffers`-State, `visibleMarkers`, Pin-Stil, null-sichere Tooltip-Datumszeile**

In `frontend/src/lib/Map.svelte`, die bestehende Zeile

```typescript
    let markers: any[] = [];
    $: markerGroups = groupByLocation(markers);
```

ersetzen durch:

```typescript
    let markers: any[] = [];
    let showCityOffers = false;
    $: visibleMarkers = markers.filter((m) => showCityOffers || !m.sourceUrl);
    $: markerGroups = groupByLocation(visibleMarkers);
```

Die bestehende Funktion

```typescript
    function handleSelect(event: CustomEvent<{ id: number }>) {
        selectedMarkerId = event.detail.id;
    }
```

ersetzen durch:

```typescript
    function handleSelect(event: CustomEvent<{ id: number }>) {
        selectedMarkerId = event.detail.id;
    }

    function handleToggleCityOffers(event: CustomEvent<boolean>) {
        showCityOffers = event.detail;
    }
```

Die bestehende Zeile

```svelte
            <FilterBar {categories} on:filter={handleFilter} />
```

ersetzen durch:

```svelte
            <FilterBar {categories} on:filter={handleFilter} on:toggleCityOffers={handleToggleCityOffers} />
```

Die bestehende Zeile

```svelte
                {#each markerGroups as group (group.key)}
                    {#if group.members.length === 1}
                        {@const marker = group.members[0]}
                        <CircleMarker
                            latLng={[marker.lat, marker.lng]}
                            options={{ radius: 10, bubblingMouseEvents: false }}
                            onclick={() => (selectedMarkerId = marker.id)}
                        >
                            <Tooltip options={{ direction: "top", offset: [0, -10] }}>
                                <div class="marker-tooltip">
                                    <div class="tooltip-header">
                                        <strong>{marker.name}</strong>
                                        {#if marker.category}
                                            <span
                                                class="tooltip-tag"
                                                style="background:{categoryColor(marker.category).bg}; color:{categoryColor(marker.category).text};"
                                            >{marker.category}</span>
                                        {/if}
                                    </div>
                                    <p class="tooltip-date">{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
                                    <p class="tooltip-rating">
                                        {marker.activityRating != null ? `★ ${marker.activityRating.toFixed(1)} (${marker.activityRatingCount})` : "Noch keine Bewertung"}
                                    </p>
                                </div>
                            </Tooltip>
                        </CircleMarker>
                    {:else}
```

ersetzen durch:

```svelte
                {#each markerGroups as group (group.key)}
                    {#if group.members.length === 1}
                        {@const marker = group.members[0]}
                        <CircleMarker
                            latLng={[marker.lat, marker.lng]}
                            options={marker.sourceUrl
                                ? { radius: 10, bubblingMouseEvents: false, color: "#F4C542", dashArray: "4, 4" }
                                : { radius: 10, bubblingMouseEvents: false }}
                            onclick={() => (selectedMarkerId = marker.id)}
                        >
                            <Tooltip options={{ direction: "top", offset: [0, -10] }}>
                                <div class="marker-tooltip">
                                    <div class="tooltip-header">
                                        <strong>{marker.name}</strong>
                                        {#if marker.category}
                                            <span
                                                class="tooltip-tag"
                                                style="background:{categoryColor(marker.category).bg}; color:{categoryColor(marker.category).text};"
                                            >{marker.category}</span>
                                        {/if}
                                    </div>
                                    {#if marker.dateTime}
                                        <p class="tooltip-date">{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
                                    {/if}
                                    <p class="tooltip-rating">
                                        {marker.activityRating != null ? `★ ${marker.activityRating.toFixed(1)} (${marker.activityRatingCount})` : "Noch keine Bewertung"}
                                    </p>
                                </div>
                            </Tooltip>
                        </CircleMarker>
                    {:else}
```

Die bestehende Zeile

```svelte
                    {markers.length} Aktivitäten {sheetExpanded ? "▼" : "▲"}
```

ersetzen durch:

```svelte
                    {visibleMarkers.length} Aktivitäten {sheetExpanded ? "▼" : "▲"}
```

Die bestehende Zeile

```svelte
                    <VolunteerList {markers} on:refresh={fetchMarkers} on:select={handleSelect} />
```

ersetzen durch:

```svelte
                    <VolunteerList markers={visibleMarkers} on:refresh={fetchMarkers} on:select={handleSelect} />
```

- [ ] **Step 6: `svelte-check` laufen lassen**

Run: `cd frontend && npx svelte-check --tsconfig ./tsconfig.json`
Expected: keine neuen Fehler (die 2 bereits bestehenden `esrap`-Fehler in `node_modules` sind vorbestehend und unrelated).

- [ ] **Step 7: Manuell verifizieren**

Backend und Frontend starten. Über die Browser-Konsole (`fetch`) zwei Test-Aktivitäten anlegen: eine normale über `/add` (mit Datum), eine "externe" direkt gegen die DB nicht möglich ohne Scraper-Lauf — stattdessen für den manuellen Zwischen-Check dieses Tasks reicht: Filter-Popover öffnen, Checkbox "Städtische Angebote (Köln) anzeigen" ist vorhanden und togglebar, `activeCount`-Badge erhöht sich beim Aktivieren, "Filter zurücksetzen" setzt sie zurück. Volle Sichtprüfung mit echten externen Punkten folgt in Task 4.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/lib/FilterBar.svelte frontend/src/lib/Map.svelte frontend/src/lib/groupByLocation.ts frontend/src/lib/ClusterMarker.svelte
git commit -m "feat: add city-offers toggle with client-side marker filtering"
```

---

### Task 3: Frontend — Detailansichten (PinDetailPanel, VolunteerList, EditActivityModal)

**Files:**
- Modify: `frontend/src/lib/PinDetailPanel.svelte`
- Modify: `frontend/src/lib/VolunteerList.svelte`
- Modify: `frontend/src/lib/EditActivityModal.svelte`

**Interfaces:**
- Consumes: `marker.sourceUrl: string | null` (aus Task 1, kommt automatisch über die `any`-typisierten Marker-Objekte durch, kein Wiring nötig), `marker.dateTime: string | null`.
- Produces: nichts, das andere Tasks konsumieren.

- [ ] **Step 1: `PinDetailPanel.svelte` — `dateTime`/`sourceUrl` in den Prop-Typ, Datumszeile bedingt, Quelle-Link**

Die bestehende Prop-Deklaration

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
        providerPhotoUrl: string | null;
        providerWebsiteUrl: string | null;
        providerRating: number | null;
        providerRatingCount: number;
    };
```

ersetzen durch:

```typescript
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

Die bestehenden Zeilen

```svelte
    <h3>{marker.name}</h3>
    <p class="meta">{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
    <p class="meta">{marker.address}</p>

    {#if marker.description}
        <p class="description">{marker.description}</p>
    {/if}
```

ersetzen durch:

```svelte
    <h3>{marker.name}</h3>
    {#if marker.dateTime}
        <p class="meta">{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
    {/if}
    <p class="meta">{marker.address}</p>

    {#if marker.description}
        <p class="description">{marker.description}</p>
    {/if}

    {#if marker.sourceUrl}
        <a class="source-link" href={marker.sourceUrl} target="_blank" rel="noopener noreferrer">
            Mehr Infos auf der Webseite der Stadt Köln
        </a>
    {/if}
```

Im `<style>`-Bereich, direkt nach dem bestehenden `.description { ... }`-Block einfügen:

```css
    .source-link {
        font-size: 0.85rem;
        color: var(--color-primary);
        align-self: flex-start;
    }
```

- [ ] **Step 2: `VolunteerList.svelte` — `dateTime` nullable, Datumszeile bedingt**

Die bestehende Zeile

```typescript
        dateTime: string;
```

ersetzen durch:

```typescript
        dateTime: string | null;
```

Die bestehende Zeile

```svelte
            <p class="date">{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
```

ersetzen durch:

```svelte
            {#if marker.dateTime}
                <p class="date">{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
            {/if}
```

- [ ] **Step 3: `EditActivityModal.svelte` — `dateTime`-Typ erweitern**

Die bestehende Zeile

```typescript
        dateTime: string;
```

ersetzen durch:

```typescript
        dateTime: string | null;
```

(Die bestehende Prefill-Logik `let dateTime = marker.dateTime ? marker.dateTime.slice(0, 16) : "";` ist bereits null-sicher und braucht keine Änderung — Bearbeiten ist für Städtische Angebote ohnehin nie erreichbar, da `isOwner` in `PinDetailPanel`/`VolunteerList` immer `false` ist, wenn `providerId == null`.)

- [ ] **Step 4: `svelte-check` laufen lassen**

Run: `cd frontend && npx svelte-check --tsconfig ./tsconfig.json`
Expected: keine neuen Fehler.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/PinDetailPanel.svelte frontend/src/lib/VolunteerList.svelte frontend/src/lib/EditActivityModal.svelte
git commit -m "feat: render city offers without a date and link back to the source"
```

---

### Task 4: End-to-End-Verifikation

**Files:** keine Code-Änderungen — reine Verifikation, ggf. kleine Nacharbeiten falls Abweichungen gefunden werden.

**Interfaces:**
- Consumes: das vollständige Feature aus Task 1–3.
- Produces: nichts (Verifikationsergebnis wird im Ledger festgehalten).

- [ ] **Step 1: Echten Scraper-Lauf gegen die Kölner Engagementdatenbank auslösen**

`backend/src/main/kotlin/com/example/VoloMap/VoloMapApp.kt` enthält bereits einen fertigen, auskommentierten Aufruf für genau diesen Zweck:

```kotlin
    /*
    scraper.scrapeWebsite(
        "https://engagementdatenbank.stadt-koeln.de/ergebnisse?fulltext=&id=&area_of_activity=All&target_group=All&postal_code=&page=1",
        "page", 20
    )*/
```

Temporär (nicht committen) auskommentieren und den `limit`-Parameter auf einen kleinen Wert setzen, um die externe Seite nicht unnötig zu belasten:

```kotlin
    val scraper = context.getBean(Scraper::class.java)
    scraper.scrapeWebsite(
        "https://engagementdatenbank.stadt-koeln.de/ergebnisse?fulltext=&id=&area_of_activity=All&target_group=All&postal_code=&page=1",
        null, 5
    )
```

Backend einmal starten (`cd backend && .\gradlew.bat bootRun`), Konsolenausgabe auf `Saved: ...` bzw. `Skipping (already exists): ...`-Zeilen prüfen — bestätigt, dass mindestens einige echte Einträge mit `dateTime = null` in `data/volomap` gespeichert wurden. Bei einem `NULL not allowed for column "DATE_TIME"`-Fehler: Task 1 Step 12 (DB zurücksetzen) nachholen, danach erneut versuchen. Nach erfolgreichem Lauf die temporäre Änderung in `VoloMapApp.kt` wieder rückgängig machen (zurück zum auskommentierten Zustand) — dieser Schritt ist nur einmalig für die Verifikation gedacht, kein dauerhaftes Verhalten.

- [ ] **Step 2: Toggle aus — Regressionscheck**

Karte laden, Toggle unberührt (Default aus). Nur App-eigene, datierte Aktivitäten sind auf Karte und in der Liste sichtbar — exakt wie vor diesem Feature.

- [ ] **Step 3: Toggle an — Städtische Angebote erscheinen**

Filter-Popover öffnen, "Städtische Angebote (Köln) anzeigen" aktivieren. Die in Step 1 importierten Einträge erscheinen als einzelne Punkte mit gestricheltem/andersfarbigem Rand auf der Karte und als Karten in der aufgeklappten Liste (Bottom-Sheet).

- [ ] **Step 4: Panel-Inhalt eines Städtischen Angebots**

Auf einen Städtischen-Angebot-Punkt klicken. Panel zeigt: keine Datumszeile, den Link "Mehr Infos auf der Webseite der Stadt Köln" (öffnet die echte Quelle in neuem Tab), kein "Bearbeiten"/"Löschen"-Button (auch nicht für einen eingeloggten Anbieter).

- [ ] **Step 5: Toggle aus — Städtische Angebote verschwinden wieder**

Toggle deaktivieren — die zuvor sichtbaren externen Punkte verschwinden sofort (kein Reload nötig, rein clientseitiger Filter) von Karte und Liste.

- [ ] **Step 6: Konsole/Netzwerk prüfen**

Keine neuen Fehler in der Browser-Konsole; kein zusätzlicher Netzwerk-Request beim Umschalten des Toggles (Bestätigung, dass rein clientseitig gefiltert wird, kein `/markers`-Refetch).

- [ ] **Step 7: Ledger-Eintrag**

Ergebnis der Verifikation im SDD-Ledger festhalten (Status, ggf. gefundene und behobene Abweichungen, insbesondere ob der `NOT NULL`-Risiko-Fall aus Task 1 Step 12 tatsächlich aufgetreten ist).
