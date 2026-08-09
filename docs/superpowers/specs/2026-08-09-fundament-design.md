# Fundament fertigstellen — Design

## Kontext

VoloMap (Benemap) ist eine Volunteer-Map: Spring-Boot-Backend (Kotlin, JPA/H2) +
Svelte-Frontend mit Leaflet-Karte. Board-Todo ([orga/board.md](../../../orga/board.md))
listet mehrere größere Ausbaustufen (mehrere Aktivitäten pro Ort, Zeitaufwand/
Verbindlichkeit, Autokategorisierung, Kalender-Sync). Dieses Dokument deckt nur
den ersten, grundlegenden Baustein ab: das bestehende Fundament (Suche, Filter,
Persistenz, Navigation) zu Ende bringen, bevor darauf aufgebaut wird.

Aktueller Stand (uncommitted, teils gestaged): SearchBar-Komponente wurde
begonnen, hat aber einen State-Bug im Zusammenspiel mit den bestehenden Filtern.

## Ziel

Eine stabile, in sich konsistente Basis:
- Suche und Filter funktionieren gleichzeitig statt sich gegenseitig zu überschreiben
- Daten überleben einen Server-Neustart
- Aktivitäten können über die UI hinzugefügt werden (nicht nur per Scraper)
- Die App hat funktionierende Navigation zwischen den vorbereiteten Seiten
- Kleinere technische Schulden (doppelte Dependency) sind bereinigt

## Nicht-Ziele

- Mehrere Aktivitäten am selben Ort (eigener Baustein, #2)
- Zeitaufwand/Verbindlichkeit als Datenfeld (eigener Baustein, #3)
- Autokategorisierung (eigener Baustein, #4)
- Kalender-Sync (eigener Baustein, #5)
- Echter Produktions-Datenbank-Betrieb (Postgres etc.) — H2 file-mode reicht für
  diese Phase

## Architektur / Komponenten

### 1. Suche + Filter kombinieren (Frontend)

`Map.svelte` hält aktuell zwei getrennte Zustände (`currentSearch` und die
FilterBar-Parameter), die beim Fetch sich gegenseitig auf leer setzen. Fix:
ein gemeinsames Zustandsobjekt

```ts
type MarkerQuery = {
  date: string; category: string; timeFrom: string; timeTo: string; search: string;
};
```

`handleFilter` und `handleSearch` aktualisieren jeweils nur ihre Felder in
diesem Objekt und rufen danach `fetchMarkers(query)` mit dem vollständigen
Objekt auf. Backend-Endpoint `/markers` bleibt unverändert — die
UND-Verknüpfung der Filter existiert dort bereits korrekt.

### 2. Persistenz statt Mock-Only (Backend)

- `application.properties`: `spring.datasource.url` von
  `jdbc:h2:mem:testdb` auf `jdbc:h2:file:./data/volomap` ändern, damit Daten
  Neustarts überleben.
- `VoloMapApp.main()`: `scraper.fakeScraper(30)` nur aufrufen, wenn
  `repository.count() == 0`, damit bestehende (echte oder manuell
  hinzugefügte) Daten nicht bei jedem Start überschrieben/aufgefüllt werden.

### 3. Add-Activity-Formular + Geocoding-Service (Full-Stack)

- Neuer `GeocodingService` (Backend, `@Component`), der die bestehende
  Nominatim-Anfrage-Logik aus `Scraper.geocode()` übernimmt. `Scraper` und
  `MainController` nutzen beide diesen Service (Dependency Injection statt
  Duplikation).
- `MainController.addActivity()`: wenn `latitude`/`longitude` im Request
  fehlen aber `addressText` vorhanden ist, wird serverseitig geocodet, bevor
  gespeichert wird. Schlägt Geocoding fehl, wird trotzdem gespeichert
  (ohne Koordinaten), Response enthält ein Flag/die Info, dass der Eintrag
  ohne Koordinaten gespeichert wurde.
- `AddActivity.svelte`: einfaches Formular (Name*, Beschreibung, Adresse,
  Kategorie, Datum/Uhrzeit) → `POST /add`. Bei Erfolg ohne Koordinaten:
  Hinweistext "Adresse konnte nicht gefunden werden — Eintrag ist gespeichert,
  erscheint aber noch nicht auf der Karte."

### 4. Router verdrahten (Frontend)

- `App.svelte`: statt `Map` direkt einzubinden, `NavBar` + `Router`
  (bestehende Komponente aus `router.ts`/`Router.svelte`) rendern.
- `router.ts`: `routes` um `/add → AddActivity` erweitern.
- `Home.svelte`: bekommt den `<Map>`-Aufruf, der bisher in `App.svelte` stand.
- `NavBar.svelte`: Link zu `/add` ergänzen (bestehendes Link-Pattern nutzen).

### 5. Kleinere Fixes

- `build.gradle.kts`: doppelte `runtimeOnly("com.h2database:h2")`-Zeilen
  entfernen (aktuell dreifach vorhanden, siehe Zeilen 29/40/41).

## Datenfluss (Suche/Filter, nach Fix)

```
FilterBar --filter event--> Map.svelte (merge in query state) --fetch--> GET /markers?date&category&timeFrom&timeTo&search
SearchBar --search event (debounced)--> Map.svelte (merge in query state) --fetch--> GET /markers?...
```

Beide Events schreiben in denselben `query`-State; jeder Fetch sendet immer
alle aktuell gesetzten Felder.

## Fehlerbehandlung

- Fetch-Fehler (Backend nicht erreichbar) in `Map.svelte`: einfache
  Fehlermeldung statt stillem Leerbleiben der Karte.
- Geocoding-Fehler beim manuellen Hinzufügen: Eintrag wird trotzdem
  gespeichert (kein harter Fehler), UI informiert transparent.
- Validierung im Add-Formular: `name` ist Pflichtfeld (clientseitig geprüft,
  serverseitig bereits durch non-nullable Kotlin-Typ abgesichert).

## Testing

- Backend (JUnit, Infra bereits vorhanden aber ungenutzt):
  - Controller-/Service-Test für `/markers`: kombinierte Filter (Kategorie +
    Suche + Zeitraum gleichzeitig) liefern korrektes Ergebnis.
  - Repository-/Service-Test für `GeocodingService`-Einbindung in
    `addActivity` (Erfolgsfall + Fehlerfall ohne Koordinaten).
- Frontend: manuell im Browser (Such-/Filter-Kombinationen, Add-Activity
  End-to-End, Neustart-Test für Persistenz). Kein Test-Setup für Svelte
  vorhanden — Einführung eines Test-Frameworks ist nicht Teil dieses
  Bausteins.

## Reihenfolge im Gesamtvorhaben

Dies ist Baustein 1 von 5 (siehe Tasks). Die folgenden Bausteine (Mehrere
Aktivitäten pro Ort, Zeitaufwand/Verbindlichkeit, Autokategorisierung,
Kalender-Sync) bauen auf diesem Fundament auf und werden jeweils eigene
Design-Dokumente bekommen.
