# Bewertungssystem — Design

**Datum:** 2026-08-12
**Status:** Approved, bereit für Implementierungsplanung

## Ziel

User (Rolle `USER`) sollen sowohl einzelne Aktivitäten als auch Anbieter als
Ganzes bewerten können (Sterne 1-5 + optionaler Kommentar). Bewertungen sind
für alle sichtbar, auch ausgeloggt — nur das Abgeben einer Bewertung
erfordert Login als `USER`.

## Nicht-Ziele

- Teilnahme-Nachweis vor dem Bewerten (kein RSVP/Anmelde-System vorhanden;
  Missbrauch wird für jetzt bewusst nicht verhindert, siehe Entscheidung
  unten)
- Anbieter-Profilseite (Anbieter-Bewertungen erscheinen inline überall, wo
  der Anbieter auftaucht, keine eigene Seite)
- Melden/Moderieren einzelner Bewertungen
- Sortierung/Filterung nach Bewertung in der bestehenden Filterleiste
- Anbieter können nicht bewerten (weder Aktivitäten noch andere Anbieter)

## Entscheidungen

- **Beide Ziele bewertbar:** einzelne Aktivitäten UND Anbieter als Ganzes,
  unabhängig voneinander — eine Aktivitäts-Bewertung erzeugt keine
  automatische Anbieter-Bewertung.
- **Format:** Sterne (1-5) + optionaler Text-Kommentar.
- **Kein Teilnahme-Nachweis fürs MVP.** Jeder eingeloggte `USER` kann jede
  Aktivität/jeden Anbieter bewerten. Genau eine Bewertung pro
  User+Ziel-Kombination (DB-Unique-Constraint) verhindert Mehrfach-Spam.
  Weitergehender Missbrauchsschutz (z.B. Melden) ist bewusst
  ausgeklammert — kleinerer Umfang fürs Erste, Moderation kann später
  nachgerüstet werden.
- **Nur `USER` darf bewerten**, nicht `ANBIETER`.
- **Lesen ist immer öffentlich**, auch ausgeloggt — nur das Abgeben einer
  Bewertung erfordert Login.
- **Erneutes Bewerten aktualisiert die bestehende Bewertung** (Upsert), statt
  abgelehnt zu werden — Meinungen können sich ändern.
- **Anbieter-Bewertungen erscheinen inline**, wo immer der Anbieter im
  Frontend auftaucht (aktuell: die `VolunteerList`-Karte) — keine eigene
  Profilseite.

## Datenmodell

Zwei neue, unabhängige Entitäten:

`ActivityRating`:
- `id` (PK, auto-generated)
- `user` (FK → `User`)
- `activity` (FK → `VolunteerActivity`)
- `stars` (Int, 1-5)
- `comment` (String?, optional)
- `createdAt`

`ProviderRating`:
- `id` (PK, auto-generated)
- `user` (FK → `User`)
- `provider` (FK → `User`, muss Rolle `ANBIETER` haben)
- `stars` (Int, 1-5)
- `comment` (String?, optional)
- `createdAt`

Beide mit DB-Unique-Constraint auf `(user, activity)` bzw. `(user,
provider)` — erzwingt "eine Bewertung pro User+Ziel" auf Datenbankebene,
nicht nur in der Anwendungslogik.

## Backend

Neue Endpunkte:
- `POST /activities/{id}/ratings` — Body `{stars, comment?}`. Legt die
  Bewertung des eingeloggten Users für diese Aktivität an oder aktualisiert
  sie (Upsert über den Unique-Constraint). Erfordert Rolle `USER`.
- `GET /activities/{id}/ratings` — Liste aller Bewertungen (mit
  Nutzername, Sterne, Kommentar, Datum) + Durchschnitt. Öffentlich.
- `POST /providers/{id}/ratings` — analog für Anbieter-Bewertungen.
  Erfordert Rolle `USER`. `{id}` muss ein existierender User mit Rolle
  `ANBIETER` sein (sonst 404).
- `GET /providers/{id}/ratings` — analog für Anbieter. Öffentlich.

Die bestehende `Marker`-DTO (aus `GET /markers`) bekommt vier zusätzliche
Felder, damit die Karten-Liste Durchschnitte ohne Extra-Request pro Karte
anzeigen kann:
- `activityRating: Double?`, `activityRatingCount: Int`
- `providerRating: Double?`, `providerRatingCount: Int`

(`null`/`0`, wenn noch keine Bewertungen vorliegen.)

Autorisierung: Die beiden `POST`-Endpunkte erfordern eingeloggten `USER`
(403 für `ANBIETER` oder falsche Rolle, 401 wenn gar nicht eingeloggt,
analog zum bestehenden `/add`-Muster). Die beiden `GET`-Endpunkte sind
öffentlich, analog zu `/markers`/`/categories`.

## Frontend

Ein neues `RatingModal.svelte` (wiederverwendbar für Aktivität und
Anbieter über einen `target: "activity" | "provider"`-Prop). Öffnet sich
durch Klick auf die Sterne-Anzeige in der `VolunteerList`-Karte — für
**alle** Besucher, auch ausgeloggt, und zeigt dann die vollständige
Kommentar-Liste (per `GET`).

Innerhalb des Modals: das Eingabeformular (Sterne + Kommentar abgeben)
erscheint nur, wenn ein `USER` eingeloggt ist. Für ausgeloggte Besucher
oder eingeloggte `ANBIETER` erscheint stattdessen ein Hinweistext mit
Links zu `/login`/`/register`, analog zum bestehenden `/add`-Gate in
`AddActivity.svelte`. Hat der eingeloggte `USER` für dieses Ziel bereits
bewertet, ist das Formular mit der bestehenden Bewertung vorausgefüllt
(Editier-Fall); Speichern ruft denselben `POST`-Endpunkt erneut auf
(Upsert).

Die `VolunteerList`-Karte zeigt zusätzlich zum bestehenden Kategorie-Tag
zwei Sterne-Zeilen: Durchschnitt + Anzahl für die Aktivität und für den
Anbieter (z.B. "★ 4.2 (7)"), beide klickbar zum Öffnen des jeweiligen
`RatingModal`.

## Fehlerbehandlung

- `POST` ohne Login → 401; `POST` als `ANBIETER` → 403 — Frontend zeigt in
  beiden Fällen den Login/Registrieren-Hinweis statt des Formulars (siehe
  oben)
- `POST /providers/{id}/ratings` mit `{id}`, das kein Anbieter ist → 404
- `stars` außerhalb 1-5 → 400 (Server-seitige Validierung, analog zur
  Registrierungs-Validierung aus dem Login-Feature)

## Tests

- Backend: Bewertung anlegen (Aktivität/Anbieter), Bewertung aktualisieren
  (Upsert-Verhalten), Zugriff ohne Login (401), als `ANBIETER` (403),
  ungültige Sterne-Zahl (400), `GET` ohne Login funktioniert (öffentlich),
  Durchschnitts-/Zählwerte in `/markers` korrekt berechnet
- Frontend: manuelle Sichtprüfung (kein Test-Framework im Projekt, siehe
  bestehende Konvention)
