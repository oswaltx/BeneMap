---

kanban-plugin: board

---

## Todo


- [ ] Research: Existing volunteer map solutions
- [ ] Research: Calendar sync implementation (iCal/Google API)
- [ ] View for multiple volunteer activies in one place
- [ ] Zeitaufwand und Verbindlichkeit
- [ ] Autokategorisierung von Events
## Doing


## Done

- [x] create datamodel
- [x] Login für Anbieter und User
- [x] Bewertungssystem
- [x] Pin-Detailseite optisch überarbeitet (Google-Maps-Stil, Seitenpanel als Overlay über der Karte)
- [x] Bugfix: Race Condition bei schnellen Filter-Änderungen behoben (Sequenz-Zähler in fetchMarkers)
- [x] Bewertungssystem-Politur: Anbieter-Name im RatingModal-Titel + Bewertungsdatum werden jetzt angezeigt
- [x] Edits von Volunteer Aktivitäten (inkl. Löschen) — Anbieter können eigene Aktivitäten bearbeiten/löschen, in Kartenpanel und Liste
- [x] Fotos für Aktivitäten (Galerie) + Anbieter-Profilbild/Website — nur URL-Eingabe, kein Datei-Upload; neue "Mein Profil"-Seite
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