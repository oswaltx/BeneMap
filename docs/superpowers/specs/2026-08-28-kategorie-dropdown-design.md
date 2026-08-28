# Kategorie-Dropdown für app-native Aktivitäten — Design

## Ausgangslage

Das Kategoriefeld beim Anlegen/Bearbeiten einer Aktivität ist aktuell ein
reines Freitextfeld (`AddActivity.svelte`, `EditActivityModal.svelte`),
das Anbieter selbst befüllen. Das führt zu uneinheitlichen Werten
(Tippfehler, Synonyme, Kurzformen wie das beobachtete "Ehre" für ein
Testdaten-Beispiel). Gescrapte Städtische Angebote haben dieses Problem
nicht: der Scraper ordnet sie bereits einer festen 15er-Kategorienliste
zu, die aus den Kategorie-IDs der Kölner Engagementdatenbank stammt
(`Scraper.kt`, `ENGAGEMENT_CATEGORIES`).

## Ziel

App-native Aktivitäten wählen ihre Kategorie aus einer festen Liste statt
sie frei einzutippen, um konsistente Werte für Filter und Darstellung zu
gewährleisten.

## Kategorienliste

Feste Liste mit 16 Werten: die 15 bestehenden Scraper-Kategorien
(Bildung, Familie & Nachbarschaft, Flüchtlingshilfe,
Hausaufgabenbetreuung, Kultur, Leben im Alter, LGBTQ, Obdachlosigkeit,
Patenschaften, Soziales, Sport und Bewegung, Tierhilfe,
Übersetzen / Dolmetschen, Umwelt, Natur und Tierschutz, Vereinsarbeit,
Verkauf) plus neu **"Sonstiges"** für nicht zuordbare Angebote.

Die Liste wird an zwei Stellen gepflegt, nicht über einen gemeinsamen
Endpoint synchronisiert:

- Backend: eine neue Kotlin-Konstante (Liste der 16 Namen), auf die auch
  `Scraper.kt` für seine bestehenden 15 Namen verweist, damit die beiden
  nicht auseinanderlaufen.
- Frontend: eine identische TypeScript-Konstante (gleiche Reihenfolge,
  gleiche Schreibweise), die beide Formulare importieren.

Ein eigener Endpoint wäre für eine derart selten wechselnde, statische
16-Werte-Liste unnötiger Aufwand (Fetch, Ladezustand, Fehlerfall) und
steht in keinem Verhältnis zum Nutzen. Ändert sich die Liste künftig,
werden beide Stellen manuell angepasst — bei der Größe der App
vertretbar.

"Sonstiges" wird nie vom Scraper vergeben (der nutzt immer echte
Kategorienamen von der Stadt-Köln-Seite), nur Anbieter können es wählen.

## Frontend-Änderungen

`AddActivity.svelte` und `EditActivityModal.svelte`: Das
`<input type="text">` für die Kategorie wird durch ein `<select>`
ersetzt, gefüllt aus der festen Liste, mit einer leeren
"– bitte wählen –"-Standardoption. Das Kategoriefeld bleibt wie bisher
optional (keine Pflichtauswahl, `null` weiterhin ein gültiger Wert).

Keine Änderung an `FilterBar.svelte` oder `categoryColor.ts` — der Filter
zeigt weiterhin dynamisch nur tatsächlich vorkommende Kategoriewerte an,
und die Farbzuordnung ist bereits hash-basiert und funktioniert mit
jedem String.

## Backend-Änderungen

Keine Schemaänderung — `category` bleibt `String?` auf
`VolunteerActivity`. Keine serverseitige Validierung gegen die feste
Liste: das Feld bleibt so vertrauensvoll behandelt wie andere optionale
Felder, eine Enum-Prüfung wäre hier keine echte Sicherheitsgrenze
(authentifizierte Anbieter könnten ohnehin über die rohe API beliebige
Werte senden) und brächte für den normalen Nutzungsfluss über die neue
Dropdown-UI keinen Mehrwert.

## Aufräumen bestehender Daten

Vor bzw. bei der Umsetzung werden alle app-nativen (nicht von der Stadt
Köln gescrapten) Aktivitäten aus der DB gelöscht — es sind ausschließlich
Testdaten, u.a. die Aktivität mit der Kategorie "Ehre". Eine
Migrationslogik für bestehende Freitext-Kategoriewerte ist damit nicht
nötig: neue app-native Aktivitäten entstehen ab sofort ausschließlich
über das Dropdown, gescrapte Aktivitäten hatten schon immer nur Werte aus
der festen Liste.

## Testing

- Backend: bestehende Tests, die `category` als Freitext setzen, bleiben
  gültig (Feldtyp ändert sich nicht) — kein neuer Backend-Test nötig, da
  keine neue Logik entsteht.
- Frontend: manuelle Verifikation im Browser, dass beide Formulare das
  Dropdown korrekt anzeigen, "Sonstiges" auswählbar ist, die leere Option
  weiterhin `null` sendet, und `EditActivityModal` die aktuelle Kategorie
  einer bestehenden Aktivität korrekt vorauswählt.
