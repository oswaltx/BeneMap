# Mehrere Aktivitäten an einem Ort — Design

**Datum:** 2026-08-15
**Status:** Approved, bereit für Implementierungsplanung

## Ziel

Wenn mehrere Aktivitäten exakt dieselbe Adresse (und damit dieselben
geokodierten Koordinaten) haben, liegen ihre Kartenpunkte heute exakt
übereinander. Nur der optisch oberste Punkt ist klickbar, die übrigen sind
auf der Karte faktisch unerreichbar (in der Liste unten bleiben sie zwar
sichtbar, aber der Zusammenhang "mehrere Aktivitäten, ein Ort" ist auf der
Karte selbst nicht erkennbar). Ziel ist ein eigener, klar erkennbarer
Kartenpunkt für solche Orte, der alle dortigen Aktivitäten zugänglich macht.

## Nicht-Ziele

- Kein echtes Marker-Clustering für nah beieinander liegende, aber
  unterschiedliche Orte (z. B. beim Rauszoomen) — betrifft nur exakt
  gleiche Koordinaten. Das bestehende Verhalten für alle anderen Punkte
  bleibt unverändert.
- Keine Änderung an `VolunteerList`/Bottom-Sheet — dort werden Aktivitäten
  bereits einzeln und ohne Overlap-Problem aufgelistet.
- Keine Änderung an `PinDetailPanel` — die Detailansicht einer einzelnen
  Aktivität bleibt exakt wie sie ist, sie wird nur über einen zusätzlichen
  Zwischenschritt erreicht.
- Keine Backend-Änderung — alle nötigen Felder (`lat`, `lng`, `name`,
  `category`, `dateTime`, `address`) sind bereits Teil von `Marker`.

## Entscheidungen

- **Gruppierung:** clientseitig in `Map.svelte`, nach Koordinaten gerundet
  auf 5 Nachkommastellen (~1 m Genauigkeit) — nicht nach dem Adress-String,
  da die tatsächliche Kartenposition (und damit das Overlap) von den
  Koordinaten abhängt.
- **Cluster-Pin-Design:** Größerer Ring in Primärfarbe mit Akzent-Rand und
  der Anzahl der Aktivitäten mittig (Option "C" aus dem visuellen
  Vergleich) — deutlich unterscheidbar von normalen Einzel-Pins, bleibt
  aber in der bestehenden Farbwelt.
- **Interaktion:** Hover öffnet ein interaktives Popup mit der Liste aller
  Aktivitäten an diesem Ort (Option "B" aus dem visuellen Vergleich); Klick
  auf eine Zeile öffnet die normale Detailansicht. Ein direkter Klick/Tap
  auf den Pin selbst öffnet dasselbe Popup, ohne vorheriges Hover — das
  deckt Touch-Geräte ab, auf denen es keinen Hover-Zustand gibt.
- **Einzel-Punkte unverändert:** Orte mit genau einer Aktivität behalten
  exakt das heutige Verhalten (einfacher `CircleMarker` mit dem
  bestehenden Hover-Tooltip).

## Architektur & Datenfluss

**Gruppierung (`Map.svelte`):** eine reaktive Ableitung
`$: markerGroups = groupByLocation(markers)` gruppiert `markers` nach
`` `${lat.toFixed(5)},${lng.toFixed(5)}` ``. Jede Gruppe hat eine Referenz-
Koordinate (die des ersten Eintrags) und ihre Mitglieder, absteigend nach
`dateTime` unrelevant — innerhalb der Gruppe aufsteigend nach `dateTime`
sortiert (nächster Termin zuerst).

**Rendering:**
- Gruppen mit `members.length === 1`: identisches Rendering wie heute —
  `CircleMarker` + der bestehende Hover-`Tooltip` mit Name, Kategorie,
  Datum, Bewertung. Keine Code-Änderung an diesem Zweig nötig, nur die
  Iterationsquelle ändert sich von `markers` auf die Einzel-Gruppen.
- Gruppen mit `members.length > 1`: statt `CircleMarker` (reines SVG, kein
  Text-Inhalt möglich) ein `Marker` mit `DivIcon` (beide aus `sveaflet`) —
  die `DivIcon` rendert einen runden `div` mit der Anzahl als Text, Akzent-
  Rand, Primärfarbe (gleiches CSS wie die Mockup-Variante "C"). Dieser Pin
  bekommt statt des einfachen Tooltips ein interaktives `Popup` (sveaflet),
  das:
  - bei `mouseover` auf den Pin **und** bei `click`/`tap` auf den Pin
    geöffnet wird (`{closeButton: false, autoClose: false,
    closeOnClick: false}`, manuell über `openPopup()`/`closePopup()`
    gesteuert),
  - bei `mouseout` vom Pin **und** vom Popup-Inhalt (mit kurzer Verzögerung,
    damit die Maus dazwischen wandern kann) automatisch schließt,
  - eine Kopfzeile mit der Anzahl und der gemeinsamen Adresse zeigt sowie
    pro Aktivität eine klickbare Zeile (Name, Kategorie-Tag, Datum),
  - deren Zeilen-Klick `selectedMarkerId = activity.id` setzt und das
    Popup schließt — identischer Mechanismus wie der bestehende
    Einzel-Pin-Klick, der `PinDetailPanel` öffnet.

**Keine neuen Endpunkte, keine neuen Props für `PinDetailPanel`/
`VolunteerList`.** Die gesamte Änderung ist in `Map.svelte` gekapselt.

## Fehlerbehandlung

- Genau eine Aktivität an einer Koordinate: normales Einzel-Pin-Verhalten,
  kein Sonderfall.
- Popup bleibt offen, wenn die Maus zwischen Pin und Popup-Inhalt wechselt;
  schließt bei tatsächlichem Verlassen beider Bereiche.
- Klick auf die Karte außerhalb eines Pins schließt weiterhin (wie heute)
  ein offenes `PinDetailPanel` — betrifft das neue Popup nicht direkt, das
  bereits bei Mouseout schließt.

## Tests

- Frontend: manuelle Sichtprüfung (kein Test-Framework im Projekt, siehe
  bestehende Konvention). Manuell zu prüfen: ein Ort mit 3 Testaktivitäten
  zeigt den Cluster-Pin mit korrekter Anzahl; Hover öffnet die Liste;
  Klick auf eine Zeile öffnet die richtige Detailansicht im Panel; Tap auf
  den Pin (ohne Hover, z. B. per simuliertem Click) öffnet dieselbe Liste;
  ein Ort mit nur einer Aktivität zeigt weiterhin den normalen Einzel-Pin
  mit dem bestehenden Hover-Tooltip.
