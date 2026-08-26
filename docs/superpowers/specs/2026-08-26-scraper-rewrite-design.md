# Scraper-Überarbeitung — Design

**Datum:** 2026-08-26
**Status:** Approved, bereit für Implementierungsplanung

## Ziel

Der bestehende Scraper (`Scraper.kt`) importiert echte Angebote von der
Kölner Engagementdatenbank, aber die importierten Daten sind falsch:
Namen landen als "Unbekannt", Kategorien werden zufällig gewürfelt, und
die geokodierte Adresse zeigt auf das Büro der Vermittlungsstelle statt
auf den tatsächlichen Einsatzort. Zusätzlich lässt sich der Scraper nur
laufen lassen, indem man Quellcode ändert (einen auskommentierten Block
in `VoloMapApp.kt` freischaltet). Ziel ist ein Scraper, der die Seite
korrekt ausliest und sich gefahrlos wiederholt anstoßen lässt.

## Befund (an der echten Seite verifiziert)

- **Name:** Es gibt kein `div.field`-Feld mit Label "Projektname" auf den
  Detailseiten (`https://engagementdatenbank.stadt-koeln.de/ehrenamt/...`).
  Der echte Titel steht als reiner `<h1>` auf der Detailseite und —
  günstiger — bereits als Linktext auf der Ergebnisliste
  (`div.views-field-title a`), die der Scraper ohnehin abruft, um an die
  Detail-URLs zu kommen.
- **Kategorie:** Kein Kategorie-Feld auf der Detailseite oder im
  Ergebnislisten-Eintrag. Die Seite hat aber ein echtes, stabiles
  Kategorie-Vokabular über den Such-Filter `area_of_activity` (17 Werte,
  z. B. `476` = "Bildung", `475` = "Umwelt, Natur und Tierschutz", `464`
  = "Soziales" — vollständige Liste unten). Scrapt man pro Kategorie
  einzeln (statt `area_of_activity=All`), ist die Kategorie jedes
  Treffers durch den Suchparameter eindeutig bekannt.
- **Standort:** Detailseiten haben zwei unterschiedliche Adressfelder:
  "Adresse der Vermittlungsstelle" (Büro des vermittelnden Vereins) und
  "Einsatzort" (wo die Tätigkeit tatsächlich stattfindet) — das sind bei
  vielen Angeboten unterschiedliche Orte in Köln. Der bestehende Scraper
  geokodiert nur die Vermittlungsstelle-Adresse.

## Nicht-Ziele

- Kein Update bereits importierter Angebote bei Änderungen auf der
  Quellseite — `existsBySourceUrl`-Deduplizierung bleibt "einmal
  importiert, nie wieder angefasst", wie bisher. Ein Änderungs-Tracking
  (z. B. über das auf der Ergebnisliste sichtbare "Geändert am"-Datum)
  wäre ein eigenes, größeres Feature.
- Kein neuer öffentlicher HTTP-Endpunkt zum Anstoßen des Scrapers und
  keine neue Admin-Rolle — die App hat aktuell nur `ANBIETER`/`USER`,
  eine Rollen-Erweiterung nur für diesen Zweck wäre unverhältnismäßig.
- Keine automatische/geplante Ausführung (kein `@Scheduled`-Cronjob) —
  der Scraper bleibt ein bewusst manuell angestoßener Vorgang.
- Keine Änderung an `fakeScraper()` (Mock-Daten-Generator für
  Entwicklung) — betrifft nur den echten Scraper-Pfad.

## Entscheidungen

- **Name:** aus der Ergebnisliste übernehmen (kein zusätzlicher Request
  nötig — die Liste wird schon für die Detail-Links abgerufen).
- **Kategorie:** pro echter Kategorie-ID einzeln scrapen, Kategorie aus
  dem Suchparameter übernehmen statt zufällig zu würfeln.
- **Standort:** "Einsatzort" bevorzugt geokodieren, "Adresse der
  Vermittlungsstelle" nur als Fallback, wenn kein Einsatzort angegeben
  ist.
- **Wiederholbarkeit:** ein Kommandozeilen-Flag (`--scrape`) beim
  Programmstart löst einen einmaligen Scraper-Lauf aus — kein Endpunkt,
  keine neue Rolle, aber wiederholbar ohne Quellcode-Änderung.
- **Höflichkeitspause:** kurze Verzögerung zwischen Detailseiten-Abrufen
  (bisher gab es nur beim Geokodieren eine Pause, nicht beim Scrapen
  selbst).

## Architektur & Datenfluss

**`Scraper.kt` — Ergebnisliste liefert Name direkt mit:**
`scrapeWithLimit` sammelt aktuell nur `href`-Werte über den Selektor
`a.btn.btn-primary`. Der Selektor wechselt zu `div.views-field-title a`
(ein `<a>`-Element pro Ergebniszeile, das sowohl den sichtbaren Titel als
Text als auch die Detail-URL als `href` trägt — ein einziges
Jsoup-`select()` liefert beides). Für jedes gefundene Element werden
`element.text()` (Name) und `element.attr("href")` (relative URL) als
Paar gesammelt und pro Treffer an `scrapeEhrenamtDetails` durchgereicht,
statt wie bisher nur die URL. `scrapeEhrenamtDetails`/
`buildActivityFromDocument` bekommen den Namen als Parameter und
verwenden ihn direkt als `name`-Wert, statt ihn selbst aus
`data["Projektname"]` zu lesen (das Feld existiert auf der Detailseite
nicht und lieferte bisher immer den Fallback `"Unbekannt"`).
`scrapeWithLimit`/`scrapeWebsite` selbst (Pagination über `page=`,
`limit`-Zählung, `scrapeWithLimit`-innere Schleife) bleiben strukturell
unverändert — nur der Auswahl-Selektor und die pro Treffer
mitgelieferten Daten ändern sich.

**`Scraper.kt` — Kategorie-Iteration:**
Ein neues, festes `Map<Int, String>` mit allen 17 Kategorie-IDs → echten
Kategorienamen (aus der Analyse):

```
476 → "Bildung"
517 → "Familie & Nachbarschaft"
302 → "Flüchtlingshilfe"
310 → "Hausaufgabenbetreuung"
468 → "Kultur"
518 → "Leben im Alter"
516 → "LGBTQ"
275 → "Obdachlosigkeit"
251 → "Patenschaften"
464 → "Soziales"
467 → "Sport und Bewegung"
425 → "Tierhilfe"
303 → "Übersetzen / Dolmetschen"
475 → "Umwelt, Natur und Tierschutz"
276 → "Vereinsarbeit"
382 → "Verkauf"
```

Eine neue Methode `scrapeAllCategories(limitPerCategory: Int)` iteriert
über diese Map und ruft für jede Kategorie die bestehende
`scrapeWebsite(url, pageString, limit)` mit der auf diese Kategorie-ID
zugeschnittenen Ergebnis-URL auf (`area_of_activity=<id>` statt `All`,
sonst identisch zur bisherigen URL-Struktur inkl. `page=1`), damit die
bestehende Pagination-Logik unverändert wiederverwendet wird.
`scrapeWebsite`/`scrapeWithLimit` bekommen dafür einen zusätzlichen
`category: String`-Parameter, den sie unverändert an jeden
`scrapeEhrenamtDetails`-Aufruf innerhalb des Laufs durchreichen — die
Kategorie wird so direkt aus dem Aufrufkontext übernommen, kein
Feld-Parsing, kein Zufall mehr nötig.

**`Scraper.kt` — Einsatzort bevorzugen:**
In `buildActivityFromDocument` wird die zu geokodierende Adresse so
bestimmt: `data["Einsatzort"] ?: data["Adresse der Vermittlungsstelle"]`.
`addressText` im gespeicherten `VolunteerActivity` verwendet denselben
Wert (die im Frontend angezeigte Adresse soll zum geokodierten Punkt
passen).

**`Scraper.kt` — Pause zwischen Detailseiten-Abrufen:**
`scrapeEhrenamtDetails` bekommt vor dem Rückgabewert eine kurze
`Thread.sleep(500)` nach dem `getDocument(url)`-Aufruf, analog zur
bestehenden Geokodierungs-Pause in `GeocodingService`, aber kürzer (kein
strenges externes Rate-Limit dokumentiert, reine Höflichkeit gegenüber
der Stadt-Webseite).

**`VoloMapApp.kt` — `--scrape`-Flag:**
`main(args: Array<String>)` prüft `args.contains("--scrape")`; ist das
Flag gesetzt, wird `scraper.scrapeAllCategories(...)` mit einem
sinnvollen Limit pro Kategorie aufgerufen (z. B. 20), statt des
bisherigen auskommentierten Blocks. Ohne das Flag verhält sich der
Start wie bisher (nur `fakeScraper` bei leerer Datenbank). Aufruf:
`./gradlew.bat bootRun --args='--scrape'`.

## Fehlerbehandlung

- Fehlt sowohl "Einsatzort" als auch "Adresse der Vermittlungsstelle"
  auf einer Detailseite: keine Geokodierung, Aktivität wird ohne
  Koordinaten gespeichert (bestehendes Verhalten, unverändert).
- Eine einzelne Kategorie-Seite liefert einen Fehler/ist nicht erreichbar:
  wird geloggt (bestehendes Verhalten), Iteration über die übrigen
  Kategorien läuft weiter — ein Fehler bei "Bildung" darf nicht
  verhindern, dass "Umwelt" gescraped wird.
- Bereits über `sourceUrl` bekannte Angebote werden weiterhin übersprungen
  (bestehendes `existsBySourceUrl`-Verhalten, unverändert) — betrifft
  auch den Fall, dass dasselbe Angebot in mehreren Kategorien auftaucht.

## Tests

- Backend: `buildActivityFromDocument` bekommt jetzt `name` und
  `category` als Parameter statt sie selbst zu parsen — Tests prüfen,
  dass beide Werte unverändert durchgereicht werden und dass
  `Einsatzort` gegenüber `Adresse der Vermittlungsstelle` bevorzugt wird
  (Test mit beiden Feldern gesetzt, Test mit nur "Adresse der
  Vermittlungsstelle"). Bestehender `ScraperTest` (dateTime bleibt
  `null`) bleibt gültig, Signaturänderung wird nachgezogen.
- Manuell: `--scrape`-Flag einmal gegen die echte Seite laufen lassen,
  Stichprobe der importierten Aktivitäten prüfen — echte Namen (kein
  "Unbekannt" mehr), echte Kategorien passend zur Such-URL, Koordinaten
  entsprechen dem Einsatzort (soweit ohne Vor-Ort-Kenntnis verifizierbar,
  zumindest nicht mehr alle identisch auf einer Bürofassade).
