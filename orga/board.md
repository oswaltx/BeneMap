---

kanban-plugin: board

---

## Todo


- [ ] Research: Existing volunteer map solutions
- [ ] Research: Calendar sync implementation (iCal/Google API)
- [ ] Zeitaufwand und Verbindlichkeit
- [ ] E-Mail-Verifizierung bei Registrierung — vor Beta-Veröffentlichung: verhindert getippte/falsche E-Mail-Adressen; kurz Design besprechen (blockiert Login? Resend-Flow?) vor Umsetzung
- [ ] Lizenz für das Projekt festlegen
- [ ] Nach Live-Scrape: prüfen dass `--scrape`-Flag in `~/benemap/start.sh` wieder entfernt ist (sonst re-scraped bei jedem Neustart)
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
- [x] Hover-Übersicht auf der Karte — Tooltip mit Name, Kategorie, Datum, Bewertung beim Überfahren eines Punkts
- [x] Mehrere Aktivitäten an einem Ort — Cluster-Pin mit Anzahl statt sich überlappender Einzel-Pins; Hover/Klick zeigt Liste, Klick auf Zeile öffnet Detailansicht
- [x] Wiederkehrende Events — Checkbox "Wiederholt sich" beim Anlegen (alle N Tage/Wochen), erzeugt automatisch unabhängige Termine bis zu 3 Monate im Voraus (max. 60)
- [x] Städtische Angebote (Köln) Toggle — blendet gescrapte, undatierte Angebote der Kölner Engagementdatenbank auf Karte/Liste ein/aus, gestrichelter Pin-Rand, Link zur Quelle statt Datum
- [x] Scraper-Überarbeitung — echte Namen/Kategorien/Einsatzort statt "Unbekannt"/Zufall/Vermittlungsstelle-Adresse, wiederholbar per `--scrape`-Flag
- [x] Vermittlungsstelle-Kontaktdaten — Name, Homepage, E-Mail, Telefonnummer der vermittelnden Organisation werden mitgescrapt und im Detail-Panel als eigener Block angezeigt
- [x] Hover-Popup vereinheitlicht — Einzel-Pins nutzen jetzt dasselbe sticky Hover-Popup wie Cluster-Pins statt eines Tooltips, der sofort verschwand
- [x] Anmeldefunktion — Ehrenamtler (Rolle USER) können sich direkt in der App für eine Aktivität anmelden ("Ich mache mit"), Anbieter sieht Name + E-Mail der Angemeldeten, optionale Teilnehmer-Obergrenze; gilt nur für app-native Aktivitäten, nicht für Städtische Angebote
- [x] Kategorie-Dropdown — Anbieter wählen die Kategorie beim Anlegen/Bearbeiten aus einer festen Liste (dieselben Kategorien wie bei Städtischen Angeboten, plus "Sonstiges") statt sie frei einzutippen
- [x] Impressum & Datenschutzerklärung — rechtliche Pflichtseiten (§5 DDG, DSGVO) verlinkt aus neuem Footer; Betreiberdaten jetzt ausgefüllt (David Oswalt, contact@benemap.org)
- [x] Konto-Selbstlöschung — jeder eingeloggte Nutzer (Ehrenamtler & Anbieter) kann sein Konto auf der Profilseite mit Passwort-Bestätigung endgültig löschen; kaskadiert über eigene Aktivitäten/Bewertungen/Anmeldungen, invalidiert alle Sessions des Kontos
- [x] Datenschutzerklärung aktualisiert — Abschnitt "Speicherdauer" verweist jetzt auf die Konto-Selbstlöschung statt auf Löschung per Kontakt-E-Mail
- [x] Passwort-Reset — Nutzer können ihr Passwort per E-Mail-Link zurücksetzen; Rate-Limiting (60s/5min), Reset invalidiert alle anderen Sessions des Kontos; SMTP über Brevo (noreply@benemap.org) läuft, live verifiziert
- [x] Rate-Limiting auf sensiblen Endpoints — Login (10/5min/IP), Registrierung (5/60min/IP), Aktivität anlegen (20/60min/Nutzer), Bewertungen & Anmeldungen (30/60min/Nutzer), Abuse-Schutz vor Livegang
- [x] Projekt als öffentliches Repo auf GitHub hochgeladen — github.com/oswaltx/BeneMap, Codeberg-Remote bleibt parallel bestehen (Hinweis-Bio wegen KI-Code-Richtlinie)
- [x] CONTRIBUTING.md geschrieben — Workflow, Code-Style, Tests, PR-Prozess
- [x] README.md überarbeitet — echte Screenshots (Kartenansicht, Login), Feature-Übersicht, Tech-Stack, Roadmap-Abschnitt mit wichtigsten offenen TODOs
- [x] About-Seite mit Inhalt gefüllt — Beschreibung, Beta-Status-Hinweis, Link zu GitHub
- [x] App live auf benemap.org deployed — Uberspace (Supervisor-Service, Port 48100), DNS bei Porkbun, `deploy.sh` für künftige Updates; dabei drei Prod-only-Bugs gefunden & gefixt: toter "Hello World"-Root-Endpoint blockierte die SPA, Security-Regeln erlaubten keine SPA-Routen/Assets, CORS erlaubte nur localhost:5173 (Frontend rief zudem überall hart `localhost:8080` statt einer relativen API_BASE auf)
- [x] contact@benemap.org eingerichtet (Weiterleitung)
- [x] Favicon & Seitentitel gesetzt (Pin-Icon in Markenfarben, "Benemap — Freiwilligenkarte")
- [x] Beta-/Cookie-Hinweisbanner — dismissible, verweist auf Datenschutzerklärung, erwähnt dass nur technisch notwendige Cookies (Login-Session) genutzt werden, kein Tracking
- [x] "Passwort vergessen?"-Link im Konto-löschen-Dialog
- [x] Frontend-Typfehler in `FilterBar.svelte` behoben (`npm run check` jetzt 0 Errors)
- [x] Backup-Strategie für die H2-Datenbank — tägliches Cron-Skript auf dem Uberspace-Server (`~/benemap/backup.sh`, 3:17 Uhr, 14 Tage Aufbewahrung)
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