# Login für Anbieter und User — Design

**Datum:** 2026-08-11
**Status:** Approved, bereit für Implementierungsplanung

## Ziel

Aktuell kann jeder ohne jede Prüfung Aktivitäten über `POST /add` anlegen. Ziel
dieses Vorhabens: ein Konto-/Login-System für zwei Rollen (**Anbieter** und
**User**), sodass nur eingeloggte Anbieter Aktivitäten hinzufügen können. Die
Karte selbst bleibt ohne Login sichtbar (Lesezugriff für alle).

## Nicht-Ziele

- Bewertungssystem (unentschieden, separates Thema — siehe Board)
- Edits von Volunteer-Aktivitäten durch Anbieter (separater, folgender
  Board-Punkt — `createdBy` wird hier nur gesetzt, nicht für
  Berechtigungsprüfungen verwendet)
- Passwort-vergessen-Flow, E-Mail-Verifizierung
- Freischaltung/Prüfung von Anbieter-Konten (Registrierung ist frei und sofort
  aktiv, siehe Entscheidung unten)
- iCal-Sync für Anbieter (separater Board-Punkt: "Research: Calendar sync")
- OAuth/Social Login

## Entscheidungen

- **Beide Rollen können sich registrieren.** Ein gemeinsames Auth-System statt
  zweier getrennter, da beide dieselbe Mechanik brauchen.
- **Anbieter-Registrierung ist frei und sofort aktiv** — kein manuelles
  Freischalten. Reduziert Aufwand fürs MVP; Missbrauch wird über spätere
  Moderation/Meldefunktion gelöst, nicht über Gatekeeping beim Registrieren.
- **Auth-Mechanismus: Spring Security mit Session-Cookie** (nicht JWT). Nutzer
  bevorzugt das aus Sicherheitsgründen (Cookie ist für JavaScript unsichtbar,
  schützt besser gegen XSS-Token-Diebstahl als ein im Frontend gespeichertes
  JWT).
- **Passwort-Hashing:** BCrypt (Spring-Security-Standard).

## Datenmodell

Neue Entität `User` (Tabelle `users`):
- `id` (PK, auto-generated)
- `email` (unique, dient als Login-Name)
- `passwordHash`
- `name`
- `role` (Enum: `ANBIETER`, `USER`)
- `createdAt`

`VolunteerActivity` bekommt ein neues, nullable Feld:
- `createdBy` (FK auf `User`, nullable — bestehende gescrapte/geseedete
  Aktivitäten haben keinen Besitzer)

## Backend

Neue Endpunkte:
- `POST /auth/register` — E-Mail, Passwort, Name, Rolle → legt `User` an,
  loggt direkt ein (setzt Session-Cookie)
- `POST /auth/login` — E-Mail, Passwort → Spring Security prüft, setzt
  Session-Cookie
- `POST /auth/logout` — invalidiert Session
- `GET /auth/me` — liefert aktuellen Nutzer (E-Mail, Name, Rolle) oder 401,
  wenn nicht eingeloggt

Autorisierung:
- `/`, `/markers`, `/categories` bleiben öffentlich (`permitAll`)
- `/add` erfordert eingeloggten Nutzer mit Rolle `ANBIETER`; beim Anlegen wird
  `createdBy` auf den aktuellen Nutzer gesetzt
- `/auth/register`, `/auth/login` sind öffentlich; `/auth/logout`, `/auth/me`
  erfordern eine bestehende Session

CORS/CSRF:
- CORS-Konfiguration wandert von `@CrossOrigin` auf `MainController`
  zentral in die Spring-Security-Konfiguration (`allowCredentials = true`,
  Origin bleibt auf `http://localhost:5173` beschränkt)
- Session-Cookie wird mit `SameSite=Lax` gesetzt
- **Abweichung von der ursprünglichen Spec:** Spring Securitys separater
  CSRF-Token-Mechanismus (`CookieCsrfTokenRepository`) wird bewusst
  weggelassen. Ihn für eine Cross-Port-SPA (5173→8080) korrekt zum Laufen
  zu bringen ist eine der fehleranfälligeren Ecken von Spring Security 6
  (bekannter Stolperstein: das Token-Cookie wird nicht zuverlässig
  gesetzt). Das `SameSite=Lax`-Cookie blockiert den klassischen
  CSRF-Angriffsvektor bei POST-Requests bereits weitgehend — für den
  Umfang dieses Projekts ausreichend Schutz bei deutlich geringerem
  Risiko kaputter Requests. CSRF-Schutz wird daher in der
  Security-Konfiguration komplett deaktiviert (`csrf { it.disable() }`).

## Frontend

- Neue Seiten `Login.svelte`, `Register.svelte` (Registrierung fragt Rolle
  Anbieter/User ab)
- Ein globaler Auth-Store (aktueller Nutzer oder `null`), wird beim App-Start
  über `GET /auth/me` befüllt
- `NavBar.svelte`: zeigt "Login/Registrieren" wenn ausgeloggt, sonst
  "Hallo {Name} · Abmelden"; "Aktivität hinzufügen" nur sichtbar für
  eingeloggte Anbieter
- Direkter Aufruf von `/add` ohne eingeloggten Anbieter leitet zur Karte um,
  mit Hinweistext
- Alle bestehenden `fetch`-Aufrufe (Map, AddActivity) bekommen
  `credentials: "include"`; state-ändernde Requests lesen das
  `XSRF-TOKEN`-Cookie und schicken es als Header mit

## Fehlerbehandlung

- Login mit falschen Daten → 401, Formular zeigt generische Fehlermeldung
  ("E-Mail oder Passwort falsch") — kein Hinweis, welches der beiden falsch
  war (verhindert Enumeration bestehender E-Mail-Adressen)
- Registrierung mit bereits vergebener E-Mail → 409, Formular zeigt
  entsprechenden Hinweis
- `/add` ohne gültige Session/Rolle → 403, Frontend fängt das ab und leitet
  um (siehe oben)

## Tests

- Backend: Registrierung, Login (korrekt/falsch), Zugriff auf `/add` ohne
  Login (403), mit Login als `USER` (403), mit Login als `ANBIETER` (200,
  `createdBy` korrekt gesetzt)
- Frontend: manuelle Sichtprüfung (kein Test-Framework im Projekt, siehe
  bestehende Konvention aus dem UI-Redesign)
