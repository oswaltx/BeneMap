---

kanban-plugin: board

---

## Todo


- [ ] Research: Existing volunteer map solutions
- [ ] Research: Calendar sync implementation (iCal/Google API)
- [ ] View for multiple volunteer activies in one place
- [ ] Zeitaufwand und Verbindlichkeit
- [ ] Autokategorisierung von Events
- [ ] Login für Anbieter und User
- [ ] Bewertungssystem
- [ ] Edits von Volunteer Aktivitäten
- [ ] Bugfix: Race Condition bei schnellen Filter-Änderungen (fetchMarkers in Map.svelte überschreibt Ergebnisse ggf. mit veralteter Response, da keine Reihenfolge-Absicherung/Request-Abbruch)
## Doing


## Done

- [x] create datamodel
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