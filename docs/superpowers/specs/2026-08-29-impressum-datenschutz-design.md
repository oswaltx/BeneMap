# Impressum & Datenschutzerklärung — Design

## Ausgangslage

VoloMap hat aktuell keine rechtlichen Pflichtseiten. Für eine öffentlich
erreichbare Seite mit echten Nutzerkonten (Login/Register, gespeicherte
Aktivitäten, Bewertungen, Anmeldungen) sind ein Impressum (§5 TMG) und
eine Datenschutzerklärung (DSGVO) unabhängig vom Beta-Status
vorgeschrieben.

Diese Spec deckt ausschließlich die beiden Pflichtseiten und ihre
Verlinkung ab. Eine Selbstlöschfunktion für Nutzerkonten ist bewusst
**nicht** Teil dieser Spec — die Datenschutzerklärung beschreibt
stattdessen ehrlich den aktuellen Ist-Zustand (Löschung auf Anfrage per
Kontakt-E-Mail). Passwort-Reset ist ebenfalls ein separates, unabhängiges
Feature und nicht Teil dieser Spec.

## Recherchierte Datenflüsse (Grundlage für den Text)

- **Konto:** E-Mail, Name, Passwort-Hash, Rolle, optionale Profilbild-URL
  (`User.kt`).
- **Aktivitäten:** Name, Beschreibung, Adresse/Standort (inkl. per
  Geokodierung ermittelter Koordinaten), Foto-URLs, Kategorie, optionale
  Teilnehmer-Obergrenze.
- **Bewertungen:** Aktivitäts- und Anbieter-Bewertungen durch Nutzer.
- **Anmeldungen:** Wer sich für eine Aktivität anmeldet — Name und E-Mail
  sind für den jeweiligen Anbieter sichtbar (nicht für andere Nutzer).
- **Cookies:** Ein Session-Cookie für die Anmeldung, `SameSite=Lax`,
  technisch notwendig, kein Tracking.
- **Drittanbieter:**
  - OpenStreetMap Nominatim (`nominatim.openstreetmap.org`) — eingegebene
    Adressen werden zur Geokodierung dorthin übertragen
    (`GeocodingService.kt`).
  - CARTO (`basemaps.cartocdn.com`) — beim Laden der Kartenkacheln wird
    die IP-Adresse des Nutzers an CARTO übertragen.
- **Analytics:** Im Router liegt totes Matomo-Tracking-Code
  (`_paq.push(...)`), das nirgends initialisiert wird — aktuell wirkungslos,
  keine echte Datenerfassung. Wird im Rahmen dieser Spec entfernt (siehe
  unten), damit kein undokumentierter/irreführender Code stehen bleibt.

## Technischer Aufbau

Zwei neue statische Seiten-Komponenten nach bestehendem Muster
(`About.svelte`):

- `frontend/src/pages/Impressum.svelte`
- `frontend/src/pages/Datenschutz.svelte`

Neue Routen in `frontend/src/router.ts`: `/impressum` und `/datenschutz`,
registriert im bestehenden `routes`-Objekt.

Neue Komponente `frontend/src/lib/Footer.svelte`: schlanke Fußzeile mit
genau zwei Links (Impressum, Datenschutz) über die bestehende `Link`-
Komponente, gerendert in `App.svelte` unterhalb von `<Router />`. Kein
weiterer Inhalt (YAGNI) — bei künftigem Bedarf (z.B. Kontakt-Link) wird
der Footer dann erweitert, nicht vorab.

Andere Ansätze verworfen: Markdown-Dateien zur Laufzeit rendern (neue
Abhängigkeit, kein bestehendes Precedent) und Inhalte über einen
Backend-Endpoint ausliefern (unnötige Komplexität für statischen Text)
sind für zwei feste Textseiten nicht gerechtfertigt.

## Inhalt: Impressum

Struktur nach § 5 TMG:

- Diensteanbieter: Name, Anschrift (Platzhalter `[DEIN NAME]`,
  `[DEINE ANSCHRIFT]`)
- Kontakt: E-Mail (Platzhalter `[DEINE E-MAIL]`)
- Inhaltlich Verantwortlicher gemäß § 18 Abs. 2 MStV: dieselbe Person
  (kleines Einzelprojekt)

Alle Platzhalter sind im Text eindeutig als solche markiert (eckige
Klammern, GROSSSCHREIBUNG) und müssen vor einem echten öffentlichen
Livegang durch reale Daten ersetzt werden.

## Inhalt: Datenschutzerklärung

- **Verantwortlicher:** derselbe Platzhalter-Block wie im Impressum.
- **Welche Daten werden erhoben:** die im Abschnitt "Recherchierte
  Datenflüsse" oben aufgeführten Kategorien, in eigenen Unterabschnitten
  (Konto, Aktivitäten, Bewertungen, Anmeldungen).
- **Zweck & Rechtsgrundlage:** Erfüllung des Nutzungsverhältnisses,
  Art. 6 Abs. 1 lit. b DSGVO.
- **Speicherdauer:** Daten werden gespeichert, bis der Nutzer per
  Kontaktaufnahme (E-Mail an die im Impressum genannte Adresse) die
  Löschung verlangt — ehrliche Beschreibung des Ist-Zustands, da es
  aktuell keine Selbstlöschfunktion gibt.
- **Cookies:** nur das technisch notwendige Session-Cookie, kein
  Tracking/Analytics aktiv.
- **Drittanbieter:** OpenStreetMap Nominatim (Geokodierung, Link zu deren
  Datenschutzhinweisen) und CARTO (Kartenkacheln, Link zu deren
  Datenschutzhinweisen), jeweils mit Hinweis auf IP-Übertragung.
- **Betroffenenrechte:** Auskunft, Berichtigung, Löschung, Einschränkung,
  Widerspruch, Beschwerderecht bei der zuständigen Aufsichtsbehörde —
  Standardformulierungen, Kontakt über die im Impressum genannte
  Adresse.

## Aufräumen

Die toten `_paq.push(...)`-Aufrufe in `frontend/src/router.ts` (in
`navigate()` und im `popstate`-Listener) werden entfernt, da Matomo
nirgends initialisiert wird und der Code aktuell nur unbenutzter,
irreführender Totcode ist.

## Hinweis (nicht Teil der ausgelieferten Seite)

Dies ist eine solide Standard-Struktur, aber keine Rechtsberatung. Vor
einem echten öffentlichen Livegang sollten die ausgefüllten Platzhalter
gegengecheckt werden (z.B. mit einem kostenlosen Generator wie
e-recht24.de), insbesondere bei Randfragen wie der genauen
Impressumspflicht für ein rein privates Hobby-Projekt.
