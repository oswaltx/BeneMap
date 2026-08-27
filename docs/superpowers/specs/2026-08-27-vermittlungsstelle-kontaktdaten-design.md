# Vermittlungsstelle-Kontaktdaten — Design

**Datum:** 2026-08-27
**Status:** Approved, bereit für Implementierungsplanung

## Ziel

Die Detailseiten der Kölner Engagementdatenbank enthalten neben Name,
Kategorie, Einsatzort und Beschreibung auch Kontaktdaten der
Vermittlungsstelle (des vermittelnden Vereins/der Organisation): Name,
Homepage, E-Mail-Adresse und Telefonnummer. Der Scraper liest diese
Felder heute schon generisch aus (sichtbar in den Scraper-Logs als
"Gefundene Felder"), verwirft sie aber wieder — nur Beschreibung und
Adresse werden tatsächlich gespeichert. Ziel ist, diese vier
Kontaktfelder mitzuspeichern und im Detail-Panel der App anzuzeigen, damit
Nutzer:innen die Vermittlungsstelle direkt kontaktieren können, ohne über
den Link zur Stadt-Köln-Seite navigieren zu müssen.

## Befund (an der echten Seite verifiziert)

Die generische Feld-Extraktion in `buildActivityFromDocument`
(`document.select("div.field")`, Label aus `div.field__label`, Wert aus
`div.field__item`) liest die folgenden vier relevanten Felder bereits
korrekt in die `data`-Map ein:

- `Name der Vermittlungsstelle` — reiner Text (kein Link).
- `Homepage der Vermittlungsstelle` — die bestehende Sonderbehandlung für
  `href.startsWith("http")` liefert hier bereits die reine URL statt des
  Linktexts.
- `E-Mail der Vermittlungsstelle` — die bestehende Sonderbehandlung für
  `href.startsWith("mailto:")` liefert hier bereits die reine
  E-Mail-Adresse (nicht `mailto:...`).
- `Telefonnummer der Vermittlungsstelle` — reiner Text (kein Link, daher
  greift `item.text()`, der generische Fallback-Zweig). Nicht bei jedem
  Angebot vorhanden.

Es ist kein neuer Parsing-Code nötig — die vier Werte müssen nur
zusätzlich aus der bereits befüllten `data`-Map in die gespeicherte
`VolunteerActivity` übernommen werden.

Das ebenfalls vorhandene Feld `Befristet` (zeitlich begrenztes Projekt)
ist keine Kontaktinformation und bleibt außen vor.

## Nicht-Ziele

- Keine rückwirkende Aktualisierung bereits importierter Angebote über
  Code — die bestehende `existsBySourceUrl`-Dedup-Regel ("einmal
  importiert, nie wieder angefasst") bleibt unverändert. Die aktuell 34
  bereits importierten Aktivitäten werden stattdessen einmalig manuell
  gelöscht und per `--scrape` neu importiert (siehe Rollout unten) — kein
  Sonderfall im Code.
- Keine Validierung/Normalisierung der Kontaktdaten (z. B. Telefonnummern-
  Formatierung, E-Mail-Syntaxprüfung) — die Werte werden unverändert von
  der Quelle übernommen, wie bei allen anderen gescrapten Feldern auch.
- Keine Bewertungs- oder Profilfunktion für Vermittlungsstellen (im
  Gegensatz zum bestehenden Anbieter-Block mit Rating/Avatar) — eine
  Vermittlungsstelle ist kein App-Nutzerkonto.

## Entscheidungen

- **Felder:** Name, Homepage, E-Mail, Telefonnummer der Vermittlungsstelle
  — alle vier, jedes einzeln optional.
- **Datenmodell:** Vier neue nullable Spalten auf `VolunteerActivity`:
  `sourceContactName`, `sourceContactWebsite`, `sourceContactEmail`,
  `sourceContactPhone`. Bewusst nicht die Namen `providerName`/
  `providerWebsiteUrl` wiederverwendet, da diese semantisch an ein
  App-`User`-Konto (mit Rating, Avatar) gebunden sind — eine
  Vermittlungsstelle ist das nicht.
- **Darstellung:** Eigener Block im Detail-Panel, stilistisch am
  bestehenden Anbieter-Block orientiert (Trennlinie, kleine Schrift):
  Name fett, darunter bedingt Homepage-Link, E-Mail-Link, Telefonnummer.
  Der bestehende "Mehr Infos auf der Webseite der Stadt Köln"-Link
  (verweist auf die einzelne Angebots-Seite, aus `sourceUrl`) bleibt
  unverändert bestehen — die neue Homepage verweist auf die Seite der
  Organisation selbst, ein anderer Link mit anderem Zweck.
- **Bestandsdaten:** Einmaliger manueller Reset (löschen + neu scrapen)
  statt Code-Sonderfall, siehe Nicht-Ziele.

## Architektur & Datenfluss

**`VolunteerActivity.kt`:** Vier neue Felder, alle
`String? = null`, direkt neben `sourceUrl` platziert:

```kotlin
var sourceContactName: String? = null,
var sourceContactWebsite: String? = null,
var sourceContactEmail: String? = null,
var sourceContactPhone: String? = null,
```

**`Scraper.kt` — `buildActivityFromDocument`:** Die vier Werte werden aus
der bereits befüllten `data`-Map gelesen und in den
`VolunteerActivity`-Konstruktor-Aufruf übernommen:

```kotlin
sourceContactName = data["Name der Vermittlungsstelle"],
sourceContactWebsite = data["Homepage der Vermittlungsstelle"],
sourceContactEmail = data["E-Mail der Vermittlungsstelle"],
sourceContactPhone = data["Telefonnummer der Vermittlungsstelle"],
```

**`Marker.kt`:** Vier neue nullable `String`-Felder analog zu
`sourceUrl`.

**`MainController.kt` — `markers()`:** Die vier Felder werden von
`activity` auf das `Marker`-DTO durchgereicht, analog zu
`sourceUrl = activity.sourceUrl`.

**`PinDetailPanel.svelte`:** Der `marker`-Typ bekommt die vier neuen
optionalen Felder. Neuer Block nach dem bestehenden
`.source-link`-Element, vor dem `.rating-badge`:

```svelte
{#if marker.sourceContactName}
    <div class="source-contact">
        <span class="source-contact-name">{marker.sourceContactName}</span>
        {#if marker.sourceContactWebsite}
            <a href={marker.sourceContactWebsite} target="_blank" rel="noopener noreferrer">Website besuchen</a>
        {/if}
        {#if marker.sourceContactEmail}
            <a href={`mailto:${marker.sourceContactEmail}`}>{marker.sourceContactEmail}</a>
        {/if}
        {#if marker.sourceContactPhone}
            <span class="source-contact-phone">{marker.sourceContactPhone}</span>
        {/if}
    </div>
{/if}
```

Styling analog zu `.provider`/`.provider-name`/`.provider-website`
(gleiche Farbvariablen, gleicher Trennlinien-Stil), eigene CSS-Klassen
(`.source-contact`, `.source-contact-name`, `.source-contact-phone`), um
keine Kopplung an die Anbieter-Styles einzugehen, die künftig unabhängig
weiterentwickelt werden könnten.

## Fehlerbehandlung

- Jedes der vier Felder ist unabhängig optional — fehlt z. B. die
  Telefonnummer auf der Detailseite, ist `sourceContactPhone` `null` und
  die entsprechende Zeile wird im Frontend nicht gerendert (gleiches
  Muster wie das bestehende `{#if marker.dateTime}`).
- Fehlt `Name der Vermittlungsstelle` komplett (bisher auf keiner
  gesehenen Seite beobachtet, aber nicht ausgeschlossen), wird der
  gesamte Block nicht angezeigt, auch wenn z. B. eine Homepage vorhanden
  wäre — ein Kontaktblock ohne erkennbaren Namen wäre wenig hilfreich.

## Tests

- Backend: `ScraperTest` um einen Fall erweitert, der eine Detailseite
  mit allen vier Vermittlungsstelle-Feldern (inkl. `mailto:`- und
  `http`-Links) simuliert und prüft, dass alle vier Werte korrekt in die
  gebaute `VolunteerActivity` übernommen werden. Ein zweiter Fall prüft,
  dass eine fehlende Telefonnummer zu `sourceContactPhone == null` führt,
  ohne die anderen drei Felder zu beeinträchtigen.
- Manuell: Nach dem Rollout (Reset + Re-Scrape) im Browser prüfen, dass
  der neue Kontakt-Block bei mehreren echten Aktivitäten korrekt
  erscheint (inkl. Fall ohne Telefonnummer) und die Links funktionieren
  (Homepage öffnet in neuem Tab, E-Mail öffnet Mail-Client).

## Rollout

Nach Merge: die 34 bereits importierten Städtische-Angebote-Aktivitäten
aus der lokalen Dev-Datenbank löschen (`source_url IS NOT NULL`) und
`--scrape` erneut laufen lassen, damit sie die neuen Kontaktfelder
bekommen.
