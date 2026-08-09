# UI-Redesign — Design

## Kontext

VoloMap (Benemap) läuft visuell noch komplett auf dem unveränderten Vite/Svelte-
Starter-Template: lila Akzentfarbe, `prefers-color-scheme`-abhängiger Dark-Mode-
Default, keine eigene Typografie. Einzelne Komponenten (`FilterBar.svelte`,
`SearchBar.svelte`, `AddActivity.svelte`) haben jeweils eigene, uneinheitliche
Hardcoded-Farben (`#ccc`, `#333`, `#5f9361`, ...). `NavBar.svelte` hat gar kein
Styling. Es gibt kein zentrales Design-System.

Dieses Dokument beschreibt ein rein visuelles Redesign der bestehenden App —
keine neuen Features, keine Backend-/Datenmodell-Änderungen. Basis ist das im
Brainstorming mit visuellem Companion getroffene Ergebnis:

- **Farbrichtung:** "Sonnenblume & Wald" — Sonnengelb (`#F4C542`) als
  Hauptakzent, Waldgrün (`#2F5233`) als Zweitfarbe, warmes Off-White
  (`#FFFBF2`) als Hintergrund. Kräftige, runde Formen.
- **Layout-Richtung:** Karte im Vollbild mit schwebenden Panels (Suche/Filter
  oben als Card, Aktivitäten-Liste als aus-/einklappbares Bottom-Sheet) statt
  der bisherigen gestapelten Anordnung.

## Ziel

Eine visuell konsistente, warme und einladende App, die sich wie ein
durchdachtes Ganzes anfühlt statt wie eine Sammlung ungestylter Einzelteile —
ohne die bestehende Funktionalität (Suche, Filter, Aktivität hinzufügen,
Navigation) zu verändern.

## Nicht-Ziele

- Keine neuen Features oder Datenfelder
- Keine Backend-Änderungen
- Kein Dark-Mode-Support (feste helle Palette ersetzt den bisherigen
  zufälligen `prefers-color-scheme`-Effekt)
- Kein Inhalt für `About.svelte` — nur Layout/Styling-Konsistenz, die Seite
  bleibt inhaltlich leer
- Keine Einführung eines Frontend-Test-Frameworks

## Architektur / Komponenten

### 1. Design-Tokens (`frontend/src/app.css`)

Zentrale CSS-Custom-Properties ersetzen das Vite-Starter-CSS:

```css
:root {
  --color-accent: #F4C542;       /* Sonnengelb */
  --color-accent-text: #4A3B00;  /* Text auf Gelb */
  --color-primary: #2F5233;      /* Waldgrün */
  --color-primary-text: #FFFBF2; /* Text auf Grün */
  --color-bg: #FFFBF2;           /* Warmes Off-White */
  --color-surface: #FFFFFF;      /* Cards/Panels */
  --color-text: #2A2A22;         /* Fließtext */
  --color-text-muted: #6B6B5A;
  --color-border: #E5DCC3;
  --radius-md: 10px;
  --radius-lg: 16px;
  --radius-pill: 999px;
  --shadow-panel: 0 3px 10px rgba(47, 82, 51, 0.15);
}
```

Basis-Body-/Link-/Button-Styles werden auf diese Variablen umgestellt,
`@media (prefers-color-scheme: light/dark)`-Sonderfälle entfernt (feste
Palette, kein Dark Mode).

### 2. Layout-Umbau (`frontend/src/lib/Map.svelte`)

- Karte füllt die volle verfügbare Höhe/Breite (statt `height:500px`-Box)
- Neues schwebendes Panel oben: enthält `FilterBar` + `SearchBar` visuell
  zusammengefasst in einer Card mit `--shadow-panel`, `position: absolute`
  über der Karte
- `VolunteerList` wandert in ein Bottom-Sheet: `position: fixed` am unteren
  Rand, standardmäßig eingeklappt (nur ein Griff mit Anzahl der Treffer
  sichtbar, z.B. "12 Aktivitäten ▲"), per Klick auf den Griff auf- und
  zuklappbar (lokaler `let expanded = false`-State in `Map.svelte`, kein
  Backend-Bezug)
- Die bestehende Datenfluss-Logik (der in der Fundament-Arbeit gefixte
  `query`-State, `fetchMarkers`, `handleSearch`/`handleFilter`,
  `errorMessage`-Handling) bleibt vollständig unverändert — nur Markup/CSS
  wird umgebaut

### 3. Komponenten-Restyle (nur Styling, keine Logikänderung)

- **`NavBar.svelte`**: Kopfleiste mit `--color-primary`-Hintergrund, Logo/
  Titel links, Links rechts (nutzt weiterhin `Link.svelte` unverändert)
- **`FilterBar.svelte`**: Buttons/Select auf Token-Farben umgestellt,
  aktive Zustände nutzen `--color-accent`
- **`SearchBar.svelte`**: Input-Styling auf Token-Farben, keine
  Verhaltensänderung (Debounce bleibt)
- **`VolunteerList.svelte`**: von reiner Textliste zu Karten mit
  farbcodiertem Kategorie-Tag (Kategorie → Farbe ist rein visuell, z.B. per
  Hash der Kategorie-Strings auf eine kleine feste Palette abgebildet — kein
  neues Datenfeld)
- **`AddActivity.svelte`**: bestehendes Formular bekommt Card-/Input-/
  Button-Styling passend zum Rest, Erfolgs-/Warn-/Fehlermeldungen nutzen
  Token-Farben statt der aktuellen Inline-Farben
- **`About.svelte`**: bleibt inhaltlich leer, aber die Seite läuft durch
  denselben Layout-Rahmen (Hintergrund, ggf. Card-Container), damit sie nicht
  aus dem Rahmen fällt
- **`Button.svelte`**: wird beim Umsetzen daraufhin geprüft, ob es überhaupt
  irgendwo verwendet wird (aktuell keine Fundstelle bekannt) — wenn
  ungenutzt, wird es entfernt statt mit Tokens nachgezogen; wenn doch
  irgendwo verwendet, wird es ins Token-System integriert

## Datenfluss

Unverändert. Dieses Redesign ist rein präsentational — kein Request/Response-
Format, keine Events, keine Zustandsverwaltung wird inhaltlich verändert,
nur wo nötig um den neuen Bottom-Sheet-Expand/Collapse-State ergänzt (rein
lokaler UI-State, kein Server-Bezug).

## Fehlerbehandlung

Keine neuen Fehlerfälle. Das bestehende `errorMessage`-Handling in
`Map.svelte` (aus der Fundament-Arbeit) bleibt erhalten und wird nur
visuell in das neue Panel-Layout integriert.

## Testing

Rein manuell im Browser, wie bereits beim Fundament-Baustein (kein
Frontend-Test-Framework vorhanden, Einführung ist explizit Nicht-Ziel):

- Alle Ansichten durchklicken (Home/Karte, Aktivität hinzufügen, About)
- Bottom-Sheet auf-/zuklappen
- Kombiniertes Suche+Filter-Verhalten bleibt funktional identisch (nur
  optischer Rahmen ändert sich)
- Grober Responsive-Check (schmalerer Viewport) — kein dediziertes Mobile-
  Layout als eigenes Ziel, aber es soll nicht sichtbar brechen

## Reihenfolge im Gesamtvorhaben

Dieses Redesign ist unabhängig von den 5 Board-Bausteinen (siehe
Fundament-Spec) und baut auf dem in Baustein 1 ("Fundament fertigstellen")
geschaffenen Stand auf (kombinierte Suche/Filter, Router, AddActivity-
Formular, Fehlerbehandlung). Es liefert keine neue Funktionalität und blockt
daher keinen der übrigen Bausteine — kann parallel oder in beliebiger
Reihenfolge dazu eingeplant werden.
