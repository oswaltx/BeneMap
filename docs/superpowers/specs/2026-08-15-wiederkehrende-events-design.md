# Wiederkehrende Events — Design

**Datum:** 2026-08-15
**Status:** Approved, bereit für Implementierungsplanung

## Ziel

Anbieter können beim Anlegen einer Aktivität ein Wiederholungsmuster
angeben (z. B. "alle 2 Wochen"), sodass nicht jeder einzelne Termin einer
regelmäßigen Aktivität (Sprachcafé, Kleiderkammer, Hausaufgabenhilfe)
manuell separat angelegt werden muss.

## Nicht-Ziele

- Kein volles Regelwerk (keine Wochentags-Auswahl, kein Enddatum-Feld,
  keine Ausnahmen/Feiertage) — nur ein festes Intervall in Tagen/Wochen ab
  dem Startdatum.
- Kein Serien-Konzept in der Bedienung: keine "ganze Serie löschen"-Aktion,
  keine gemeinsame Serien-Kennung in der Datenbank. Jeder erzeugte Termin
  ist danach eine ganz normale, unabhängige Aktivität — bearbeitbar und
  löschbar über die bestehenden Edit/Delete-Mechanismen, ohne
  Sonderbehandlung.
- Kein Wiederholungs-UI in `EditActivityModal` — Wiederholung wird nur
  beim erstmaligen Anlegen konfiguriert, nicht nachträglich beim
  Bearbeiten eines einzelnen bereits erzeugten Termins.
- Keine nutzerseitige Angabe, wie viele Termine oder bis wann erzeugt
  werden — fester Zeitraum von 3 Monaten ab dem Startdatum, serverseitig
  zusätzlich auf maximal 60 Termine gedeckelt.

## Entscheidungen

- **Muster: flexibles Intervall in Tagen oder Wochen** ("alle N
  Tage/Wochen"), kein festes wöchentlich/monatlich-Duo und kein volles
  Regelwerk.
- **Anzeige: ein echter Aktivitäts-Eintrag pro Termin**, automatisch beim
  Anlegen generiert — keine Karten-/Listen-Sonderbehandlung, jeder Termin
  erscheint wie jede andere Aktivität einzeln filterbar.
- **Löschen/Bearbeiten: nur einzeln**, kein Serien-Bulk-Delete.
- **Horizont: fester Zeitraum von 3 Monaten** ab dem Startdatum,
  serverseitig zusätzlich auf 60 Termine gedeckelt (gegen Missbrauch,
  analog zum bestehenden `MAX_PHOTO_URLS`-Muster).

## Architektur & Datenfluss

**Backend — neuer Endpunkt `POST /add-recurring`** (Rolle `ANBIETER`,
identische Sicherheitsregel wie das bestehende `POST /add`). Eigenes
Request-DTO `AddRecurringActivityRequest` mit denselben Feldern wie das
Anlegen einer einzelnen Aktivität (`name`, `description`, `addressText`,
`category`, `dateTime`, `photoUrls`) plus `recurrenceIntervalDays: Int`.
Das Frontend rechnet die gewählte Einheit ("Wochen") vor dem Senden in
Tage um — das Backend kennt nur einen Tages-Wert, keine Einheiten-Logik.

Ablauf im Controller:
1. `recurrenceIntervalDays < 1` → `400 Bad Request`.
2. Adresse **einmal** geokodieren (nicht pro Termin) — wichtig, da jede
   Geokodierung wegen des Nominatim-Rate-Limits ~1,1 s dauert
   (`GeocodingService.geocode`); bei z. B. 12 Terminen wären das sonst
   über 13 s Wartezeit für eine einzige Anfrage. Alle erzeugten Termine
   teilen sich dieselben Koordinaten, wie es der bestehenden
   "mehrere Aktivitäten pro Ort"-Logik ohnehin entspricht (identische
   Adresse → identische Koordinaten).
3. Anzahl der zu erzeugenden Termine berechnen: so viele, wie ins
   3-Monats-Fenster ab dem Startdatum passen, gedeckelt auf maximal 60.
4. Für jeden Termin einen eigenen `VolunteerActivity`-Datensatz anlegen
   (`dateTime = startDateTime + i * recurrenceIntervalDays Tage`),
   `createdBy` wie beim bestehenden `/add` auf den eingeloggten Anbieter
   gesetzt, `photoUrls` normalisiert wie beim bestehenden `/add`.
5. Response: `List<VolunteerActivity>` — die Liste aller erzeugten
   Termine (das Frontend braucht nur die Länge für die Erfolgsmeldung).

Kein neues Datenbankfeld, keine Serien-Kennung — die erzeugten Zeilen sind
strukturell nicht von manuell einzeln angelegten Aktivitäten zu
unterscheiden.

**Frontend — `AddActivity.svelte`:** neue Checkbox "Wiederholt sich".
Aktiviert, erscheinen zwei zusätzliche Felder: eine Zahl (`recurrenceCount:
number`, Default z. B. `1`) und eine Einheit-Auswahl (`recurrenceUnit:
"days" | "weeks"`, Default `"weeks"`). Beim Absenden: ist die Checkbox
aktiv, wird `POST /add-recurring` mit
`recurrenceIntervalDays = recurrenceUnit === "weeks" ? recurrenceCount * 7
: recurrenceCount` aufgerufen statt `POST /add`; die restlichen Felder
werden unverändert mitgeschickt. Erfolgsmeldung zeigt die Anzahl erzeugter
Termine (`"${count} Termine wurden angelegt."`).

**Keine Änderung an `EditActivityModal.svelte`, `PinDetailPanel.svelte`,
`VolunteerList.svelte`, `Map.svelte`** — jeder erzeugte Termin fließt
durch die bestehenden Anzeige-/Bearbeitungspfade, ohne dass diese von der
Wiederholung wissen müssen.

## Fehlerbehandlung

- `recurrenceIntervalDays < 1`: `400 Bad Request`, Formular zeigt
  Fehlermeldung, kein Request wird gesendet, wenn das Frontend das schon
  clientseitig validiert (Zahl-Feld mit `min="1"`).
- Geokodierung schlägt fehl: Termine werden trotzdem angelegt (ohne
  Koordinaten, erscheinen nicht auf der Karte) — gleiches Verhalten wie
  beim bestehenden Einzel-Anlegen. Erfolgsmeldung im Frontend wird
  entsprechend angepasst ("Termine wurden gespeichert — die Adresse
  konnte aber nicht gefunden werden, sie erscheinen noch nicht auf der
  Karte."), analog zur bestehenden Meldung bei `POST /add`.
- Mehr als 60 Termine würden ins 3-Monats-Fenster passen (z. B. "alle 1
  Tag"): serverseitig auf 60 gekappt, kein Fehler, keine Frontend-Warnung
  (bewusst einfach gehalten für MVP, analog zur bestehenden
  Photo-URL-Kappung).

## Tests

- Backend: `recurrenceIntervalDays`-Validierung (< 1 → 400); Anzahl
  erzeugter Termine bei verschiedenen Intervallen korrekt berechnet
  (3-Monats-Fenster, 60er-Deckel greift bei kleinem Intervall); alle
  erzeugten Termine teilen dieselben Koordinaten; Geokodierung wird nur
  einmal aufgerufen (nicht pro Termin); `createdBy` korrekt gesetzt;
  unauthentifizierter/Nicht-Anbieter-Zugriff liefert 401/403 wie bei
  `/add`.
- Frontend: manuelle Sichtprüfung (kein Test-Framework im Projekt, siehe
  bestehende Konvention). Manuell zu prüfen: Checkbox blendet
  Intervall-Felder ein/aus; Absenden mit aktivierter Wiederholung erzeugt
  mehrere Termine, die einzeln in Karte/Liste erscheinen; jeder erzeugte
  Termin ist normal einzeln bearbeitbar/löschbar; Absenden ohne
  Wiederholung verhält sich exakt wie bisher (`POST /add` unverändert).
