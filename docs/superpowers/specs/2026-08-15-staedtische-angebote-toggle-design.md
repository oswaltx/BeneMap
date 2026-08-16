# Städtische Angebote (Köln) — Toggle — Design

**Datum:** 2026-08-15
**Status:** Approved, bereit für Implementierungsplanung

## Ziel

Der bestehende Scraper (`Scraper.kt`) kann echte Angebote von der Kölner
Engagementdatenbank (engagementdatenbank.stadt-koeln.de) importieren, aber
diese Einträge haben kein Anbieter-Konto (`createdBy` bleibt leer) und
keinen echten Termin (die Stadt Köln führt bei diesen Angeboten keine
Termine). Nutzer sollen diese "Städtischen Angebote" optional zusätzlich
zu den App-eigenen, datierten Aktivitäten auf der Karte sehen können.

## Nicht-Ziele

- Kein Live-Abfragen der Stadt-Webseite beim Umschalten des Toggles — der
  Scraper läuft weiterhin manuell/gelegentlich vorab, der Toggle filtert
  nur bereits importierte, in der eigenen Datenbank gespeicherte Einträge.
- Keine echten Anbieter-Konten für gescrapte Organisationen (siehe
  vorherige Diskussion: ein Konto ohne bekanntes Passwort der Organisation
  wäre ein Fake-Account in ihrem Namen — das bauen wir nicht).
- Keine Sonderbehandlung für Cluster-Pins, die zufällig einen externen und
  einen App-eigenen Eintrag an derselben Adresse mischen — bleiben
  unverändert im bestehenden Cluster-Look (Ring mit Anzahl).
- Keine Persistenz der Toggle-Einstellung (z. B. `localStorage`) — setzt
  bei jedem Laden auf den Default (aus) zurück, analog zu den bestehenden
  Filtern (Kategorie/Datum/Uhrzeit), die ebenfalls nicht persistiert
  werden.
- Kein neues Boolean-Flag in der Datenbank für "extern" — wird rein aus
  `sourceUrl != null` abgeleitet (jede Aktivität mit gesetztem `sourceUrl`
  stammt vom Scraper, nie vom eigenen `/add`-Formular).

## Entscheidungen

- **Datenfluss:** vorab importieren (Scraper), Toggle filtert nur die
  Anzeige der bereits geladenen `/markers`-Antwort — kein Server-Request
  beim Umschalten.
- **Liste:** Städtische Angebote erscheinen bei aktiviertem Toggle auch im
  Bottom-Sheet (`VolunteerList`) — keine Sonderbehandlung, dieselbe
  gefilterte Marker-Liste geht an Karte und Liste.
- **Default:** Toggle ist standardmäßig **aus** (Opt-in).
- **Pin-Stil:** gleiche Form/Größe wie normale Punkte, aber gestrichelter/
  andersfarbiger Rand statt vollem Primärfarben-Fill.

## Architektur & Datenfluss

**Backend:**
- `Marker` (API-DTO für `/markers`) bekommt ein neues Feld
  `sourceUrl: String?`, befüllt aus `activity.sourceUrl` in
  `MainController.markers()`. Das Frontend leitet daraus ab, ob ein Punkt
  "extern" ist (`sourceUrl != null`) — kein zusätzliches Flag nötig.
- `Scraper.kt`s `scrapeEhrenamtDetails()`: `dateTime = LocalDateTime.now()`
  wird entfernt — der `VolunteerActivity`-Konstruktor-Aufruf lässt
  `dateTime` auf seinem Default (`null`) stehen, da die Kölner
  Engagementdatenbank keine Termine für diese Angebote führt. Bisheriges
  Verhalten (`LocalDateTime.now()`) war irreführend, da es einen Termin
  vortäuschte, den es nicht gibt.

**Frontend:**
- `FilterBar.svelte`: neue Checkbox "Städtische Angebote (Köln) anzeigen"
  im bestehenden Popover, eigener Zustand `showCityOffers: boolean`
  (Default `false`). Löst ein neues, eigenständiges Event
  `toggleCityOffers: boolean` aus (nicht Teil des bestehenden `filter`-
  Events, da kein Server-Query-Parameter dahintersteht). `activeCount`
  (Filter-Badge-Zähler) berücksichtigt den Toggle mit.
- `Map.svelte`: neuer lokaler State `showCityOffers = false`, aktualisiert
  über `on:toggleCityOffers`. Neue reaktive Ableitung
  `$: visibleMarkers = markers.filter((m) => showCityOffers || !m.sourceUrl);`
  — diese Liste (nicht mehr das rohe `markers`) geht an die Marker-
  Gruppierung fürs Kartenrendering **und** an `<VolunteerList
  markers={visibleMarkers} .../>`.
- Kartenrendering: im bestehenden Einzel-Pin-Zweig (Gruppen mit genau
  einem Mitglied) bekommt ein `CircleMarker` mit `member.sourceUrl != null`
  zusätzliche `options` (`dashArray: "4, 4"`, abweichende `color`) statt
  der vollen Primärfarben-Füllung. Cluster-Pins (mehrere Mitglieder)
  bleiben unverändert.
- `PinDetailPanel.svelte` und `VolunteerList.svelte`: die Datumszeile
  (`{new Date(marker.dateTime).toLocaleString("de-DE")}`) wird
  bedingt gerendert (`{#if marker.dateTime}`) statt unbedingt — vermeidet
  eine Anzeige von "Invalid Date" bei `dateTime: null`. Die
  `dateTime`-Prop-Typen in beiden Komponenten werden von `string` auf
  `string | null` erweitert. Ist `marker.sourceUrl` gesetzt, zeigt
  `PinDetailPanel` zusätzlich einen Link "Mehr Infos auf der Webseite der
  Stadt Köln" (`<a href={marker.sourceUrl} target="_blank"
  rel="noopener noreferrer">`).
- `groupByLocation.ts`: die bestehende Sortierung
  `a.dateTime.localeCompare(b.dateTime)` wird null-sicher gemacht —
  Mitglieder ohne `dateTime` sortieren ans Ende der Gruppe, statt beim
  Aufruf von `.localeCompare` auf `null` eine `TypeError` zu werfen. Das
  ist ein eigenständiger Bugfix, unabhängig von diesem Feature nötig,
  sobald irgendein Marker ohne Datum existiert.

**Keine Änderung an Bearbeiten/Löschen-Logik** — beide sind bereits an
`isOwner = $currentUser?.id === marker.providerId` gekoppelt, und
Scraper-Einträge haben `providerId: null`, also niemals `isOwner`. Kein
neuer Code nötig, um Städtische Angebote als nicht-bearbeitbar
darzustellen.

## Fehlerbehandlung

- `dateTime: null` beim Rendern: Datumszeile wird komplett ausgeblendet
  statt "Invalid Date" anzuzeigen (siehe oben).
- `sourceUrl` zeigt auf eine mittlerweile tote/geänderte Seite der Stadt:
  kein serverseitiges Prüfen, der Link öffnet einfach wie eingetragen —
  analog zum bestehenden Umgang mit `providerWebsiteUrl` (keine
  Erreichbarkeitsprüfung).
- Aktivitäts-Bewertung bleibt für Städtische Angebote möglich (bezieht
  sich auf die Aktivität selbst, nicht auf einen Anbieter) — Anbieter-
  Bewertung bleibt automatisch ausgeblendet, da `providerId == null`.

## Tests

- Backend: `Marker.sourceUrl` wird korrekt aus `activity.sourceUrl`
  übernommen (vorhanden/`null`); `Scraper.scrapeEhrenamtDetails()` setzt
  `dateTime` nicht mehr auf einen Wert (bleibt `null`).
- Frontend: manuelle Sichtprüfung (kein Test-Framework im Projekt, siehe
  bestehende Konvention). Manuell zu prüfen: Toggle aus → Städtische
  Angebote weder auf Karte noch in Liste sichtbar; Toggle an → erscheinen
  mit gestricheltem Rand auf der Karte und in der Liste; Panel eines
  Städtischen Angebots zeigt keine Datumszeile, aber den Link zur
  Stadt-Köln-Quelle; kein Bearbeiten/Löschen-Button für Städtische
  Angebote sichtbar (auch nicht für eingeloggte Anbieter); ein Cluster,
  der einen Städtischen und einen App-eigenen Eintrag an derselben
  Adresse mischt, zeigt weiterhin den normalen Cluster-Ring ohne Crash.
