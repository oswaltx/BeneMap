# Fotos und Anbieter-Profil — Design

**Datum:** 2026-08-14
**Status:** Approved, bereit für Implementierungsplanung

## Ziel

Anbieter können ihren Aktivitäten eine Foto-Galerie hinzufügen und ein
eigenes Profil mit Profilbild und Website pflegen. Nutzer sehen die Fotos
in der Detailansicht einer Aktivität, ähnlich wie bei Google-Maps-Einträgen.

## Nicht-Ziele

- Kein echter Datei-Upload — Anbieter fügen Links zu bereits gehosteten
  Bildern ein (eigene Website, Imgur o.ä.). Kein Multipart-Handling, keine
  Speicherplatz-Verwaltung auf dem Server nötig.
- Keine Bild-Validierung — eine ungültige URL zeigt einfach das
  Browser-Standard-Platzhalterbild, keine serverseitige Prüfung ob die URL
  wirklich auf ein Bild zeigt.
- Keine Foto-Vorschau in `VolunteerList`-Karten — die Galerie lebt nur in
  `PinDetailPanel`, die Liste bleibt kompakt und text-only, passt zum
  bisherigen Muster (reicher Inhalt im Panel, kompakte Liste).
- Keine Änderung an `RatingModal` — kein Profilbild dort, das wäre eigene
  Politur für ein andermal.
- Kein Migrationspfad auf echten Upload wird jetzt gebaut — aber das
  Datenmodell (Liste von URL-Strings) ist dafür bereits offen: ein
  späterer Upload-Endpunkt müsste nur eine Datei speichern und eine URL
  zurückgeben, die dann ins bestehende Feld geschrieben wird. Additive
  Änderung, kein Umbau des Datenmodells.

## Entscheidungen

- **Speicherung: nur URLs.** Kein Cloud-Storage, kein lokaler
  Datei-Upload — bewusste MVP-Entscheidung, siehe Nicht-Ziele.
- **Umfang: Aktivitäts-Galerie UND Anbieter-Profilbild zusammen** in einem
  Feature, da beides am selben Formular-Muster hängt.
- **Galerie-Layout: Hero-Bild + Streifen** (Option A aus dem visuellen
  Vergleich) — erstes Foto groß oben, restliche Fotos als kleine
  Vorschau darunter, Klick auf ein Vorschaubild tauscht das Hero-Bild aus.
  Analog zum Google-Maps-Vorbild, das den Ausschlag für dieses Feature
  gegeben hat.
- **Maximal 10 Fotos pro Aktivität**, serverseitig begrenzt (gegen
  Missbrauch, nicht weil mehr technisch problematisch wäre).
- **Neue "Mein Profil"-Seite**, nicht nur ein Registrierungsfeld — Anbieter
  können Profilbild-URL und Website jederzeit nach der Registrierung
  setzen/ändern, analog zum Aktivität-Bearbeiten-Formular.

## Architektur & Datenmodell

**Backend — Aktivitäts-Galerie:** `VolunteerActivity` bekommt ein neues
Feld `photoUrls: String?` (TEXT-Spalte, zeilenweise getrennte URLs) — keine
neue Tabelle/Entität, da die Fotoreihenfolge einfach der Zeilenreihenfolge
im Textfeld entspricht und nichts galerieübergreifend abgefragt werden
muss. `Marker` (das API-DTO für `/markers`) bekommt ein entsprechendes
`photoUrls: List<String>`-Feld, das aus dem gespeicherten Text geparst
wird (Split auf Zeilenumbruch, leere Zeilen und Whitespace getrimmt).
`/add` und `PUT /activities/{id}` nehmen `photoUrls: List<String>?` im
Request-Body entgegen, serverseitig auf maximal 10 Einträge begrenzt
(überzählige werden verworfen, kein Fehler).

**Backend — Anbieter-Profil:** `User` bekommt zwei neue Felder,
`photoUrl: String?` und `websiteUrl: String?`. Neuer Endpunkt
`PUT /auth/me` — Request-Body ist ein eigenes, schmales
`UpdateProfileRequest(photoUrl: String?, websiteUrl: String?)`, **nicht**
der volle User/`UserResponse`, damit `email`/`name`/`role` über diesen
Endpunkt gar nicht erst änderbar sind (kein versehentlicher
Rollen-/E-Mail-Wechsel möglich). Aktualisiert nur die eigenen Profildaten
des eingeloggten Anbieters (kein Anbieter-Wechsel möglich, keine ID im
Pfad nötig, da immer "ich selbst" gemeint ist, analog zum bestehenden
`GET /auth/me`). `UserResponse` liefert `photoUrl`/`websiteUrl` mit aus.
`Marker` bekommt `providerPhotoUrl: String?`/`providerWebsiteUrl: String?`
(analog zum bereits bestehenden `providerName`), damit das Frontend das
Anbieter-Profilbild und den Website-Link anzeigen kann, ohne einen
zusätzlichen Request zu brauchen.

**Frontend:**
- Neue Seite/Route **"Mein Profil"** (nur sichtbar für eingeloggte
  Anbieter, Link in der `NavBar`) — Formular mit Profilbild-URL- und
  Website-Feld, `PUT /auth/me`, strukturell wie das bestehende
  Aktivität-Bearbeiten-Formular (Status-Meldung, kein automatisches
  Schließen/Weiterleiten).
- `AddActivity.svelte` und `EditActivityModal.svelte` bekommen ein neues
  Textarea-Feld "Foto-URLs (eine pro Zeile)".
- `PinDetailPanel.svelte` bekommt die Galerie (Hero-Bild + Vorschau-Streifen,
  lokaler State für den Index des aktuell großen Bildes) sowie im
  Anbieter-Bereich ein kleines Profilbild (falls `providerPhotoUrl`
  gesetzt) und einen klickbaren Website-Link (falls `providerWebsiteUrl`
  gesetzt, öffnet in neuem Tab).
- Aktivitäten ohne Fotos: Galerie-Bereich wird komplett ausgeblendet, kein
  leerer Platzhalter — analog zum bestehenden Umgang mit fehlender
  Beschreibung/fehlendem Anbieter.

## Fehlerbehandlung

- Ungültige/tote Bild-URL: Browser zeigt sein Standard-Platzhalterbild,
  keine serverseitige Prüfung.
- Mehr als 10 Foto-URLs eingefügt: serverseitig auf 10 gekappt, kein
  Fehler, kein Hinweis im Frontend (bewusst einfach gehalten für MVP —
  falls das später als verwirrend auffällt, kann eine Warnung nachgezogen
  werden).
- `PUT /auth/me` ohne Login: `401`, wie alle anderen geschützten
  Endpunkte in diesem Projekt.

## Tests

- Backend: `photoUrls`-Parsing (Zeilenumbruch-Split, Trimmen, Kappung bei
  10) sowohl beim Lesen (`/markers`) als auch beim Schreiben
  (`/add`, `PUT /activities/{id}`); `PUT /auth/me` aktualisiert nur die
  eigenen Profildaten, nie die eines anderen Users; unauthentifizierter
  Zugriff auf `PUT /auth/me` liefert `401`.
- Frontend: manuelle Sichtprüfung (kein Test-Framework im Projekt, siehe
  bestehende Konvention). Manuell zu prüfen: Galerie zeigt Hero-Bild +
  Streifen korrekt, Klick auf Vorschaubild tauscht das Hero-Bild;
  Aktivität ohne Fotos zeigt keinen leeren Galerie-Bereich; "Mein
  Profil"-Formular speichert und zeigt Profilbild/Website danach im
  PinDetailPanel für eigene Aktivitäten.
