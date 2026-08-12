# Pin-Detailpanel — Design

**Datum:** 2026-08-12
**Status:** Approved, bereit für Implementierungsplanung

## Ziel

Die Pin-Detailseite (aktuell ein unstyled Leaflet-Popup mit nackten
`h3`/`p`-Tags) wird durch ein Google-Maps-inspiriertes Seitenpanel ersetzt,
das Kategorie, Datum, Adresse, Beschreibung sowie Aktivitäts- und
Anbieter-Bewertungen anzeigt und direkt ins bestehende Bewertungssystem
verlinkt.

## Nicht-Ziele

- Kein dediziertes Mobile-Layout für das Panel — unterhalb der Breakpoint-
  Breite bleibt es bei der bisherigen Situation (Details nur über die
  Bottom-Sheet-Liste, kein Pin-Popup). Kann später nachgezogen werden.
- Keine Änderung an den blauen `CircleMarker`-Pins selbst (bewusste
  Entscheidung aus einer früheren Diskussion — sie zeigen überlagernde
  Events und bleiben wie sie sind).
- Kein neues Bild/Medienfeld für Aktivitäten — falls später Bilder
  hinzukommen, ist das ein eigenes Thema.

## Entscheidungen

- **Layout: Seitenpanel** (Option C aus dem visuellen Vergleich), nicht das
  kompakte Info-Fenster oder das Bottom-Sheet — geräumigste Variante,
  Google-Maps-Desktop-Vorbild.
- **Nur Desktop ab 1024px Breite.** Unterhalb bleibt das Verhalten
  unverändert (kein Panel, kein Popup) — kleinerer Umfang fürs Erste.
- **Panel-Breite: 360px, links positioniert**, analog zu Google Maps.
- **Doppelter Auslöser:** Klick auf einen Pin auf der Karte ODER auf eine
  Karte in der bestehenden `VolunteerList` öffnet dasselbe Panel — beide
  Wege führen zum selben Detail, keine getrennten Implementierungen.
- **Inhalt umfasst auch die Beschreibung** — bisher nirgends im Frontend
  angezeigtes Feld.
- Löst nebenbei den bereits vermerkten Politur-Punkt: **Anbieter-Name wird
  jetzt tatsächlich angezeigt** (das Feld `Marker.providerName` existiert
  seit dem Bewertungssystem-Feature, wurde aber noch nirgends
  konsumiert).

## Architektur & Layout

Neue Komponente `PinDetailPanel.svelte`. `Map.svelte` bekommt einen neuen
State `selectedMarker: Marker | null`. Ab 1024px Fensterbreite wird das
Panel (fest 360px, links) als **Overlay** über der Karte eingeblendet
(`position: absolute` innerhalb von `.map-area`, höherer z-index) —
**nicht** durch eine Größenänderung der Karte, sondern analog zum
bestehenden Bottom-Sheet-Muster (die Karte bleibt in voller Breite,
darüber liegende Elemente wie die Suchleiste weichen zur Seite aus). Diese
Entscheidung wurde während der Implementierung revidiert: die
ursprüngliche Flex-Reihen-Variante (Karte schiebt sich zur Seite,
`invalidateSize()`-Workaround nötig) wurde nach Rückmeldung verworfen, da
sie nicht dem Google-Maps-Vorbild entspricht — dort verschiebt sich die
Karte beim Öffnen des Seitenpanels ebenfalls nicht. Unterhalb von 1024px
bleibt `selectedMarker` ungenutzt — kein Panel wird gerendert, Klicks auf
Pins haben (vorerst) keine Detailansicht zur Folge.

## Auslöser & Schließen

- Klick auf einen `CircleMarker` setzt `selectedMarker` auf die
  entsprechende Aktivität.
- `VolunteerList` feuert bei Klick auf eine Karte ein neues `select`-Event
  (Payload: die `marker`-Objekt), analog zum bestehenden `refresh`-Event
  — `Map.svelte` reagiert mit `on:select={(e) => selectedMarker = e.detail}`.
- Klick auf einen anderen Pin/eine andere Liste-Karte wechselt direkt um
  (kein Zwischenschritt).
- Ein Schließen-Button (×) im Panel-Header sowie ein Klick auf die Karte
  außerhalb eines Pins setzen `selectedMarker` zurück auf `null`.

## Panel-Inhalt

- Kategorie-Tag (wiederverwendet die bestehende Farb-Hash-Logik aus
  `VolunteerList`)
- Titel (Aktivitätsname)
- Datum/Uhrzeit, Adresse
- Beschreibung (`marker.description`, bisher ungenutzt)
- Aktivitäts-Bewertung: Sterne + Anzahl, klickbar → öffnet das bestehende
  `RatingModal` mit `target="activity"`
- Anbieter (falls `marker.providerId` gesetzt): Name (`marker.providerName`)
  + Sterne + Anzahl, klickbar → `RatingModal` mit `target="provider"`
- Falls kein Anbieter (gescrapte/geseedete Aktivität ohne `createdBy`):
  Anbieter-Zeile wird komplett ausgeblendet, wie schon in `VolunteerList`
  gehandhabt

## Fehlerbehandlung

- Aktivität ohne Bewertungen: "Noch keine Bewertung" statt Sternen,
  identisch zum bestehenden Verhalten in `VolunteerList`
- Leeres `description`-Feld: Abschnitt wird ausgeblendet, kein leerer
  Platzhalter

## Tests

- Frontend: manuelle Sichtprüfung (kein Test-Framework im Projekt, siehe
  bestehende Konvention). Manuell zu prüfen: Panel öffnet über Pin-Klick
  und über Listen-Klick identisch; Kartenkacheln sehen nach Öffnen/
  Schließen nicht verzerrt aus; unterhalb 1024px bleibt das bisherige
  Verhalten unverändert.
