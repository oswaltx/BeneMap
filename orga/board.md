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
- [ ] Bewertungssystem-Politur: RatingModal zeigt bei Anbieter-Bewertungen aus VolunteerList weiterhin nur den generischen Titel "Anbieter" statt des tatsächlichen Namens (in der neuen PinDetailPanel-Detailansicht ist das seit dem Pin-Detailpanel-Feature bereits gelöst, in VolunteerList.svelte noch nicht nachgezogen), und das Datum einer Bewertung wird nirgends angezeigt (Feld vorhanden, ungenutzt)
## Doing


## Done

- [x] create datamodel
- [x] Login für Anbieter und User
- [x] Bewertungssystem
- [x] Pin-Detailseite optisch überarbeitet (Google-Maps-Stil, Seitenpanel als Overlay über der Karte)
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