<script lang="ts">
    import LegalPageLayout from "../lib/LegalPageLayout.svelte";
</script>

<LegalPageLayout title="Datenschutzerklärung">
    <h2>1. Verantwortlicher</h2>
    <p>
        David Oswalt<br />
        Aposte-Paulus-Straße 5<br />
        E-Mail: contact@benemap.org
    </p>

    <h2>2. Welche Daten wir verarbeiten</h2>
    <p>
        <strong>Konto:</strong> E-Mail-Adresse, Name, Passwort (als Hash gespeichert,
        niemals im Klartext), Rolle (Ehrenamtler oder Anbieter), optional eine
        Profilbild-URL und eine Website-Adresse. Bei Anbietern werden Name,
        Profilbild und Website-Adresse öffentlich bei ihren Aktivitäten
        angezeigt.
    </p>
    <p>
        <strong>Aktivitäten:</strong> Name, Beschreibung, Adresse bzw. Standort
        (inklusive der bei der Adresseingabe ermittelten Geokoordinaten),
        Foto-URLs, Kategorie, optionale Teilnehmer-Obergrenze.
    </p>
    <p>
        <strong>Bewertungen:</strong> Sternebewertung und optionaler
        Freitext-Kommentar zu Aktivitäten und Anbietern. Ihre Bewertung wird
        zusammen mit Ihrem Namen öffentlich bei der jeweiligen Aktivität bzw.
        beim jeweiligen Anbieter angezeigt — auch für nicht angemeldete
        Besucher.
    </p>
    <p>
        <strong>Anmeldungen:</strong> Wenn Sie sich für eine Aktivität anmelden,
        werden Ihr Name und Ihre E-Mail-Adresse dem Anbieter dieser Aktivität
        angezeigt (nicht anderen Nutzern).
    </p>

    <h2>3. Zweck und Rechtsgrundlage der Verarbeitung</h2>
    <p>
        Die Verarbeitung erfolgt zur Erfüllung des Nutzungsverhältnisses
        zwischen Ihnen und uns, insbesondere um Ihnen die Funktionen der
        Plattform (Suche, Anmeldung zu Aktivitäten, Bewertungen) bereitzustellen.
        Rechtsgrundlage ist Art. 6 Abs. 1 lit. b DSGVO (Vertragserfüllung).
    </p>

    <h2>4. Speicherdauer</h2>
    <p>
        Ihre Daten werden gespeichert, bis Sie Ihr Konto löschen. Sie können
        Ihr Konto jederzeit selbst über Ihre Profilseite ("Konto löschen")
        endgültig löschen — dabei werden auch Ihre eigenen Aktivitäten,
        Bewertungen und Anmeldungen entfernt. Alternativ können Sie sich auch
        per E-Mail an die oben genannte Adresse wenden.
    </p>

    <h2>5. Cookies</h2>
    <p>
        Wir setzen ein technisch notwendiges Session-Cookie zur Anmeldung ein
        (SameSite=Lax). Für die Reichweitenmessung (siehe Punkt 6) werden
        keine Cookies gesetzt.
    </p>

    <h2>6. Reichweitenmessung (Matomo)</h2>
    <p>
        Wir nutzen die Analysesoftware Matomo, die wir selbst auf einem
        eigenen Server betreiben — Ihre Daten werden dabei nicht an Dritte
        weitergegeben. Matomo ist so konfiguriert, dass es keine Cookies
        setzt und Ihre IP-Adresse vor der Speicherung anonymisiert. Erfasst
        werden aufgerufene Seiten, ungefähre Herkunft (über die anonymisierte
        IP), verwendeter Browser/Bildschirmgröße und Verweildauer — ein
        Rückschluss auf Ihre Person ist damit nicht möglich. Rechtsgrundlage
        ist unser berechtigtes Interesse (Art. 6 Abs. 1 lit. f DSGVO) an der
        bedarfsgerechten Weiterentwicklung der Plattform. Da weder Cookies
        noch personenbezogene Kennungen gespeichert werden, ist hierfür keine
        Einwilligung erforderlich.
    </p>

    <h2>7. Drittanbieter</h2>
    <p>
        <strong>OpenStreetMap Nominatim:</strong> Wenn Sie eine Adresse für eine
        Aktivität eingeben, wird diese zur Ermittlung der Geokoordinaten an den
        Geokodierungsdienst Nominatim (OpenStreetMap Foundation) übertragen.
        Dabei wird auch Ihre IP-Adresse an Nominatim übermittelt. Mehr dazu:
        <a href="https://osmfoundation.org/wiki/Privacy_Policy" target="_blank" rel="noopener noreferrer">
            osmfoundation.org/wiki/Privacy_Policy
        </a>.
    </p>
    <p>
        <strong>CARTO:</strong> Die Kartenkacheln der Karte werden vom Anbieter
        CARTO geladen. Dabei wird Ihre IP-Adresse an CARTO übermittelt. Mehr
        dazu:
        <a href="https://carto.com/privacy/" target="_blank" rel="noopener noreferrer">
            carto.com/privacy
        </a>.
    </p>

    <h2>8. Ihre Rechte</h2>
    <p>
        Sie haben das Recht auf Auskunft, Berichtigung, Löschung und
        Einschränkung der Verarbeitung Ihrer Daten sowie ein Recht auf
        Datenübertragbarkeit und ein Widerspruchsrecht. Wenden Sie sich dazu
        an die oben genannte E-Mail-Adresse. Außerdem haben Sie das Recht,
        sich bei einer Datenschutz-Aufsichtsbehörde zu beschweren.
    </p>
</LegalPageLayout>
