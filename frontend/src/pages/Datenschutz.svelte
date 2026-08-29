<script lang="ts">
    import LegalPageLayout from "../lib/LegalPageLayout.svelte";
</script>

<LegalPageLayout title="Datenschutzerklärung">
    <h2>1. Verantwortlicher</h2>
    <p>
        <span class="placeholder">[DEIN NAME]</span><br />
        <span class="placeholder">[DEINE ANSCHRIFT]</span><br />
        E-Mail: <span class="placeholder">[DEINE E-MAIL]</span>
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
        Ihre Daten werden gespeichert, bis Sie per E-Mail an die oben genannte
        Adresse die Löschung Ihres Kontos verlangen. Eine automatisierte
        Selbstlöschfunktion steht aktuell noch nicht zur Verfügung.
    </p>

    <h2>5. Cookies</h2>
    <p>
        Wir setzen ausschließlich ein technisch notwendiges Session-Cookie zur
        Anmeldung ein (SameSite=Lax). Es findet kein Tracking und keine
        Analyse Ihres Nutzungsverhaltens statt.
    </p>

    <h2>6. Drittanbieter</h2>
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

    <h2>7. Ihre Rechte</h2>
    <p>
        Sie haben das Recht auf Auskunft, Berichtigung, Löschung und
        Einschränkung der Verarbeitung Ihrer Daten sowie ein Recht auf
        Datenübertragbarkeit und ein Widerspruchsrecht. Wenden Sie sich dazu
        an die oben genannte E-Mail-Adresse. Außerdem haben Sie das Recht,
        sich bei einer Datenschutz-Aufsichtsbehörde zu beschweren.
    </p>
</LegalPageLayout>
