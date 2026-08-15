# Mehrere Aktivitäten an einem Ort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aktivitäten, die exakt dieselben geokodierten Koordinaten haben (gleiche Adresse), bekommen einen eigenen, deutlich erkennbaren Kartenpunkt statt sich unsichtbar zu überlappen — Hover oder Klick zeigt eine Liste aller dortigen Aktivitäten, aus der man eine einzelne zur Detailansicht öffnen kann.

**Architecture:** Rein clientseitig in `frontend/src/`. Eine neue reine Funktion `groupByLocation` gruppiert die vom Backend gelieferten `markers` nach gerundeten Koordinaten. `Map.svelte` rendert Gruppen mit genau einem Mitglied exakt wie bisher (`CircleMarker` + bestehendes Hover-`Tooltip`). Gruppen mit mehreren Mitgliedern rendert eine neue `ClusterMarker.svelte`-Komponente: ein größerer, nummerierter `Marker`+`DivIcon`-Pin, der bei Hover oder Klick ein interaktives `Popup` mit einer Zeile pro Aktivität zeigt; ein Zeilen-Klick dispatcht ein `select`-Event, das `Map.svelte` genauso behandelt wie den bestehenden `select`-Event aus `VolunteerList`.

**Tech Stack:** Svelte 5 (legacy-Style: `<script lang="ts">`, `export let`, `createEventDispatcher`, `$:` — keine Runes), `sveaflet` (`Marker`, `DivIcon`, `Popup`, `CircleMarker`, `Tooltip`), kein Backend-Change, kein neues Test-Framework (manuelle Browser-Verifikation, wie in diesem Projekt durchgängig üblich).

## Global Constraints

- Gruppierung nach Koordinaten gerundet auf 5 Nachkommastellen (`lat.toFixed(5)`, `lng.toFixed(5)`) — nicht nach Adress-String.
- Orte mit genau einer Aktivität: unverändertes Verhalten (`CircleMarker` + bestehendes Hover-`Tooltip` aus `Map.svelte`).
- Orte mit ≥2 Aktivitäten: neuer `Marker`+`DivIcon`-Pin (größerer Ring, `var(--color-primary)`-Hintergrund, `var(--color-accent)`-Rand, Anzahl mittig), kein `CircleMarker`.
- Hover **und** Klick/Tap auf den Cluster-Pin öffnen dasselbe Popup (Touch-Geräte haben keinen Hover-Zustand). Popup schließt automatisch bei Mouseout von Pin *und* Popup-Inhalt (mit kurzer Verzögerung, damit die Maus dazwischen wandern kann).
- Klick auf eine Zeile im Popup schließt das Popup und öffnet die normale Detailansicht (`selectedMarkerId` in `Map.svelte`) — identischer Mechanismus wie der bestehende Einzel-Pin-Klick und `VolunteerList`s `select`-Event.
- Keine Änderung an `PinDetailPanel.svelte`, `VolunteerList.svelte` oder am Backend.
- Alle Farben/Radien aus den bestehenden Design-Tokens (`--color-primary`, `--color-primary-text`, `--color-accent`, `--color-text`, `--color-text-muted`, `--color-border`, `--color-bg`, `--radius-md`, `--radius-pill`), keine neuen Farbwerte.
- Kein Test-Framework im Frontend — jeder Task endet mit manueller Browser-Verifikation, kein `vitest`/`jest`-Setup einführen.

---

### Task 1: `groupByLocation`-Hilfsfunktion + Cluster-Pin-Rendering (visuell, noch nicht interaktiv)

**Files:**
- Create: `frontend/src/lib/groupByLocation.ts`
- Create: `frontend/src/lib/ClusterMarker.svelte`
- Modify: `frontend/src/lib/Map.svelte`

**Interfaces:**
- Produces (`groupByLocation.ts`): `interface LocationGroup<T> { key: string; lat: number; lng: number; members: T[]; }` und `function groupByLocation<T extends { lat: number; lng: number; dateTime: string }>(markers: T[]): LocationGroup<T>[]`.
- Produces (`ClusterMarker.svelte`, Task 1 state — noch ohne Events): Props `lat: number`, `lng: number`, `members: { id: number; name: string; category: string; address: string; dateTime: string }[]`.
- Consumes: nichts aus früheren Tasks (erster Task).

- [ ] **Step 1: `groupByLocation.ts` schreiben**

```ts
export interface LocationGroup<T> {
    key: string;
    lat: number;
    lng: number;
    members: T[];
}

export function groupByLocation<T extends { lat: number; lng: number; dateTime: string }>(
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
        group.members.sort((a, b) => a.dateTime.localeCompare(b.dateTime));
    }

    return [...groups.values()];
}
```

- [ ] **Step 2: `ClusterMarker.svelte` schreiben (Pin-Rendering, noch ohne Popup/Interaktion)**

```svelte
<script lang="ts">
    import { Marker, DivIcon } from "sveaflet";

    export let lat: number;
    export let lng: number;
    export let members: {
        id: number;
        name: string;
        category: string;
        address: string;
        dateTime: string;
    }[] = [];
</script>

<Marker latLng={[lat, lng]}>
    <DivIcon options={{ className: "cluster-divicon", iconSize: [34, 34], iconAnchor: [17, 17] }}>
        <div class="cluster-pin">{members.length}</div>
    </DivIcon>
</Marker>

<style>
    :global(.cluster-divicon) {
        background: none;
        border: none;
    }
    .cluster-pin {
        width: 34px;
        height: 34px;
        border-radius: 50%;
        background: var(--color-primary);
        color: var(--color-primary-text);
        border: 3px solid var(--color-accent);
        box-shadow: 0 2px 5px rgba(0, 0, 0, 0.35);
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: 700;
        font-size: 0.8rem;
    }
</style>
```

- [ ] **Step 3: `Map.svelte` auf Gruppen umstellen**

In `frontend/src/lib/Map.svelte`, Imports ergänzen (nach der bestehenden `categoryColor`-Import-Zeile):

```ts
    import { groupByLocation } from "./groupByLocation";
    import ClusterMarker from "./ClusterMarker.svelte";
```

Nach der bestehenden Zeile `let markers: any[] = [];` eine reaktive Ableitung ergänzen:

```ts
    $: markerGroups = groupByLocation(markers);
```

Die bestehende `{#each markers as marker (marker.id)}`-Schleife (aktuell der einzige `CircleMarker`-Block innerhalb von `<Map>`) ersetzen durch:

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
                        <ClusterMarker lat={group.lat} lng={group.lng} members={group.members} />
                    {/if}
                {/each}
```

(Der `{#if}`-Zweig ist inhaltlich identisch zum bisherigen Einzel-Pin-Rendering — nur `marker` kommt jetzt aus `group.members[0]` statt direkt aus der `markers`-Iteration.)

- [ ] **Step 4: `svelte-check` laufen lassen**

Run: `cd frontend && npx svelte-check --tsconfig ./tsconfig.json`
Expected: keine neuen Fehler (die 2 bereits bestehenden `esrap`-Fehler in `node_modules` sind vorbestehend und betreffen keine Projektdatei).

- [ ] **Step 5: Manuell verifizieren**

Backend (`cd backend && .\gradlew.bat bootRun`) und Frontend (`cd frontend && npm run dev`) starten. Als eingeloggter Anbieter zwei Testaktivitäten mit **identischer Adresse** anlegen (z. B. über das bestehende "Aktivität hinzufügen"-Formular) und eine dritte mit einer anderen Adresse. Auf der Karte prüfen:
- Die beiden Aktivitäten mit gleicher Adresse zeigen **einen** größeren Ring-Pin mit der Zahl "2" darin, statt zwei sich überlappender Einzel-Pins.
- Die dritte Aktivität (andere Adresse) zeigt weiterhin einen normalen kleinen Einzel-Pin mit dem bestehenden Hover-Tooltip (Name/Kategorie/Datum/Bewertung) — unverändert.
- Der Cluster-Pin reagiert auf diesen Task noch nicht auf Hover/Klick (kommt in Task 2) — das ist an dieser Stelle erwartet.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/groupByLocation.ts frontend/src/lib/ClusterMarker.svelte frontend/src/lib/Map.svelte
git commit -m "feat: group markers by location, render cluster pin for shared addresses"
```

---

### Task 2: Popup-Interaktivität in `ClusterMarker.svelte`

**Files:**
- Modify: `frontend/src/lib/ClusterMarker.svelte`
- Modify: `frontend/src/lib/Map.svelte`

**Interfaces:**
- Consumes: `ClusterMarker`-Props aus Task 1 (`lat`, `lng`, `members`); `categoryColor(category: string): { bg: string; text: string }` aus `frontend/src/lib/categoryColor.ts` (bereits im Projekt vorhanden, siehe Task 1 der Hover-Übersicht-Änderung in `Map.svelte`); `handleSelect(event: CustomEvent<{ id: number }>)` aus `Map.svelte` (bereits vorhanden, wird von `VolunteerList`s `select`-Event verwendet).
- Produces: `ClusterMarker` dispatcht `select: { id: number }` — gleiche Event-Form wie `VolunteerList`s `select`-Event, damit `Map.svelte` denselben Handler wiederverwenden kann.

- [ ] **Step 1: `ClusterMarker.svelte` um Popup und Event-Handling erweitern**

Gesamten Inhalt von `frontend/src/lib/ClusterMarker.svelte` durch folgendes ersetzen:

```svelte
<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import { Marker, DivIcon, Popup } from "sveaflet";
    import { categoryColor } from "./categoryColor";

    export let lat: number;
    export let lng: number;
    export let members: {
        id: number;
        name: string;
        category: string;
        address: string;
        dateTime: string;
    }[] = [];

    const dispatch = createEventDispatcher<{ select: { id: number } }>();

    let popupOpen = false;
    let closeTimer: ReturnType<typeof setTimeout> | null = null;

    function openNow() {
        if (closeTimer) {
            clearTimeout(closeTimer);
            closeTimer = null;
        }
        popupOpen = true;
    }

    function scheduleClose() {
        if (closeTimer) clearTimeout(closeTimer);
        closeTimer = setTimeout(() => {
            popupOpen = false;
            closeTimer = null;
        }, 200);
    }

    function handleMarkerClick() {
        if (popupOpen) {
            scheduleClose();
        } else {
            openNow();
        }
    }

    function selectMember(id: number) {
        if (closeTimer) {
            clearTimeout(closeTimer);
            closeTimer = null;
        }
        popupOpen = false;
        dispatch("select", { id });
    }
</script>

<Marker
    latLng={[lat, lng]}
    onmouseover={openNow}
    onmouseout={scheduleClose}
    onclick={handleMarkerClick}
>
    <DivIcon options={{ className: "cluster-divicon", iconSize: [34, 34], iconAnchor: [17, 17] }}>
        <div class="cluster-pin">{members.length}</div>
    </DivIcon>
</Marker>

{#if popupOpen}
    <Popup
        latLng={[lat, lng]}
        options={{ closeButton: false, autoClose: false, closeOnClick: false, offset: [0, -16] }}
    >
        <div class="cluster-popup" on:mouseenter={openNow} on:mouseleave={scheduleClose}>
            <p class="cluster-popup-title">{members.length} Aktivitäten hier</p>
            <p class="cluster-popup-address">{members[0]?.address ?? ""}</p>
            {#each members as member (member.id)}
                <button type="button" class="cluster-row" on:click={() => selectMember(member.id)}>
                    <span class="cluster-row-text">
                        <span class="cluster-row-name">{member.name}</span>
                        <span class="cluster-row-date">{new Date(member.dateTime).toLocaleString("de-DE")}</span>
                    </span>
                    {#if member.category}
                        <span
                            class="cluster-row-tag"
                            style="background:{categoryColor(member.category).bg}; color:{categoryColor(member.category).text};"
                        >{member.category}</span>
                    {/if}
                </button>
            {/each}
        </div>
    </Popup>
{/if}

<style>
    :global(.cluster-divicon) {
        background: none;
        border: none;
    }
    .cluster-pin {
        width: 34px;
        height: 34px;
        border-radius: 50%;
        background: var(--color-primary);
        color: var(--color-primary-text);
        border: 3px solid var(--color-accent);
        box-shadow: 0 2px 5px rgba(0, 0, 0, 0.35);
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: 700;
        font-size: 0.8rem;
    }

    .cluster-popup {
        min-width: 220px;
        max-width: 260px;
    }

    .cluster-popup-title {
        margin: 0;
        font-size: 0.8rem;
        font-weight: 700;
        color: var(--color-text);
    }

    .cluster-popup-address {
        margin: 2px 0 8px;
        font-size: 0.75rem;
        color: var(--color-text-muted);
    }

    .cluster-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        width: 100%;
        padding: 6px 8px;
        margin-bottom: 4px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        cursor: pointer;
        font: inherit;
        text-align: left;
    }

    .cluster-row:hover {
        border-color: var(--color-primary);
    }

    .cluster-row-text {
        display: flex;
        flex-direction: column;
    }

    .cluster-row-name {
        font-weight: 600;
        font-size: 0.8rem;
        color: var(--color-text);
    }

    .cluster-row-date {
        font-size: 0.7rem;
        color: var(--color-text-muted);
    }

    .cluster-row-tag {
        font-size: 0.65rem;
        padding: 1px 6px;
        border-radius: var(--radius-pill);
        white-space: nowrap;
    }
</style>
```

- [ ] **Step 2: `Map.svelte` auf das `select`-Event von `ClusterMarker` reagieren lassen**

In `frontend/src/lib/Map.svelte` die in Task 1 eingefügte Zeile

```svelte
                        <ClusterMarker lat={group.lat} lng={group.lng} members={group.members} />
```

ersetzen durch:

```svelte
                        <ClusterMarker
                            lat={group.lat}
                            lng={group.lng}
                            members={group.members}
                            on:select={handleSelect}
                        />
```

(`handleSelect` existiert bereits in `Map.svelte` — derselbe Handler, den auch `VolunteerList`s `select`-Event nutzt.)

- [ ] **Step 3: `svelte-check` laufen lassen**

Run: `cd frontend && npx svelte-check --tsconfig ./tsconfig.json`
Expected: keine neuen Fehler.

- [ ] **Step 4: Manuell verifizieren**

Mit den zwei Testaktivitäten aus Task 1 (gleiche Adresse) im Browser:
- Maus über den Cluster-Pin bewegen (hover) → Popup mit Titel "2 Aktivitäten hier", der gemeinsamen Adresse und je einer Zeile pro Aktivität (Name, Datum, Kategorie-Tag) erscheint.
- Maus vom Pin auf eine Zeile im Popup bewegen, ohne den Cursor zwischenzeitlich ganz zu verlassen → Popup bleibt offen (schließt nicht durch den kurzen Übergang).
- Auf eine Zeile klicken → Popup schließt, das normale Seitenpanel (`PinDetailPanel`) öffnet sich mit genau der angeklickten Aktivität.
- Maus complet vom Pin und Popup wegbewegen, ohne zu klicken → Popup schließt nach kurzer Verzögerung von selbst.
- Direkter Klick auf den Pin (ohne vorheriges Hover, z. B. Browser-Fenster schmal genug für Touch-Emulation oder einfach Klick ohne vorheriges Hover) → dasselbe Popup öffnet sich.
- Der normale Einzel-Pin (dritte Testaktivität, andere Adresse) verhält sich weiterhin exakt wie vor dieser Änderung.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/ClusterMarker.svelte frontend/src/lib/Map.svelte
git commit -m "feat: open interactive popup on cluster pin hover/click, select activity from list"
```

---

### Task 3: End-to-End-Verifikation

**Files:** keine Code-Änderungen — reine Verifikation, ggf. kleine Nacharbeiten falls Abweichungen gefunden werden.

**Interfaces:**
- Consumes: das vollständige Feature aus Task 1 + Task 2.
- Produces: nichts (Verifikationsergebnis wird im Ledger festgehalten).

- [ ] **Step 1: Sauberen Zustand herstellen**

Backend und Frontend laufen (`cd backend && .\gradlew.bat bootRun`, `cd frontend && npm run dev`). Falls noch nicht vorhanden, mindestens drei Testaktivitäten anlegen: zwei mit identischer Adresse, eine mit einer anderen Adresse. Geocoding braucht ca. 1,1s pro Aktivität (Nominatim-Rate-Limit in `GeocodingService.kt`) — beim Anlegen kurz warten.

- [ ] **Step 2: Regressionscheck — Einzel-Pins**

Auf der Karte: Einzel-Pin (andere Adresse) zeigt weiterhin den kleinen `CircleMarker`, Hover zeigt das bestehende Tooltip mit Name/Kategorie/Datum/Bewertung, Klick öffnet `PinDetailPanel` wie vor dieser Änderung. `VolunteerList` (Bottom-Sheet aufklappen) zeigt weiterhin alle Aktivitäten einzeln, unverändert.

- [ ] **Step 3: Cluster-Pin — volle Interaktion**

Hover über den Cluster-Pin öffnet die Liste; ein Klick auf eine Zeile öffnet die korrekte Detailansicht (Titel/Adresse/Datum in `PinDetailPanel` müssen zur angeklickten Zeile passen, nicht zur ersten); danach erneut hovern und eine *andere* Zeile anklicken, um zu bestätigen, dass nicht immer dieselbe Aktivität geöffnet wird.

- [ ] **Step 4: Filter/Suche mit Cluster-Pins**

Über die Suchleiste nach dem Namen einer der beiden geclusterten Aktivitäten filtern, sodass nur noch eine der beiden übrig bleibt → der Punkt muss zu einem normalen Einzel-Pin zurückwechseln (die Gruppierung ist rein clientseitig aus dem jeweils aktuellen `markers`-Ergebnis abgeleitet, muss sich also bei jeder Filteränderung neu berechnen).

- [ ] **Step 5: Konsole/Netzwerk prüfen**

Keine neuen Fehler in der Browser-Konsole oder fehlgeschlagene Requests, die durch diese Änderung verursacht wurden (bereits bekannte `401` von `/auth/me` im ausgeloggten Zustand ist normales Bestandsverhalten).

- [ ] **Step 6: Ledger-Eintrag**

Ergebnis der Verifikation im SDD-Ledger festhalten (Status, ggf. gefundene und behobene Abweichungen).
