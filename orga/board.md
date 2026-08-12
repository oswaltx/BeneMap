---

kanban-plugin: board

---

## Todo


- [ ] Research: Existing volunteer map solutions
- [ ] Research: Calendar sync implementation (iCal/Google API)
- [ ] View for multiple volunteer activies in one place
- [ ] Zeitaufwand und Verbindlichkeit
- [ ] Autokategorisierung von Events
- [ ] Edits von Volunteer Aktivitäten
- [ ] Bugfix: Race Condition bei schnellen Filter-Änderungen (fetchMarkers in Map.svelte überschreibt Ergebnisse ggf. mit veralteter Response, da keine Reihenfolge-Absicherung/Request-Abbruch)
- [ ] Pin-Detailseite (Popup beim Klick auf einen Marker) optisch überarbeiten — aktuell unstyled Leaflet-Popup (nackte h3/p-Tags), passt nicht zum Rest der App. Die blauen CircleMarker selbst bleiben wie sie sind (bewusst gewählt, zeigen überlagernde Events).
- [ ] Bewertungssystem-Politur: RatingModal zeigt bei Anbieter-Bewertungen nur den generischen Titel "Anbieter" statt des tatsächlichen Namens (Feld `providerName` existiert im Backend, wird im Frontend noch nicht durchgereicht), und das Datum einer Bewertung wird nicht angezeigt (Feld vorhanden, ungenutzt)
## Doing


## Done

- [x] create datamodel
- [x] Login für Anbieter und User
- [x] Bewertungssystem
- [ ] Add map library to frontend (Leaflet or MapLibre)
- [ ] Create basic Map component in Svelte
- [x] create basic springboot application


***

## Archive

- [ ] Work

%% kanban:settings
```
{"kanban-plugin":"board","list-collapse":[false,false,false]}
```
%%