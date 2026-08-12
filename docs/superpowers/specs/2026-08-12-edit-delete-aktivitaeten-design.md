# Edit/Delete von Volunteer-Aktivitäten — Design

**Datum:** 2026-08-12
**Status:** Approved, bereit für Implementierungsplanung

## Ziel

Anbieter können ihre eigenen Aktivitäten nachträglich bearbeiten und
löschen. Bisher ist `/add` der einzige schreibende Endpunkt — einmal
angelegte Aktivitäten sind unveränderlich.

## Nicht-Ziele

- Kein Aktiv/Inaktiv-Umschalter für Aktivitäten — das Feld `isActive`
  existiert im Backend, wird aber aktuell nirgends genutzt. Relevant wird
  es erst mit wiederkehrenden Events (eigenes, noch nicht geplantes
  Feature) — dann macht "diese eine Instanz pausieren, ohne die ganze
  Serie zu löschen" Sinn. Für dieses Feature bleibt es außen vor.
- Keine eigene "Meine Aktivitäten"-Verwaltungsseite — Bearbeiten/Löschen
  passiert inline dort, wo Aktivitäten ohnehin schon angezeigt werden.
- Keine Änderung an Kategorien, Bewertungssystem oder Anbieter-Profilen.

## Entscheidungen

- **UI-Ort: sowohl `PinDetailPanel` als auch `VolunteerList`** — beide
  zeigen Bearbeiten/Löschen-Buttons, wenn der eingeloggte Anbieter der
  Eigentümer ist. Das deckt auch mobile Nutzung ab (unterhalb 1024px gibt
  es kein `PinDetailPanel`, aber `VolunteerList` ist überall sichtbar).
- **Löschen kaskadiert auf Bewertungen.** `ActivityRating` hat einen
  Pflicht-Fremdschlüssel auf die Aktivität (kein `ON DELETE CASCADE` in
  der DB). Beim Löschen einer Aktivität werden zuerst alle zugehörigen
  `ActivityRating`-Einträge gelöscht, dann die Aktivität selbst.
  Anbieter-Bewertungen (`ProviderRating`) sind an den Anbieter selbst
  gebunden, nicht an die Aktivität — bleiben unberührt.
- **Bearbeiten-Formular hat identische Felder wie "Aktivität
  hinzufügen"**: Name, Beschreibung, Adresse, Kategorie, Datum/Uhrzeit.
  Kein neues Feld.
- **Löschen-Bestätigung über natives `confirm()`** — kein eigenes
  Custom-Dialog-Component nötig für diesen MVP-Umfang.
- **Ownership-Erkennung im Frontend** über einen Vergleich der aktuellen
  User-ID mit `marker.providerId` (das Feld existiert bereits, entspricht
  `activity.createdBy?.id`). Dafür bekommt `UserResponse` (`/auth/me`)
  ein neues Feld `id: Long` — bisher wurde dort nur `email`/`name`/`role`
  zurückgegeben.

## Architektur & Datenfluss

**Backend:** Zwei neue Endpunkte in `MainController`, analog zu `/add`:

- `PUT /activities/{id}` — Body wie bei `/add` (name, description,
  addressText, category, dateTime). Erfordert `ROLE_ANBIETER`
  (`SecurityConfig`) **und** einen Ownership-Check im Controller
  (`activity.createdBy?.id == aktueller User.id`, sonst `403 Forbidden`
  — Spring Security prüft nur die Rolle, nicht den Objektbesitz).
  Geocodierung läuft nur neu, wenn sich `addressText` gegenüber dem
  gespeicherten Wert tatsächlich geändert hat. Schlägt die Geocodierung
  fehl, bleiben die alten Koordinaten erhalten (kein Pin-Verlust durch
  eine fehlgeschlagene Adresssuche bei einer sonst gültigen Bearbeitung).
- `DELETE /activities/{id}` — gleiche Rollen-/Ownership-Prüfung wie oben.
  Löscht zuerst alle `ActivityRating`-Zeilen zu dieser Aktivität
  (`activityRatingRepository.findByActivity` + `deleteAll`), dann die
  Aktivität selbst. Antwort `204 No Content`.

`UserResponse` bekommt das Feld `id: Long`, befüllt aus dem bereits
vorhandenen `User.id`.

**Frontend:** Neue Komponente `EditActivityModal.svelte`, strukturell wie
`RatingModal.svelte` (Overlay/Backdrop-Pattern), vorausgefüllt mit den
aktuellen Werten der Aktivität. Events: `close` (Abbrechen), `saved`
(nach erfolgreichem `PUT`). Löschen ist keine eigene Modal-Komponente,
sondern ein Button mit `confirm()`-Bestätigung, der direkt `DELETE`
aufruft und danach ein Event feuert.

`PinDetailPanel.svelte` und `VolunteerList.svelte` zeigen die
Bearbeiten-/Löschen-Buttons nur, wenn `$currentUser?.id ===
marker.providerId`. Beide nutzen dasselbe `EditActivityModal`. Nach
Speichern oder Löschen ruft die jeweilige Elternkomponente `fetchMarkers()`
erneut auf (das bestehende Refresh-Muster, das auch nach einer neuen
Bewertung greift). Wird die aktuell im `PinDetailPanel` angezeigte
Aktivität gelöscht, verschwindet sie aus `markers`, wodurch sich das Panel
automatisch schließt (`selectedMarker` wird `null`, da die reaktive
`.find()`-Ableitung nichts mehr findet — bestehendes Verhalten aus dem
Pin-Detailpanel-Feature, keine neue Logik nötig).

`auth.ts`s `currentUser`-Typ wird um `id: number` ergänzt, analog zum
Backend-Response.

## Fehlerbehandlung

- `403` bei `PUT`/`DELETE` durch einen Nicht-Eigentümer: im UI eigentlich
  nicht erreichbar, da die Buttons nur für Eigentümer sichtbar sind —
  trotzdem serverseitig durchgesetzt (z. B. gegen einen zweiten,
  parallel offenen Tab mit veraltetem Zustand). Frontend zeigt bei einem
  `403` eine generische Fehlermeldung.
- Geocodierung schlägt bei einer Adressänderung fehl: Speichern
  funktioniert trotzdem, alte Koordinaten bleiben erhalten, Warnhinweis
  analog zum bestehenden Verhalten in `AddActivity.svelte`.
- Aktivität existiert nicht mehr (z. B. bereits von einem anderen Tab
  gelöscht): `404`, Frontend zeigt Fehlermeldung und aktualisiert die
  Liste.

## Tests

- Backend: `PUT`/`DELETE` durch den Eigentümer funktionieren; durch einen
  fremden Anbieter oder ohne Login schlagen mit `403`/`401` fehl;
  `DELETE` einer Aktivität mit vorhandenen `ActivityRating`-Einträgen
  gelingt und lässt keine verwaisten Bewertungszeilen zurück; `PUT` ohne
  Adressänderung geocodiert nicht neu; `PUT` mit geänderter, aber nicht
  auflösbarer Adresse behält die alten Koordinaten.
- Frontend: manuelle Sichtprüfung (kein Test-Framework im Projekt, siehe
  bestehende Konvention). Manuell zu prüfen: Buttons nur für den
  Eigentümer sichtbar (Panel und Liste); Bearbeiten übernimmt aktuelle
  Werte korrekt vorausgefüllt; nach Speichern zeigt die Karte die neuen
  Werte; Löschen fragt nach Bestätigung, entfernt den Pin aus der Karte
  und schließt ein offenes Panel für die gelöschte Aktivität.
