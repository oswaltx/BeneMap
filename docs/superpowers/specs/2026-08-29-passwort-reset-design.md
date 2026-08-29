# Passwort-Reset — Design

## Ausgangslage

Nutzer, die ihr Passwort vergessen haben, sind aktuell dauerhaft aus ihrem
Konto ausgesperrt — es gibt keine Selbsthilfe-Möglichkeit. Dieses Feature
führt einen klassischen "Passwort vergessen"-Flow per E-Mail-Link ein. Im
Projekt existiert aktuell **keinerlei** E-Mail-Infrastruktur (keine
`spring-boot-starter-mail`-Dependency, keine SMTP-Konfiguration) — das
wird hier komplett neu aufgebaut.

## E-Mail-Versand

Neue Dependency `spring-boot-starter-mail`. SMTP-Zugangsdaten (Brevo)
werden über Umgebungsvariablen gesetzt, nicht committet:

```properties
spring.mail.host=${MAIL_SMTP_HOST:smtp-relay.brevo.com}
spring.mail.port=${MAIL_SMTP_PORT:587}
spring.mail.username=${MAIL_SMTP_USERNAME:}
spring.mail.password=${MAIL_SMTP_PASSWORD:}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

Solange `MAIL_SMTP_USERNAME`/`MAIL_SMTP_PASSWORD` nicht gesetzt sind,
startet die App normal weiter — nur der tatsächliche Versand schlägt fehl.
Dieser Fehler wird serverseitig geloggt und **nicht** an den Client
durchgereicht (siehe Endpoint-Verhalten unten) — sowohl aus
Sicherheitsgründen (keine Rückschlüsse auf Erfolg/Misserfolg) als auch,
damit die App auch ohne konfigurierte Zugangsdaten benutzbar bleibt.

Der Reset-Link zeigt fest auf `http://localhost:5173/reset-password?token=<token>`,
passend zum bestehenden Muster, dass Frontend-/Backend-URLs im Projekt
aktuell überall hartkodiert sind (z. B. CORS-Konfiguration, alle
`fetch`-Aufrufe im Frontend). Die Mail ist reiner Text (kein HTML-Templating
nötig für eine einzelne, einfache Nachricht).

## Token

Neue Entity `PasswordResetToken`: `id`, `user` (`@ManyToOne`), `token`
(zufälliger String, `UUID.randomUUID()` — 122 Bit Entropie, Industrie-
Standard für solche Tokens), `expiresAt` (`Instant`).

- Gültigkeit: 30 Minuten.
- Einmalgebrauch: der Token wird beim erfolgreichen Zurücksetzen gelöscht.
- Fordert ein Nutzer einen neuen Reset-Link an, werden vorherige, noch
  offene Tokens für dieses Konto vorher gelöscht (immer nur ein gültiger
  Link gleichzeitig).

## Rate-Limiting (Anfrage-Endpoint)

Verhindert, dass jemand das Postfach eines Dritten mit Reset-Mails flutet.
Rein In-Memory (kein neuer Infrastruktur-Bedarf, passt zur aktuellen
Ein-Instanz-Betriebsgröße der App), pro (normalisierter) E-Mail-Adresse
getrackt — unabhängig davon, ob zu dieser Adresse überhaupt ein Konto
existiert, damit sich am Antwortverhalten weiterhin nicht ablesen lässt,
ob ein Konto existiert.

- 1. Anfrage: sofort erlaubt.
- 2. Anfrage: erst nach 60 Sekunden seit der letzten Anfrage erlaubt.
- Jede weitere Anfrage danach: erst nach 5 Minuten seit der letzten
  Anfrage erlaubt.

Bei einer zu frühen Anfrage antwortet der Endpoint mit `429 Too Many
Requests` und einer bereits fertig formulierten deutschen Fehlermeldung
inklusive Wartezeit (z. B. "Bitte warte noch 45 Sekunden, bevor du es
erneut versuchst."), die das Frontend unverändert anzeigt — damit die
Wartezeit für den Nutzer sichtbar ist, ohne dass das Frontend eigene
Zeitberechnung braucht. Kein automatischer Reset des Zählers nach
längerer Inaktivität (bewusst einfach gehalten).

## Backend-Endpoints (neuer `PasswordResetController.kt`)

**`POST /auth/forgot-password`** mit Body `{ email }`, öffentlich (kein
Login nötig):
1. Rate-Limit prüfen (siehe oben) — bei Überschreitung `429`.
2. E-Mail normalisieren (trim + lowercase, wie in `AuthController`
   bereits üblich) und nach existierendem Nutzer suchen.
3. Existiert kein Nutzer zu dieser Adresse: trotzdem `200 OK` zurückgeben,
   nichts weiter tun (kein Konto-Enumeration-Leak).
4. Existiert ein Nutzer: alte offene Tokens löschen, neuen Token
   erzeugen und speichern, Reset-Mail verschicken (Fehler beim Versand
   werden abgefangen und geloggt, ändern aber nichts an der Antwort).
   `200 OK`.

**`POST /auth/reset-password`** mit Body `{ token, newPassword }`,
öffentlich:
1. Token nachschlagen. Ungültig oder abgelaufen → `400` mit
   `ErrorResponse("Link ist ungültig oder abgelaufen.")`.
2. Neues Passwort validieren (gleiche Regeln wie bei der Registrierung:
   8–72 Zeichen).
3. Passwort-Hash des Nutzers aktualisieren, Token löschen.
4. Alle aktuell bekannten Sessions dieses Kontos invalidieren (siehe
   unten) — unabhängig davon, ob der Reset-Request selbst über eine
   eingeloggte Session lief (der Flow funktioniert ja gerade ohne
   Login).
5. `204 No Content`.

## Sessions bei Reset invalidieren

Dafür wird eine echte `SessionRegistry` gebraucht, die es aktuell nicht
gibt. Neue Beans in `SecurityConfig.kt`:

```kotlin
@Bean
fun sessionRegistry(): SessionRegistry = SessionRegistryImpl()

@Bean
fun httpSessionEventPublisher(): HttpSessionEventPublisher = HttpSessionEventPublisher()
```

Verdrahtet über `.sessionManagement { it.sessionConcurrency { concurrency
-> concurrency.maximumSessions(-1).sessionRegistry(sessionRegistry) } }`
in der bestehenden `securityFilterChain`-Bean-Methode. `-1` bedeutet
laut Spring-Security-Doku ausdrücklich "unbegrenzt" — es wird **keine**
Obergrenze für gleichzeitige Sessions eingeführt (ein Nutzer darf
weiterhin auf beliebig vielen Geräten gleichzeitig eingeloggt sein), die
Registry dient ausschließlich dem gezielten Invalidieren beim Reset.

Beim erfolgreichen Reset werden alle in der Registry bekannten,
nicht-abgelaufenen Sessions für den betroffenen Nutzer per
`SessionInformation.expireNow()` als abgelaufen markiert — das führt dazu,
dass diese Sessions beim nächsten Request automatisch invalidiert und als
nicht-authentifiziert behandelt werden (Standard-Spring-Security-
Mechanismus über den automatisch aktivierten `ConcurrentSessionFilter`).

## Aufräumen bei Konto-Löschung

`AuthController.deleteAccount()` (bereits vorhandenes Feature) muss um
das Löschen offener `PasswordResetToken`-Einträge des Kontos ergänzt
werden, sonst verletzt eine Kontolöschung mit noch offenem Reset-Token
die Fremdschlüssel-Beziehung.

## Frontend

- `frontend/src/pages/ForgotPassword.svelte`: E-Mail-Eingabe, sendet an
  `POST /auth/forgot-password`. Zeigt bei Erfolg eine generische
  Bestätigung ("Falls ein Konto mit dieser E-Mail existiert, wurde eine
  E-Mail mit einem Link zum Zurücksetzen verschickt."), bei `429` die
  vom Server gelieferte Wartezeit-Meldung direkt an.
- `frontend/src/pages/ResetPassword.svelte`: liest `token` aus
  `window.location.search` (der bestehende Router trackt nur den Pfad,
  keine Query-Parameter — die Seite liest den Query-String selbst),
  Formular für neues Passwort + Bestätigung, sendet an
  `POST /auth/reset-password`. Bei Erfolg Weiterleitung zu `/login` mit
  Erfolgsmeldung, bei ungültigem/abgelaufenem Token Fehlermeldung mit
  Link zurück zu `/forgot-password`.
- `frontend/src/router.ts`: zwei neue Routen `/forgot-password` und
  `/reset-password`.
- `frontend/src/auth.ts`: zwei neue Helper-Funktionen nach bestehendem
  Muster (`requestPasswordReset(email)`, `resetPassword(token,
  newPassword)`).
- `frontend/src/pages/Login.svelte`: neuer Link "Passwort vergessen?"
  unterhalb des Formulars, zu `/forgot-password`.

## Bewusst nicht enthalten

- Kein automatischer Reset des Rate-Limit-Zählers nach Inaktivität.
- Keine Live-Countdown-Anzeige im Frontend — die Wartezeit wird einmalig
  als Zahl in der Fehlermeldung angezeigt, kein tickender Timer.
- Kein HTML-E-Mail-Templating — reiner Text reicht für eine einzelne,
  einfache Nachricht.

## Testing

- Backend: `JavaMailSender` wird in Tests per `@MockBean` ersetzt (kein
  echter Netzwerkzugriff). Testfälle: kompletter Flow (Anfrage → Token in
  DB → Reset mit Token → neues Passwort funktioniert beim Login), Reset
  mit abgelaufenem/ungültigem Token schlägt fehl, Anfrage für
  nicht-existierende E-Mail liefert trotzdem 200 und keinen Fehler,
  Rate-Limiting (2. Anfrage vor 60s wird abgelehnt, nach 60s erlaubt),
  Reset invalidiert eine zweite, noch offene Session desselben Kontos
  (analog zum bestehenden Test für Konto-Löschung).
- Frontend: keine automatisierten Tests (Projekt-Konvention), manuelle
  Verifikation im Browser.
