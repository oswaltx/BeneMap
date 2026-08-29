# Konto-Selbstlöschung — Design

## Ausgangslage

Nutzer (Ehrenamtler wie Anbieter) können ihr Konto aktuell nicht selbst
löschen — die Datenschutzerklärung verweist auf Löschung per
Kontaktaufnahme. Dieses Feature führt eine echte Selbstlöschfunktion ein.
Das Aktualisieren der Datenschutzerklärung ist bewusst **nicht** Teil
dieser Spec, sondern ein eigener kleiner Folgeschritt danach.

## Betroffene Daten je Rolle

- **Anbieter (`Role.ANBIETER`):** besitzt `VolunteerActivity`-Einträge
  (`createdBy`). Jede davon hat eigene `ActivityRating`- und
  `ActivitySignup`-Einträge, die beim Löschen der Aktivität ebenfalls
  gelöscht werden müssen — genau das Muster, das
  `MainController.deleteActivity()` bereits für die manuelle Löschung
  einer einzelnen Aktivität verwendet. Zusätzlich besitzt ein Anbieter
  `ProviderRating`-Einträge, bei denen er als `provider` referenziert wird
  (Bewertungen, die andere über ihn abgegeben haben).
- **Ehrenamtler (`Role.USER`):** kann laut bestehender Rollenprüfung in
  `SecurityConfig` nur als `USER` bewerten (`POST /activities/*/ratings`,
  `/providers/*/ratings`) und sich anmelden (`POST /activities/*/signup`)
  — nie als `ANBIETER`. Ein Ehrenamtler besitzt daher eigene
  `ActivityRating`-, `ProviderRating`- und `ActivitySignup`-Einträge (als
  `user`), aber nie eigene `VolunteerActivity`-Einträge.

Beide Rollen: keine speziellen Blockaden — jedes Konto kann jederzeit
gelöscht werden.

## Backend

**`GET /auth/me/deletion-impact`** (authentifiziert, jede Rolle):
liefert `{ activityCount: Int }` — Anzahl der `VolunteerActivity`-Einträge
des aktuellen Nutzers (`VolunteerActivityRepository.findByCreatedBy`, neue
Repository-Methode). Für Ehrenamtler immer `0`. Wird vom Frontend
abgerufen, wenn der Löschen-Bereich geöffnet wird, um den Nutzer vorab
über betroffene Aktivitäten zu informieren.

**`DELETE /auth/me`** mit Body `{ password: String }` (authentifiziert,
jede Rolle):

1. Passwort erneut gegen `user.passwordHash` prüfen
   (`passwordEncoder.matches`). Bei Fehlschlag: `401` mit
   `ErrorResponse("Passwort ist falsch.")`, nichts wird gelöscht.
2. Kaskadierendes Löschen, abhängig von der Rolle:
   - **Anbieter:** für jede eigene `VolunteerActivity` — deren
     `ActivityRating`s und `ActivitySignup`s löschen, dann die Aktivität
     selbst (identisches Muster zu `MainController.deleteActivity()`).
     Danach alle `ProviderRating`s löschen, bei denen der Nutzer
     `provider` ist.
   - **Ehrenamtler:** alle eigenen `ActivityRating`s, `ProviderRating`s
     und `ActivitySignup`s löschen (neue Repository-Methoden
     `findByUser` auf allen drei Repositories).
3. Den `User`-Datensatz selbst löschen.
4. Die Session invalidieren (`SecurityContextLogoutHandler`, gleiches
   Muster wie der bestehende `POST /auth/logout`-Endpoint).
5. `204 No Content` zurückgeben.

Kein Eingriff in `SecurityConfig` nötig: Weder `GET
/auth/me/deletion-impact` noch `DELETE /auth/me` passen zu einer der
bestehenden rollen-spezifischen Regeln, beide fallen unter die
bestehende Catch-all-Regel `.anyRequest().authenticated()` — reicht, da
beide Rollen löschen dürfen.

## Frontend

**`frontend/src/auth.ts`:** zwei neue Funktionen nach dem Muster von
`login`/`register`/`logout`:
- `getDeletionImpact(): Promise<{ activityCount: number }>`
- `deleteAccount(password: string): Promise<string | null>` — liefert
  bei Erfolg `null` und setzt `currentUser` auf `null` (wie `logout`),
  bei Fehler die Fehlermeldung vom Server.

**`frontend/src/lib/Profile.svelte`:** Zugriffsschranke von "nur
Anbieter" auf "jeder eingeloggte Nutzer" gelockert. Profilbild-/Website-
Formular bleibt unverändert Anbieter-only (in einen Rollen-Check
gewrappt). Neuer Bereich "Konto löschen" darunter, für jede Rolle
sichtbar:
- Button "Konto löschen" öffnet den Bereich, ruft dabei
  `getDeletionImpact()` ab.
- Ist `activityCount > 0`, erscheint ein Warnhinweis mit der Zahl
  betroffener Aktivitäten.
- Passwort-Eingabefeld plus "Endgültig löschen"- und
  "Abbrechen"-Buttons.
- Bei falschem Passwort: Fehlermeldung, Bereich bleibt offen.
- Bei Erfolg: Weiterleitung zur Startseite (`navigate("/")`).

**`frontend/src/lib/NavBar.svelte`:** Der Link "Mein Profil" wird von
"nur bei `role === ANBIETER`" auf "bei jedem eingeloggten Nutzer"
umgestellt. "Aktivität hinzufügen" bleibt unverändert Anbieter-only.

## Testing

- Backend: Integrationstests für `DELETE /auth/me` (analog zum
  bestehenden Stil in `SignupControllerTest.kt`/
  `MainControllerDeleteActivityTest.kt`): falsches Passwort → 401, nichts
  gelöscht; Ehrenamtler-Löschung entfernt eigene Ratings/Signups;
  Anbieter-Löschung entfernt eigene Aktivitäten inkl. deren
  Ratings/Signups sowie empfangene ProviderRatings; nach Löschung ist der
  Nutzer ausgeloggt (Session invalidiert) und ein erneuter Login mit den
  alten Zugangsdaten schlägt fehl.
- Frontend: keine automatisierten Tests (Projekt-Konvention), manuelle
  Verifikation im Browser für beide Rollen.
