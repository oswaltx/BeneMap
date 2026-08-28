# Anmeldefunktion — Design

**Datum:** 2026-08-28
**Status:** Approved, bereit für Implementierungsplanung

## Ziel

Ehrenamtler können sich bislang nur außerhalb der App bei einem Anbieter
melden (Anruf, E-Mail), um bei einer Aktivität mitzumachen — die App zeigt
nur an, sagt aber nichts über tatsächliches Interesse oder Teilnahme aus.
Ziel ist eine einfache Anmeldefunktion: eingeloggte Ehrenamtler (Rolle
`USER`) können sich direkt in der App einer Aktivität zuordnen ("Ich mache
mit"), und der Anbieter sieht im eigenen Aktivitäts-Panel, wer sich
angemeldet hat.

## Nicht-Ziele

- **Kein Chat/Messaging** zwischen Anbieter und Ehrenamtler — zu großer
  Funktionsumfang für diese Iteration (Nachrichtenspeicherung,
  Echtzeitanzeige, Ungelesen-Status). E-Mail ist der einzige Kontaktweg
  in v1; sie ist ohnehin schon vorhanden (Login-Adresse), erfordert kein
  neues Feld.
- **Keine Benachrichtigung** (E-Mail/Push) bei neuer Anmeldung — der
  Anbieter muss aktiv im Panel nachschauen. Die App hat aktuell keinerlei
  Benachrichtigungs-Infrastruktur; das wäre ein eigenes, größeres Feature.
- **Keine Warteliste**, wenn `maxParticipants` erreicht ist — die
  Anmeldung wird einfach abgelehnt.
- **Kein Entfernen von Teilnehmern durch den Anbieter** — nur der
  Ehrenamtler selbst kann seine eigene Anmeldung zurückziehen (v1).
- **Keine Unterstützung für Städtische Angebote (Köln)** — diese haben
  keinen echten Anbieter-Account, der die Anmeldungen sehen könnte. Die
  Funktion ist ausschließlich für app-native Aktivitäten mit `createdBy
  != null` sichtbar.
- **Keine Zeitaufwand/Verbindlichkeit-Angabe** — war ursprünglich Teil der
  Diskussion, wurde aber verworfen (siehe Board), da die Kölner
  Engagementdatenbank dafür keine Daten liefert und der Mehrwert für den
  Aufwand als gering eingeschätzt wurde.

## Entscheidungen

- **Wer darf sich anmelden:** nur Rolle `USER`, analog zur bestehenden
  Einschränkung `POST /activities/*/ratings` → `hasRole("USER")` in
  `SecurityConfig.kt`. `ANBIETER`-Accounts können sich nicht anmelden.
- **Sichtbarkeit für den Anbieter:** Name **und** E-Mail-Adresse der
  angemeldeten Ehrenamtler (nicht nur Name) — E-Mail ist der einzige
  Kontaktweg, den es aktuell gibt.
- **Platzbegrenzung:** optional, vom Anbieter beim Anlegen/Bearbeiten
  einer Aktivität angebbar (`maxParticipants: Int?`, `null` = unbegrenzt).
  Anmeldung wird abgelehnt, sobald die Zahl erreicht ist.
- **Datenmodell-Muster:** eine neue Join-Entity `ActivitySignup`,
  strukturell identisch zu `ActivityRating` (siehe Architektur unten) —
  folgt einem bereits etablierten, getesteten Muster in der Codebase.

## Architektur & Datenfluss

**`ActivitySignup.kt`** (neu, analog zu `ActivityRating.kt`):

```kotlin
@Entity
@Table(
    name = "activity_signups",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "activity_id"])]
)
class ActivitySignup(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    var activity: VolunteerActivity,

    var createdAt: Instant = Instant.now(),
)
```

**`ActivitySignupRepository.kt`** (neu): `findByUserAndActivity`,
`findByActivity`, `countByActivity` (Spring Data Query-Derivation, wie bei
`ActivityRatingRepository`).

**`VolunteerActivity.kt`:** neues Feld `var maxParticipants: Int? = null`,
direkt neben den bestehenden optionalen Feldern platziert.

**`SignupController.kt`** (neu), im Stil von `RatingController.kt`:

```kotlin
data class SignupEntry(val name: String, val email: String)

data class SignupStatusResponse(
    val count: Int,
    val maxParticipants: Int?,
    val signedUp: Boolean,
    val participants: List<SignupEntry>,
)
```

- `POST /activities/{id}/signup` — 404 wenn Aktivität fehlt; wenn schon
  angemeldet (`findByUserAndActivity` liefert Treffer): no-op, `200 OK`.
  Sonst: wenn `maxParticipants != null && count(activity) >=
  maxParticipants` → `409 Conflict` mit Fehlermeldung. Sonst: neue
  `ActivitySignup` speichern, `200 OK`.
- `DELETE /activities/{id}/signup` — 404 wenn Aktivität fehlt; löscht die
  eigene Anmeldung, falls vorhanden (no-op sonst), `204 No Content`.
- `GET /activities/{id}/signups` — 404 wenn Aktivität fehlt. `count` und
  `maxParticipants` immer befüllt. `signedUp`: `true`, wenn der
  authentifizierte Nutzer (falls vorhanden) eine eigene `ActivitySignup`
  für diese Aktivität hat. `participants`: nur befüllt (Name + E-Mail
  jedes Teilnehmers), wenn `authentication?.name` zur E-Mail des
  `activity.createdBy` passt (der Anfragende ist der Anbieter dieser
  Aktivität) — sonst leere Liste.

**`SecurityConfig.kt`:**
```kotlin
it.requestMatchers(HttpMethod.GET, "/", "/markers", "/categories",
    "/activities/*/ratings", "/providers/*/ratings", "/activities/*/signups"
).permitAll()
it.requestMatchers(HttpMethod.POST, "/activities/*/ratings", "/providers/*/ratings",
    "/activities/*/signup"
).hasRole("USER")
it.requestMatchers(HttpMethod.DELETE, "/activities/*/signup").hasRole("USER")
```
(Die bestehende Zeile `DELETE /activities/*` bleibt unverändert für
`hasRole("ANBIETER")` — sie betrifft das Löschen der Aktivität selbst,
nicht die Anmeldung.)

**`Marker.kt`:** zwei neue Felder `signupCount: Int` und `maxParticipants:
Int?`, analog zu `activityRating`/`activityRatingCount`.

**`MainController.kt` — `markers()`:** Ein `signupsByActivityId =
activitySignupRepository.findAll().groupBy { it.activity.id }` (wie
`activityRatingsByActivityId`), `signupCount = signupsByActivityId[activity.id].orEmpty().size`,
`maxParticipants = activity.maxParticipants`.

**`MainController.kt` — `deleteActivity()`:** vor `repository.delete(activity)`
zusätzlich `activitySignupRepository.deleteAll(activitySignupRepository.findByActivity(activity))`
(gleiches Muster wie die bestehende Zeile für Ratings).

**`MainController.kt` — `addActivity()`/`addRecurringActivity()`/`updateActivity()`:**
`maxParticipants` wird wie die anderen optionalen Felder durchgereicht
(bei `addActivity` direkt vom Client übernommen, da `VolunteerActivity`
als Request-Body dient; bei den anderen beiden über das jeweilige
Request-DTO).

**`SignupModal.svelte`** (neu, analog zu `RatingModal.svelte`): lädt beim
Öffnen `GET /activities/{id}/signups`.
- Ist `isOwner` (als Prop von `PinDetailPanel` übergeben, wiederverwendet
  die dortige bestehende `isOwner`-Berechnung): zeigt die Teilnehmerliste
  (Name + E-Mail je Zeile).
- Sonst, wenn `$currentUser?.role === "USER"`: zeigt einen Button
  "Ich mache mit" (bzw. "Angemeldet ✓" mit einer Möglichkeit, sich wieder
  abzumelden, wenn `signedUp === true`); deaktiviert mit Hinweis
  "Ausgebucht", wenn `maxParticipants != null && count >= maxParticipants
  && !signedUp`.
- Sonst (nicht eingeloggt, oder `ANBIETER` bei fremder Aktivität): zeigt
  nur die Zahl plus denselben Login-Hinweis wie im bestehenden
  `RatingModal`.

**`PinDetailPanel.svelte`:** neues Badge (Stil wie `.rating-badge`), z. B.
"👥 3/5 Teilnehmende" oder "👥 3 Teilnehmende" ohne Limit — nur gerendert,
wenn `marker.providerId != null`. Klick öffnet `SignupModal`.
`marker`-Typ bekommt `signupCount: number` und `maxParticipants: number |
null` dazu.

**`AddActivity.svelte` / `EditActivityModal.svelte`:** neues optionales
Zahlenfeld "Maximale Teilnehmerzahl (optional)", analog zu den
bestehenden `<input type="number">`-Feldern bei der Wiederholungs-Angabe.

## Fehlerbehandlung

- Anmeldung bei bereits voller Aktivität: `409 Conflict`, Frontend zeigt
  eine Fehlermeldung im Modal (gleiches Muster wie
  `submitError`/`statusMessage` in bestehenden Formularen).
- Doppelte Anmeldung (z. B. zwei parallele Klicks): dank
  `existsByUserAndActivity`-Prüfung vor dem Speichern und dem
  DB-Unique-Constraint als Absicherung kein Duplikat möglich; der
  Unique-Constraint fängt eine Race Condition ab, die im Anwendungscode
  durchrutschen würde (führt zu einem seltenen 500er statt einem
  doppelten Datensatz — für v1 akzeptiert, da praktisch nur bei exakt
  zeitgleichen Requests desselben Nutzers auftreten kann).
- Reduziert ein Anbieter `maxParticipants` nachträglich unter die aktuelle
  Teilnehmerzahl: bestehende Anmeldungen bleiben unangetastet, nur neue
  Anmeldungen werden ab dann blockiert.

## Tests

- Backend: `SignupControllerTest` (neu, Stil wie `RatingControllerTest`)
  — Anmelden, doppeltes Anmelden ist no-op, Abmelden, Abmelden ohne
  vorherige Anmeldung ist no-op, Anmeldung bei vollem Limit wird
  abgelehnt, `participants` ist nur für den Anbieter selbst befüllt (nicht
  für andere Nutzer oder nicht eingeloggte Anfragen).
- `MainControllerMarkersTest`: `signupCount`/`maxParticipants` werden
  korrekt aus den `ActivitySignup`-Einträgen berechnet.
- `MainControllerDeleteActivityTest`: zugehörige Signups werden beim
  Löschen der Aktivität mitgelöscht.
- Manuell: im Browser eine Aktivität mit Limit 1 anlegen, mit zwei
  verschiedenen Test-Accounts anmelden (zweiter Versuch schlägt fehl),
  Anbieter-Ansicht zeigt beide — nur der erste ist tatsächlich
  gespeichert.
