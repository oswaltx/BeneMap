# UI-Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rein visuelles Redesign von VoloMap (Benemap) auf Basis der Palette "Sonnenblume & Wald" und eines Vollbild-Karten-Layouts mit schwebenden Panels — ohne Funktionalität zu verändern.

**Architektur:** Zentrale CSS-Custom-Properties in `app.css` ersetzen das unveränderte Vite-Starter-CSS. Alle Svelte-Komponenten werden auf diese Tokens umgestellt (nur `<style>`-Blöcke, Markup/Script bleibt bis auf `Map.svelte`, `Home.svelte`, `App.svelte` und `VolunteerList.svelte` unverändert). `Map.svelte` bekommt ein neues Layout: Karte füllt die volle Höhe, Suche/Filter schweben als Panel oben, die Aktivitätenliste wird ein aus-/einklappbares Bottom-Sheet.

**Tech Stack:** Svelte 5 (legacy `<script lang="ts">` + `createEventDispatcher`-Stil, wie im restlichen Projekt), Vite, CSS Custom Properties (kein CSS-Framework).

## Global Constraints

- Keine neuen Features, keine Backend-Änderungen, keine Datenmodell-Änderungen (siehe Spec, Nicht-Ziele).
- Kein Dark-Mode-Support — feste helle Palette, `prefers-color-scheme`-Sonderfälle werden entfernt.
- Kein Frontend-Test-Framework wird eingeführt — Verifikation ist manuell/visuell (svelte-check + Browser).
- Bestehende Komponenten-Konventionen beibehalten: `<script lang="ts">` mit `createEventDispatcher` und einfachen reaktiven `let`-Bindings (kein Runes-Stil, außer in bereits bestehenden Dateien wie `Link.svelte`, die nicht angefasst werden).
- Farb-Tokens (aus der Spec, exakt diese Werte verwenden):
  `--color-accent: #F4C542`, `--color-accent-text: #4A3B00`,
  `--color-primary: #2F5233`, `--color-primary-text: #FFFBF2`,
  `--color-bg: #FFFBF2`, `--color-surface: #FFFFFF`,
  `--color-text: #2A2A22`, `--color-text-muted: #6B6B5A`,
  `--color-border: #E5DCC3`, `--color-error: #B23A3A`,
  `--radius-md: 10px`, `--radius-lg: 16px`, `--radius-pill: 999px`,
  `--shadow-panel: 0 3px 10px rgba(47, 82, 51, 0.15)`.
- `About.svelte` bleibt inhaltlich leer (Nicht-Ziel laut Spec) — nur konsistenter Rahmen.

---

### Task 1: Design-Tokens + Layout-Grundgerüst (`app.css`)

**Context:** Fundament für alle folgenden Tasks. Ersetzt das Vite-Starter-CSS
komplett: neue Farb-/Radius-/Schatten-Tokens, entfernt die zentrierte
`max-width:1280px`-Box und den `prefers-color-scheme`-Dark-Mode, und baut
eine `height:100%`-Flex-Spalten-Kette (`html`/`body`/`#app`) auf, die
`Map.svelte` in Task 5 braucht, um die Karte auf volle Resthöhe zu bringen.

**Files:**
- Modify: `frontend/src/app.css`

**Interfaces:**
- Produces: die CSS-Variablen aus den Global Constraints, verfügbar in
  jeder Komponente über `var(--color-accent)` etc. Ein `#app`-Flex-Column-
  Container, in dem `NavBar` und die vom `Router` gerenderte Seite als
  Flex-Items nebeneinander stehen (Task 2/5 nutzen das).

- [ ] **Step 1: `app.css` komplett ersetzen**

```css
:root {
  --color-accent: #F4C542;
  --color-accent-text: #4A3B00;
  --color-primary: #2F5233;
  --color-primary-text: #FFFBF2;
  --color-bg: #FFFBF2;
  --color-surface: #FFFFFF;
  --color-text: #2A2A22;
  --color-text-muted: #6B6B5A;
  --color-border: #E5DCC3;
  --color-error: #B23A3A;
  --radius-md: 10px;
  --radius-lg: 16px;
  --radius-pill: 999px;
  --shadow-panel: 0 3px 10px rgba(47, 82, 51, 0.15);

  font-family: system-ui, Avenir, Helvetica, Arial, sans-serif;
  line-height: 1.5;
  font-weight: 400;

  color-scheme: light;
  color: var(--color-text);
  background-color: var(--color-bg);

  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

*,
*::before,
*::after {
  box-sizing: border-box;
}

html,
body {
  height: 100%;
  margin: 0;
}

body {
  min-width: 320px;
}

#app {
  height: 100%;
  display: flex;
  flex-direction: column;
}

a {
  font-weight: 500;
  color: var(--color-primary);
  text-decoration: inherit;
}
a:hover {
  color: var(--color-accent-text);
}

h1,
h2,
h3 {
  margin: 0;
  color: var(--color-text);
}

button {
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  padding: 0.6em 1.2em;
  font-size: 1em;
  font-weight: 500;
  font-family: inherit;
  background-color: var(--color-accent);
  color: var(--color-accent-text);
  cursor: pointer;
  transition: filter 0.15s;
}
button:hover {
  filter: brightness(0.95);
}
button:focus,
button:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}
```

This fully replaces the file's previous content (the old Vite starter CSS,
including the `.card`, `#app { max-width: 1280px; ... }` block, and the
`@media (prefers-color-scheme: light)` block — none of that carries over).

- [ ] **Step 2: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: no new errors compared to before this change (CSS-only change,
should be a no-op for svelte-check's TS/template checking).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app.css
git commit -m "style: replace Vite starter CSS with warm color-token design system"
```

---

### Task 2: NavBar restyle + App.svelte aufräumen

**Context:** `NavBar.svelte` hat aktuell kein Styling; `App.svelte` hat ein
separates `<h1>Benemap</h1>` über der Nav, das im neuen Vollbild-Layout
unnötig vertikalen Platz wegnimmt. Der Markenname wandert in die NavBar
selbst (wie im Mockup), das separate `<h1>` entfällt.

**Files:**
- Modify: `frontend/src/lib/NavBar.svelte`
- Modify: `frontend/src/App.svelte`

**Interfaces:**
- Consumes: `Link.svelte`'s bestehende `activeClass`-Prop (bereits
  vorhanden, keine Änderung an `Link.svelte` nötig), Tokens aus Task 1.

- [ ] **Step 1: `NavBar.svelte` ersetzen**

```svelte
<script lang="ts">
    import Link from "./Link.svelte";
</script>

<nav>
    <span class="brand">Benemap</span>
    <div class="links">
        <Link href="/" activeClass="active">Home</Link>
        <Link href="/add" activeClass="active">Aktivität hinzufügen</Link>
        <Link href="/about" activeClass="active">About</Link>
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
</style>
```

- [ ] **Step 2: `App.svelte` — `<h1>` entfernen**

```svelte
<script>
    import NavBar from "./lib/NavBar.svelte";
    import Router from "./lib/Router.svelte";
</script>
<NavBar />
<Router />
```

- [ ] **Step 3: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: no new errors in `NavBar.svelte` or `App.svelte`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/NavBar.svelte frontend/src/App.svelte
git commit -m "style: restyle NavBar as header bar, move Benemap branding into it"
```

---

### Task 3: FilterBar + SearchBar Restyle

**Context:** Beide Komponenten behalten Markup und Verhalten exakt bei —
nur die `<style>`-Blöcke werden auf Tokens umgestellt, damit sie farblich
zum neuen schwebenden Panel passen, das Task 5 um sie herum baut.

**Files:**
- Modify: `frontend/src/lib/FilterBar.svelte`
- Modify: `frontend/src/lib/SearchBar.svelte`

**Interfaces:**
- Consumes: Tokens aus Task 1.
- Produces: keine Änderung an Events/Props — `FilterBar`s `filter`-Event
  und `SearchBar`s `search`-Event bleiben exakt wie zuvor, Task 5 verlässt
  sich darauf.

- [ ] **Step 1: `FilterBar.svelte`s `<style>`-Block ersetzen (Script/Markup unverändert lassen)**

```css
<style>
    div {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
    }

    select,
    button {
        font-family: inherit;
        font-size: 0.85rem;
        padding: 5px 10px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-surface);
        color: var(--color-text);
        cursor: pointer;
        transition: border-color 0.15s, background 0.15s;
    }

    select:hover,
    button:hover {
        border-color: var(--color-primary);
    }

    select:focus,
    button:focus {
        outline: none;
        border-color: var(--color-primary);
    }

    .active {
        font-weight: bold;
        border-color: var(--color-primary);
        background: var(--color-accent);
        color: var(--color-accent-text);
        outline: none;
    }
</style>
```

(Removed the old `margin-bottom: 6px` on the outer `div` — spacing between
`SearchBar` and `FilterBar` inside the new panel is handled by the panel's
own `gap`, added in Task 5.)

- [ ] **Step 2: `SearchBar.svelte` — `<style>`-Block hinzufügen**

The file currently has no `<style>` block at all. Add one after the closing
`</script>` tag, before the `<input .../>` markup (markup itself stays
exactly as it is — only adding styling):

```css
<style>
    input {
        width: 100%;
        font-family: inherit;
        font-size: 0.9rem;
        padding: 8px 12px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        color: var(--color-text);
    }
    input:focus {
        outline: none;
        border-color: var(--color-primary);
    }
</style>
```

- [ ] **Step 3: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: no new errors in either file.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/FilterBar.svelte frontend/src/lib/SearchBar.svelte
git commit -m "style: restyle FilterBar and SearchBar with design tokens"
```

---

### Task 4: VolunteerList Restyle (Karten + Kategorie-Farben)

**Context:** Aktuell reine Textliste. Wird zu Karten mit einem
farbcodierten Kategorie-Tag umgebaut (Farbe wird per einfachem String-Hash
auf eine kleine feste Palette abgebildet — rein visuell, kein neues
Datenfeld). Wird in Task 5 in das Bottom-Sheet eingebettet.

**Files:**
- Modify: `frontend/src/lib/VolunteerList.svelte`

**Interfaces:**
- Consumes: `markers` prop, unverändertes Shape (`id, name, address,
  category, dateTime, lat, lng`).
- Produces: keine neue Prop/kein neues Event — reines Rendering.

- [ ] **Step 1: `VolunteerList.svelte` komplett ersetzen**

```svelte
<script lang="ts">
    export let markers: {
        id: number;
        name: string;
        address: string;
        category: string;
        dateTime: string;
        lat: number;
        lng: number;
    }[] = [];

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
        </div>
    {/each}
    {#if markers.length === 0}
        <p class="empty">Keine Aktivitäten gefunden.</p>
    {/if}
</div>

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
    .empty {
        color: var(--color-text-muted);
        font-size: 0.85rem;
        text-align: center;
        padding: 12px 0;
    }
</style>
```

- [ ] **Step 2: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: no new errors in `VolunteerList.svelte`.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/VolunteerList.svelte
git commit -m "style: restyle VolunteerList as cards with category color tags"
```

---

### Task 5: Map.svelte Layout-Umbau + Button.svelte entfernen

**Context:** Die größte strukturelle Änderung. Aktuell: `SearchBar`,
`FilterBar`, eine `height:500px`-Karten-Box und `VolunteerList` sind
vertikal gestapelt. Neu: Karte füllt die volle Resthöhe (dank Task 1s
`#app`-Flex-Kette + `Home.svelte`s Wrapper), `SearchBar`+`FilterBar` bilden
ein schwebendes Panel oben auf der Karte, `VolunteerList` sitzt in einem
aus-/einklappbaren Bottom-Sheet. Die komplette Daten-/Fetch-Logik
(`query`-State, `fetchMarkers`, `handleSearch`, `handleFilter`,
`errorMessage`) bleibt exakt wie sie ist — nur Markup und `<style>` ändern
sich. `Button.svelte` wird entfernt, da es nirgends im Projekt tatsächlich
verwendet wird (nur ein toter Import in `Map.svelte`, den dieser Umbau
sowieso beseitigt).

**Files:**
- Modify: `frontend/src/lib/Map.svelte`
- Modify: `frontend/src/pages/Home.svelte`
- Delete: `frontend/src/lib/Button.svelte`

**Interfaces:**
- Consumes: `SearchBar` (`search`-Event), `FilterBar` (`filter`-Event,
  `categories`-Prop), `VolunteerList` (`markers`-Prop) — alle aus Task 3/4,
  unverändertes Interface.
- Produces: `Home.svelte` rendert `<Map />` in einem Wrapper, der ihr
  `flex: 1` gibt, damit sie die Resthöhe unter der `NavBar` ausfüllt.

- [ ] **Step 1: Vor dem Löschen bestätigen, dass `Button.svelte` wirklich ungenutzt ist**

Run: `grep -rn "Button" frontend/src --include=*.svelte --include=*.ts`
Expected: nur der (bald entfernte) Import in `Map.svelte` und die
Definition in `Button.svelte` selbst — keine `<Button` Verwendung im
Markup irgendeiner Datei. Falls doch eine echte Verwendung auftaucht,
NICHT löschen — stattdessen stoppen und den Fund melden (BLOCKED), das ist
ein Abweichen von dieser Brief-Annahme.

- [ ] **Step 2: `Button.svelte` löschen**

```bash
rm frontend/src/lib/Button.svelte
```

- [ ] **Step 3: `Map.svelte` komplett ersetzen**

```svelte
<script lang="ts">
    import {onMount} from "svelte";
    import { ControlZoom } from 'sveaflet';
    import {Map, Marker, Popup, TileLayer} from "sveaflet";
    import FilterBar from "./FilterBar.svelte";
    import SearchBar from "./SearchBar.svelte";
    import VolunteerList from "./VolunteerList.svelte";
    import { CircleMarker } from "sveaflet";

    let markers: any[] = [];
    let categories: string[] = [];
    let errorMessage: string | null = null;
    let sheetExpanded = false;

    let query = {
        date: "",
        category: "",
        timeFrom: "",
        timeTo: "",
        search: "",
    };

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
    const attribution = '&copy; <a href="https://carto.com/">CARTO</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
</script>

<div class="map-shell">
    <div class="search-panel">
        <SearchBar on:search={handleSearch} />
        <FilterBar {categories} on:filter={handleFilter} />
        {#if errorMessage}<p class="error">{errorMessage}</p>{/if}
    </div>

    <div class="map-container">
        <Map options={{ center: [50.9375, 6.9603], zoom: 13 }}>
            <TileLayer
                url={'https://cartodb-basemaps-a.global.ssl.fastly.net/light_nolabels/{z}/{x}/{y}.png'}
                options={{ attribution }}
            />

            {#each markers as marker}
                <CircleMarker latLng={[marker.lat, marker.lng]}>
                    <Popup>
                        <h3>{marker.name}</h3>
                        <p>{marker.address}</p>
                        <p>{marker.category}</p>
                        <p>{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
                    </Popup>
                </CircleMarker>
            {/each}
        </Map>
    </div>

    <div class="bottom-sheet" class:expanded={sheetExpanded}>
        <button class="sheet-handle" on:click={() => sheetExpanded = !sheetExpanded}>
            {markers.length} Aktivitäten {sheetExpanded ? "▼" : "▲"}
        </button>
        {#if sheetExpanded}
            <div class="sheet-content">
                <VolunteerList {markers} />
            </div>
        {/if}
    </div>
</div>

<style>
    .map-shell {
        position: relative;
        height: 100%;
        width: 100%;
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
        z-index: 1000;
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
        z-index: 1000;
        background: var(--color-surface);
        border-radius: var(--radius-lg) var(--radius-lg) 0 0;
        box-shadow: 0 -3px 10px rgba(47, 82, 51, 0.15);
        max-height: 60%;
        display: flex;
        flex-direction: column;
    }

    .sheet-handle {
        width: 100%;
        background: none;
        border: none;
        border-radius: var(--radius-lg) var(--radius-lg) 0 0;
        padding: 12px;
        font-weight: 600;
        color: var(--color-primary);
        cursor: pointer;
    }

    .sheet-content {
        overflow-y: auto;
        padding: 0 12px 12px;
    }
</style>
```

Key differences from the current file: `Button` import removed (dead), the
three previously-stacked blocks (`SearchBar`+`FilterBar`, error message,
map box, `VolunteerList`) are now positioned as layers inside one
`.map-shell` — floating panel on top, full-bleed map behind it, collapsible
bottom sheet at the bottom. The `:global(.leaflet-container)` rule makes
sure the Leaflet map (previously sized by an explicit `height:500px` on its
wrapper) fills the new `.map-container`, which itself is sized by
`.map-shell`'s `height:100%` — which in turn depends on `Home.svelte`'s
wrapper (next step) giving it real height to fill.

- [ ] **Step 4: `Home.svelte` — Wrapper mit `flex: 1` hinzufügen**

```svelte
<script lang="ts">
    import Map from "../lib/Map.svelte";
</script>

<div class="home-page">
    <Map />
</div>

<style>
    .home-page {
        flex: 1;
        min-height: 0;
    }
</style>
```

(`min-height: 0` is needed so this flex item can actually shrink to fit the
available space instead of growing to its content's natural size — a
common flexbox gotcha; without it the map wouldn't visibly fill the
viewport correctly.)

- [ ] **Step 5: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: no new errors in `Map.svelte` or `Home.svelte`.

Then start both servers and check visually:

```bash
cd backend && ./gradlew.bat bootRun
```
```bash
cd frontend && npm run dev
```

Open the app in a browser. Confirm: the map fills the screen below the nav
bar (not a small fixed box), the search/filter panel floats on top of the
map near the top, clicking the bottom-sheet handle (e.g. "34 Aktivitäten
▲") expands/collapses the activity list, and searching/filtering still
updates the map markers exactly as before (functionality unchanged — only
verify it still works, this isn't new logic).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/Map.svelte frontend/src/pages/Home.svelte
git rm frontend/src/lib/Button.svelte
git commit -m "style: rebuild Map layout as fullscreen map with floating panel and bottom sheet"
```

---

### Task 6: AddActivity + About Seiten-Container Restyle

**Context:** Beide sind eigenständige, normal scrollende Seiten (kein
Vollbild-Layout wie die Karte). `AddActivity.svelte` behält Script und
Formular-Markup exakt bei — nur `<style>` ändert sich, plus ein
umschließender `.page`-Container für Zentrierung/Padding und einen
Card-Look für das Formular selbst. `About.svelte` ist weiterhin inhaltlich
leer (Nicht-Ziel laut Spec), bekommt aber denselben `.page`-Rahmen, damit
sie nicht unstyled/abgeschnitten wirkt.

**Files:**
- Modify: `frontend/src/lib/AddActivity.svelte`
- Modify: `frontend/src/pages/About.svelte`

**Interfaces:**
- Consumes: Tokens aus Task 1. Keine Änderung an `AddActivity`s
  Submit-Logik oder Feldern.

- [ ] **Step 1: `AddActivity.svelte` — Markup mit `.page`-Wrapper umschließen, `<style>` ersetzen**

Script-Block (alles von `let name = "";` bis zum Ende von `handleSubmit`)
bleibt **exakt unverändert**. Nur das Markup unterhalb von `</script>` und
der `<style>`-Block werden ersetzt:

```svelte
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
</style>
```

- [ ] **Step 2: `About.svelte` — konsistenter leerer Rahmen**

```svelte
<div class="page"></div>

<style>
    .page {
        flex: 1;
        padding: 24px 16px;
    }
</style>
```

- [ ] **Step 3: Verify**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: no new errors in either file.

Manually: navigate to `/add`, confirm the form still submits successfully
(functionality unchanged, only styling) and looks like a centered card;
navigate to `/about`, confirm it renders the shared background/padding
without looking broken.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/AddActivity.svelte frontend/src/pages/About.svelte
git commit -m "style: restyle AddActivity form as a card, give About a consistent empty frame"
```

---

### Task 7: Manuelle End-to-End-Sichtprüfung

**Context:** Abschließender visueller Rundgang durch die komplette
umgestylte App, um sicherzustellen, dass alle Tasks als Ganzes
zusammenpassen (keine Task-Review kann das isoliert sehen).

**Files:** keine (nur Verifikation)

- [ ] **Step 1: Volle svelte-check-Prüfung**

Run (from `frontend/`): `npx svelte-check --tsconfig ./tsconfig.app.json`
Expected: keine neuen Fehler/Warnungen gegenüber dem Stand vor diesem Plan
(bekannte Altlasten wie eine bestehende Warnung in `Router.svelte` zählen
nicht als neu).

- [ ] **Step 2: Backend-Tests unverändert grün**

Run: `cd backend && ./gradlew.bat test`
Expected: `BUILD SUCCESSFUL` (dieser Plan ändert kein Backend-Verhalten,
reine Kontrolle, dass nichts kaputt gegangen ist).

- [ ] **Step 3: Visueller Rundgang im Browser**

Mit laufendem Backend (`./gradlew.bat bootRun`) und Frontend
(`npm run dev`):

- `/` (Home): Karte füllt den Bildschirm unter der Nav, Such-/Filter-Panel
  schwebt oben, Bottom-Sheet lässt sich auf-/zuklappen und zeigt Karten mit
  farbigen Kategorie-Tags
- Kombiniertes Suche+Filter-Verhalten (aus dem Fundament-Baustein) verhält
  sich weiterhin korrekt — nur der visuelle Rahmen hat sich geändert
- `/add`: Formular sieht wie eine zentrierte Karte aus, ein erfolgreiches
  Absenden funktioniert weiterhin end-to-end
- `/about`: rendert mit konsistentem Hintergrund/Rahmen, kein gebrochenes
  Layout
- Navigation zwischen allen drei Seiten über die neue NavBar funktioniert
  (aktiver Link wird hervorgehoben)
- Grober Responsive-Check: Browserfenster auf ca. 400px Breite verkleinern,
  nichts überlappt komplett unlesbar (kein dediziertes Mobile-Layout als
  Ziel, aber nichts soll sichtbar brechen)

- [ ] **Step 4: Report**

Zusammenfassung Pass/Fail für Schritt 1-3. Wenn alles passt: Redesign ist
fertig.
