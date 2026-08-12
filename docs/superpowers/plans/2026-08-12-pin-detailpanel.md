# Pin-Detailpanel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the unstyled Leaflet popup with a Google-Maps-style left side panel (desktop only, ≥1024px) that shows an activity's full detail — including the description and provider name, both previously unused/unshown — and links into the existing rating system.

**Architecture:** A new `PinDetailPanel.svelte` component renders inside `Map.svelte`'s layout as a fixed-width flex sibling to a new `.map-area` wrapper (which itself contains the existing floating search panel, the Leaflet map, and the bottom sheet — all now scoped to that wrapper instead of the full `.map-shell`, so they don't extend over the side panel). `Map.svelte` tracks the selected marker by **id**, not by object reference, and re-derives the current marker reactively from the live `markers` array — so if a rating changes (and `fetchMarkers()` re-runs) or a filter removes the marker, the panel reflects that automatically instead of showing stale data.

**Tech Stack:** Svelte 5 legacy style (`<script lang="ts">`, `createEventDispatcher`, plain reactive `let`/`$:`) — matches the rest of the project. `sveaflet` (Leaflet wrapper) — its `<Map>` component exposes a bindable `instance` prop giving direct access to the underlying `leaflet` `Map` object, and any prop named `on<eventname>` (e.g. `onclick`) on `<CircleMarker>` is forwarded as a native Leaflet event listener (confirmed by reading `sveaflet`'s `EventBridge` — it binds any prop matching `/^on.+/` whose value is a function, using the part after `on` as the Leaflet event name).

## Global Constraints

- Panel only renders at viewport width ≥ 1024px. Below that, clicking a pin or a list card has no detail-view effect (existing behavior otherwise unchanged) — explicit non-goal from the spec, not an oversight.
- Panel is fixed **360px** wide, positioned on the **left**.
- Panel opens identically from two triggers: clicking a `CircleMarker` pin on the map, or clicking a card in `VolunteerList` — both must produce the exact same panel for the exact same marker.
- Panel content includes the activity's `description` field and the provider's `providerName` — neither is shown anywhere in the app today.
- No changes to the `CircleMarker` pins themselves (color/shape) — explicit non-goal from the spec.
- Leaflet does not auto-detect container resize; `leafletMap.invalidateSize()` must be called after the panel opens/closes changes the map's width, or map tiles render incorrectly.

---

### Task 1: `PinDetailPanel.svelte`

**Context:** A self-contained detail panel, structurally similar to the existing `VolunteerList.svelte` card content but with more room (full description, provider name). Manages its own rating-modal state exactly like `VolunteerList.svelte` already does — this is a deliberate, small duplication of an established pattern, not a new abstraction.

**Files:**
- Create: `frontend/src/lib/PinDetailPanel.svelte`

**Interfaces:**
- Consumes: `RatingModal` (existing, unchanged) with its established `target`/`targetId`/`targetLabel` props and `close`/`rated` events.
- Produces: Props: `marker: { id: number; name: string; address: string; category: string; description: string; dateTime: string; activityRating: number | null; activityRatingCount: number; providerId: number | null; providerName: string | null; providerRating: number | null; providerRatingCount: number }`. Events: `close` (no payload), `refresh` (no payload, fired after a rating is saved). Task 2 (`Map.svelte`) renders this component with these exact prop/event names.

- [ ] **Step 1: `PinDetailPanel.svelte` anlegen**

```svelte
<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import RatingModal from "./RatingModal.svelte";

    export let marker: {
        id: number;
        name: string;
        address: string;
        category: string;
        description: string;
        dateTime: string;
        activityRating: number | null;
        activityRatingCount: number;
        providerId: number | null;
        providerName: string | null;
        providerRating: number | null;
        providerRatingCount: number;
    };

    const dispatch = createEventDispatcher<{ close: void; refresh: void }>();

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

    function openActivityRating() {
        openRating = { target: "activity", targetId: marker.id, targetLabel: marker.name };
    }

    function openProviderRating() {
        if (marker.providerId == null) return;
        openRating = { target: "provider", targetId: marker.providerId, targetLabel: marker.providerName ?? "Anbieter" };
    }

    function handleRated() {
        openRating = null;
        dispatch("refresh");
    }
</script>

<div class="panel">
    <div class="panel-header">
        {#if marker.category}
            <span
                class="tag"
                style="background:{categoryColor(marker.category).bg}; color:{categoryColor(marker.category).text};"
            >{marker.category}</span>
        {/if}
        <button class="close" on:click={() => dispatch("close")} aria-label="Schließen">×</button>
    </div>

    <h3>{marker.name}</h3>
    <p class="meta">{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
    <p class="meta">{marker.address}</p>

    {#if marker.description}
        <p class="description">{marker.description}</p>
    {/if}

    <button class="rating-badge" on:click={openActivityRating}>
        {marker.activityRating != null ? `★ ${marker.activityRating.toFixed(1)} (${marker.activityRatingCount})` : "Noch keine Bewertung"}
    </button>

    {#if marker.providerId != null}
        <div class="provider">
            <span class="provider-name">{marker.providerName}</span>
            <button class="rating-badge" on:click={openProviderRating}>
                {marker.providerRating != null ? `★ ${marker.providerRating.toFixed(1)} (${marker.providerRatingCount})` : "Noch keine Bewertung"}
            </button>
        </div>
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
    .panel {
        width: 360px;
        flex-shrink: 0;
        height: 100%;
        overflow-y: auto;
        background: var(--color-surface);
        border-right: 1px solid var(--color-border);
        box-shadow: var(--shadow-panel);
        padding: 20px;
        display: flex;
        flex-direction: column;
        gap: 10px;
    }

    .panel-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
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

    .tag {
        font-size: 0.75rem;
        padding: 2px 10px;
        border-radius: var(--radius-pill);
        white-space: nowrap;
    }

    h3 {
        font-size: 1.15rem;
    }

    .meta {
        margin: 0;
        font-size: 0.9rem;
        color: var(--color-text-muted);
    }

    .description {
        margin: 6px 0 0;
        font-size: 0.9rem;
        color: var(--color-text);
        line-height: 1.5;
    }

    .rating-badge {
        align-self: flex-start;
        font-size: 0.8rem;
        padding: 4px 10px;
        border-radius: var(--radius-pill);
        border: 1px solid var(--color-border);
        background: var(--color-bg);
        color: var(--color-text);
        cursor: pointer;
    }

    .rating-badge:hover {
        border-color: var(--color-primary);
    }

    .provider {
        display: flex;
        flex-direction: column;
        gap: 4px;
        margin-top: 4px;
        padding-top: 10px;
        border-top: 1px solid var(--color-border);
    }

    .provider-name {
        font-size: 0.85rem;
        font-weight: 600;
        color: var(--color-text);
    }
</style>
```

- [ ] **Step 2: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler (die Datei wird von keiner Komponente importiert, bis Task 2).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/PinDetailPanel.svelte
git commit -m "feat: add PinDetailPanel component for the pin detail view"
```

---

### Task 2: `Map.svelte` — Panel einbinden, Layout umbauen, Pin-Klick verdrahten

**Context:** Der größte Umbau. `.map-shell` wird zu einer Flex-Reihe mit dem
Panel links (nur ≥1024px, nur wenn eine Aktivität ausgewählt ist) und einem
neuen `.map-area`-Wrapper rechts, der die bisherigen absolut positionierten
Kinder (`search-panel`, `map-container`, `bottom-sheet`) enthält — sie
bleiben dadurch auf den Kartenbereich beschränkt und schieben sich nicht
unter das Panel. Die bisherige `Popup`-Nutzung entfällt komplett (ersetzt
durch Panel + Klick). Ausgewählt wird per `id`, nicht per Objekt-Referenz,
damit das Panel nach einem `fetchMarkers()` (z.B. durch eine neue Bewertung
oder einen Filterwechsel) automatisch aktuelle Daten zeigt statt eines
veralteten Objekts.

**Files:**
- Modify: `frontend/src/lib/Map.svelte`

**Interfaces:**
- Consumes: `PinDetailPanel` (Task 1) mit `marker`-Prop und `close`/`refresh`-Events; `VolunteerList`s neuem `select`-Event (Task 3, Payload: `{ id: number }`).
- Produces: keine neuen Exports — reine interne Umstrukturierung.

- [ ] **Step 1: `Map.svelte` komplett ersetzen**

```svelte
<script lang="ts">
    import {onMount, tick} from "svelte";
    import type {Map as LeafletMap} from "leaflet";
    import {Map, TileLayer} from "sveaflet";
    import FilterBar from "./FilterBar.svelte";
    import SearchBar from "./SearchBar.svelte";
    import VolunteerList from "./VolunteerList.svelte";
    import PinDetailPanel from "./PinDetailPanel.svelte";
    import { CircleMarker } from "sveaflet";

    let markers: any[] = [];
    let categories: string[] = [];
    let errorMessage: string | null = null;
    let sheetExpanded = false;

    let selectedMarkerId: number | null = null;
    $: selectedMarker = markers.find((m) => m.id === selectedMarkerId) ?? null;

    let isDesktop = typeof window !== "undefined" ? window.innerWidth >= 1024 : false;
    function handleResize() {
        isDesktop = window.innerWidth >= 1024;
    }

    $: panelOpen = selectedMarker != null && isDesktop;

    let leafletMapInstance: LeafletMap | undefined;
    $: if (leafletMapInstance) {
        panelOpen;
        tick().then(() => leafletMapInstance?.invalidateSize());
    }

    let query = {
        date: "",
        category: "",
        timeFrom: "",
        timeTo: "",
        search: "",
    };

    onMount(() => {
        window.addEventListener("resize", handleResize);
        return () => window.removeEventListener("resize", handleResize);
    });

    onMount(async () => {
        try {
            const res = await fetch("http://localhost:8080/categories");
            categories = await res.json();
        } catch (e) {
            errorMessage = "Kategorien konnten nicht geladen werden. Ist der Server erreichbar?";
        }
        fetchMarkers();
    });

    async function fetchMarkers() {
        const params = new URLSearchParams();

        if (query.date) params.append("date", query.date);
        if (query.category) params.append("category", query.category);
        if (query.timeFrom) params.append("timeFrom", query.timeFrom);
        if (query.timeTo) params.append("timeTo", query.timeTo);
        if (query.search) params.append("search", query.search);

        try {
            const res = await fetch(
                "http://localhost:8080/markers?" + params.toString()
            );
            if (!res.ok) throw new Error("Request failed");
            markers = await res.json();
            errorMessage = null;
        } catch (e) {
            errorMessage = "Aktivitäten konnten nicht geladen werden. Ist der Server erreichbar?";
        }
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

    function handleSelect(event: CustomEvent<{ id: number }>) {
        selectedMarkerId = event.detail.id;
    }

    const attribution = '&copy; <a href="https://carto.com/">CARTO</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
</script>

<div class="map-shell">
    {#if panelOpen && selectedMarker}
        <PinDetailPanel
            marker={selectedMarker}
            on:close={() => (selectedMarkerId = null)}
            on:refresh={fetchMarkers}
        />
    {/if}

    <div class="map-area">
        <div class="search-panel">
            <SearchBar on:search={handleSearch} />
            <FilterBar {categories} on:filter={handleFilter} />
            {#if errorMessage}<p class="error">{errorMessage}</p>{/if}
        </div>

        <div class="map-container">
            <Map bind:instance={leafletMapInstance} options={{ center: [50.9375, 6.9603], zoom: 13, zoomControl: false, attributionControl: false }}>
                <TileLayer
                    url={'https://cartodb-basemaps-a.global.ssl.fastly.net/light_nolabels/{z}/{x}/{y}.png'}
                    options={{ attribution }}
                />

                {#each markers as marker}
                    <CircleMarker latLng={[marker.lat, marker.lng]} onclick={() => (selectedMarkerId = marker.id)} />
                {/each}
            </Map>
        </div>

        <div class="bottom-sheet" class:expanded={sheetExpanded}>
            <div class="sheet-header">
                <button class="sheet-handle" on:click={() => sheetExpanded = !sheetExpanded}>
                    {markers.length} Aktivitäten {sheetExpanded ? "▼" : "▲"}
                </button>
                <span class="attribution">© CARTO © OpenStreetMap</span>
            </div>
            {#if sheetExpanded}
                <div class="sheet-content">
                    <VolunteerList {markers} on:refresh={fetchMarkers} on:select={handleSelect} />
                </div>
            {/if}
        </div>
    </div>
</div>

<style>
    .map-shell {
        height: 100%;
        width: 100%;
        display: flex;
    }

    .map-area {
        position: relative;
        flex: 1;
        min-width: 0;
        height: 100%;
    }

    .map-container {
        position: absolute;
        inset: 0;
    }

    .map-container :global(.leaflet-container) {
        height: 100%;
        width: 100%;
    }

    .search-panel {
        position: absolute;
        top: 12px;
        left: 12px;
        right: 12px;
        z-index: 1001;
        background: var(--color-surface);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-panel);
        padding: 10px 12px;
        display: flex;
        flex-direction: column;
        gap: 8px;
    }

    .error {
        color: var(--color-error);
        font-size: 0.8rem;
        margin: 0;
    }

    .bottom-sheet {
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        z-index: 1001;
        background: var(--color-surface);
        border-radius: var(--radius-lg) var(--radius-lg) 0 0;
        box-shadow: 0 -3px 10px rgba(47, 82, 51, 0.15);
        max-height: 60%;
        display: flex;
        flex-direction: column;
    }

    .sheet-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        padding: 0 12px;
    }

    .sheet-handle {
        flex: 1;
        background: none;
        border: none;
        border-radius: var(--radius-lg) var(--radius-lg) 0 0;
        padding: 12px 0;
        font-weight: 600;
        color: var(--color-primary);
        cursor: pointer;
        text-align: left;
    }

    .attribution {
        font-size: 0.65rem;
        color: var(--color-text-muted);
        white-space: nowrap;
    }

    .sheet-content {
        overflow-y: auto;
        padding: 0 12px 12px;
    }
</style>
```

Key differences from the current file: two separate `onMount` calls (the
new one is synchronous specifically so its returned cleanup function is
honored by Svelte — an `async` `onMount` callback's return value is *not*
treated as a cleanup function, which is why the existing categories/markers
fetch stays in its own untouched `onMount`); `Popup`/`Marker` no longer
imported from `sveaflet` for popup rendering (the `Popup` import is
dropped, `CircleMarker` now takes an `onclick` prop instead of a `<Popup>`
child); `selectedMarkerId`/`selectedMarker`/`isDesktop`/`panelOpen` are new
reactive state; `leafletMapInstance` is bound via `bind:instance` on the
sveaflet `<Map>` and used only to call `invalidateSize()` reactively when
`panelOpen` changes; the former direct children of `.map-shell`
(`search-panel`, `map-container`, `bottom-sheet`) move one level deeper
into a new `.map-area` wrapper, which is `position: relative` and `flex: 1`
so it fills whatever width remains next to the panel.

- [ ] **Step 2: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler in `Map.svelte`.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/Map.svelte
git commit -m "feat: wire PinDetailPanel into Map, restructure layout around a map-area wrapper"
```

---

### Task 3: `VolunteerList.svelte` — Karten-Klick öffnet dasselbe Panel

**Context:** Klick auf eine Karte (außerhalb der Bewertungs-Badges) feuert
ein neues `select`-Event mit dem `marker`-Objekt. Die beiden bestehenden
Bewertungs-Badge-Buttons bekommen `stopPropagation`, damit ein Klick auf
sie nicht *zusätzlich* die Karte "auswählt" (sie öffnen ohnehin schon
direkt das `RatingModal` — das wäre sonst ein verwirrender Doppel-Effekt).

**Files:**
- Modify: `frontend/src/lib/VolunteerList.svelte`

**Interfaces:**
- Consumes: keine neuen.
- Produces: neues `select`-Event, Payload `{ id: number }`. Bewusst nur die
  id, nicht das ganze `marker`-Objekt — `Map.svelte` (Task 2) leitet die
  aktuelle Aktivität ohnehin per `.find()` aus seiner eigenen `markers`-
  Liste ab (siehe Task 2s "Architektur"-Absatz), ein schlankeres Payload
  vermeidet jede Typ-Unschärfe zwischen `VolunteerList`s lokalem
  `markers`-Typ (ohne `description`/`providerName`) und dem vollen
  API-Shape, das `PinDetailPanel` erwartet.

- [ ] **Step 1: `<script>`-Block — `select`-Event zum Dispatcher-Typ hinzufügen**

In `frontend/src/lib/VolunteerList.svelte`, Zeile 20 (`const dispatch = createEventDispatcher<{ refresh: void }>();`) ersetzen durch:

```typescript
    const dispatch = createEventDispatcher<{ refresh: void; select: { id: number } }>();
```

- [ ] **Step 2: Karten-Div klickbar machen, Badge-Klicks stoppen die Ausbreitung**

Den bestehenden Card-Block (aktuell Zeilen 58-80) ersetzen durch:

```svelte
        <div class="card" on:click={() => dispatch("select", { id: marker.id })}>
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
                <button class="rating-badge" on:click|stopPropagation={() => openActivityRating(marker)}>
                    {marker.activityRating != null ? `★ ${marker.activityRating.toFixed(1)} (${marker.activityRatingCount})` : "Noch keine Bewertung"}
                </button>
                {#if marker.providerId != null}
                    <button class="rating-badge" on:click|stopPropagation={() => openProviderRating(marker)}>
                        Anbieter: {marker.providerRating != null ? `★ ${marker.providerRating.toFixed(1)} (${marker.providerRatingCount})` : "Noch keine Bewertung"}
                    </button>
                {/if}
            </div>
        </div>
```

- [ ] **Step 3: `.card` optisch als klickbar kennzeichnen**

Im `<style>`-Block, der `.card`-Regel (aktuell Zeilen 103-108) `cursor: pointer;` hinzufügen:

```css
    .card {
        background: var(--color-bg);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        padding: 10px 12px;
        cursor: pointer;
    }
```

- [ ] **Step 4: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler in `VolunteerList.svelte`.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/VolunteerList.svelte
git commit -m "feat: open PinDetailPanel from VolunteerList card clicks"
```

---

### Task 4: End-to-End-Verifikation

**Context:** Kompletter manueller Rundgang mit laufendem Backend und
Frontend, Fokus auf die beiden Trigger-Wege, das Resize-Verhalten der
Karte und die Breakpoint-Grenze.

**Files:** keine (nur Verifikation)

- [ ] **Step 1: Volle svelte-check-Prüfung**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler/Warnungen gegenüber dem Stand vor diesem Plan
(bekannte Altlasten wie der `FilterBar.svelte`-Fehler und die
`Router.svelte`-Warnung zählen nicht als neu).

- [ ] **Step 2: Backend-Tests unverändert grün**

Run: `cd backend && ./gradlew.bat test`
Expected: `BUILD SUCCESSFUL` (dieser Plan ändert kein Backend-Verhalten).

- [ ] **Step 3: Visueller Rundgang im Browser**

Mit laufendem Backend (`./gradlew.bat bootRun`) und Frontend
(`npm run dev`), Browserfenster **mindestens 1024px breit**:

- Klick auf einen Pin öffnet das Panel links, Karte schiebt sich nach
  rechts, keine verzerrten/abgeschnittenen Kartenkacheln nach dem Öffnen
  (Resize-Fix greift)
- Panel zeigt Kategorie, Titel, Datum, Adresse, Beschreibung (falls
  vorhanden), Aktivitäts-Sterne, und — falls die Aktivität einen Anbieter
  hat — Anbieter-Name und Anbieter-Sterne
- Aktivität ohne Anbieter (gescrapte/geseedete Daten): Anbieter-Abschnitt
  fehlt komplett, kein leerer Platzhalter
- Klick auf X schließt das Panel, Karte füllt wieder die volle Breite,
  keine verzerrten Kacheln
- Bottom-Sheet öffnen, Klick auf eine Karte (nicht auf ein Bewertungs-
  Badge) öffnet dasselbe Panel für dieselbe Aktivität
- Klick auf ein Bewertungs-Badge in der Liste öffnet weiterhin nur das
  `RatingModal`, nicht zusätzlich das Panel
  Innerhalb des Panels eine Bewertung abgeben: Panel bleibt offen und
  zeigt danach den aktualisierten Sterne-Durchschnitt für dieselbe
  Aktivität (kein Zurücksetzen auf veraltete Daten)
- Klick auf einen anderen Pin bei offenem Panel wechselt direkt zur neuen
  Aktivität, kein Zwischenschritt nötig
- Browserfenster auf unter 1024px verkleinern: Klick auf einen Pin öffnet
  kein Panel mehr (aktuelles Verhalten ohne Detailansicht bleibt
  bestehen)

- [ ] **Step 4: Report**

Zusammenfassung Pass/Fail für Schritt 1-3. Wenn alles passt: Pin-Detailpanel ist fertig.
