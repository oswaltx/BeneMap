# Impressum & Datenschutzerklärung Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the legally required Impressum and Datenschutzerklärung pages, linked from a new site-wide footer, and remove unused dead analytics code found along the way.

**Architecture:** Two new static Svelte page components (`Impressum.svelte`, `Datenschutz.svelte`) share a small layout wrapper (`LegalPageLayout.svelte`) for consistent styling, are registered as new client-side routes, and are linked from a new minimal `Footer.svelte` rendered on every page. No backend changes.

**Tech Stack:** Svelte 5 (runes), TypeScript, the project's existing client-side router (`frontend/src/router.ts`).

## Global Constraints

- Every operator-identity value (name, address, email) in both pages is a clearly marked placeholder — wrapped in a `<span class="placeholder">` and written in square brackets, e.g. `[DEIN NAME]` — never a real value. The project owner fills these in later, outside this plan's scope.
- The Datenschutzerklärung's storage-duration section states data is kept until the user requests deletion by emailing the contact address — there is no self-service account-deletion feature, and this plan must not add one.
- No new dependencies (no markdown renderer, no new routing library) — plain Svelte components and the existing `routes` object in `frontend/src/router.ts`.
- The footer contains exactly two links: Impressum and Datenschutz. Nothing else.
- This project has no automated frontend test suite. `npm run check` (svelte-check + tsc) is the only automated gate for these tasks, per existing project convention.

---

### Task 1: Layout wrapper, Footer, Impressum page, router cleanup

**Files:**
- Create: `frontend/src/lib/LegalPageLayout.svelte`
- Create: `frontend/src/lib/Footer.svelte`
- Create: `frontend/src/pages/Impressum.svelte`
- Modify: `frontend/src/router.ts` (add the `/impressum` route; remove the dead Matomo `_paq` calls)
- Modify: `frontend/src/App.svelte` (render the new footer)

**Interfaces:**
- Produces: `LegalPageLayout.svelte` — a Svelte 5 component with props `{ title: string; children?: Snippet }`, rendering `title` as an `<h1>` and `children` inside a styled `.content` container. Task 2's `Datenschutz.svelte` imports and uses this exact component the same way `Impressum.svelte` does in this task.
- Produces: `Footer.svelte` — exported as the default component (no props). Task 2 edits this same file to add its second link; nothing else in this task's file list touches it.

- [ ] **Step 1: Create the shared legal-page layout**

Create `frontend/src/lib/LegalPageLayout.svelte`:

```svelte
<script lang="ts">
    import type { Snippet } from "svelte";

    interface Props {
        title: string;
        children?: Snippet;
    }

    let { title, children }: Props = $props();
</script>

<div class="page">
    <div class="content">
        <h1>{title}</h1>
        {@render children?.()}
    </div>
</div>

<style>
    .page {
        flex: 1;
        display: flex;
        justify-content: center;
        padding: 24px 16px;
    }

    .content {
        width: 100%;
        max-width: 720px;
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        padding: 24px 28px;
        box-shadow: var(--shadow-panel);
        color: var(--color-text);
    }

    .content :global(h2) {
        margin-top: 24px;
        margin-bottom: 8px;
        font-size: 1.1rem;
    }

    .content :global(p) {
        margin: 0 0 12px;
        line-height: 1.6;
    }

    .content :global(a) {
        color: var(--color-primary);
    }

    .content :global(.placeholder) {
        color: var(--color-error);
        font-weight: 600;
    }
</style>
```

- [ ] **Step 2: Create the footer with the Impressum link**

Create `frontend/src/lib/Footer.svelte`:

```svelte
<script lang="ts">
    import Link from "./Link.svelte";
</script>

<footer>
    <Link href="/impressum" activeClass="active">Impressum</Link>
</footer>

<style>
    footer {
        display: flex;
        justify-content: center;
        gap: 16px;
        padding: 12px 20px;
        border-top: 1px solid var(--color-border);
        font-size: 0.85rem;
    }

    footer :global(a) {
        color: var(--color-text-muted);
    }

    footer :global(a:hover),
    footer :global(a.active) {
        color: var(--color-text);
        text-decoration: underline;
    }
</style>
```

- [ ] **Step 3: Create the Impressum page content**

Create `frontend/src/pages/Impressum.svelte`:

```svelte
<script lang="ts">
    import LegalPageLayout from "../lib/LegalPageLayout.svelte";
</script>

<LegalPageLayout title="Impressum">
    <h2>Angaben gemäß § 5 TMG</h2>
    <p>
        <span class="placeholder">[DEIN NAME]</span><br />
        <span class="placeholder">[DEINE ANSCHRIFT]</span>
    </p>

    <h2>Kontakt</h2>
    <p>
        E-Mail: <span class="placeholder">[DEINE E-MAIL]</span>
    </p>

    <h2>Verantwortlich für den Inhalt nach § 18 Abs. 2 MStV</h2>
    <p>
        <span class="placeholder">[DEIN NAME]</span> (Anschrift wie oben)
    </p>
</LegalPageLayout>
```

- [ ] **Step 4: Register the `/impressum` route and remove the dead Matomo code**

`frontend/src/router.ts` currently reads:

```typescript
import { writable } from "svelte/store";
import type { Component } from "svelte";

import Home from "./pages/Home.svelte";
import About from "./pages/About.svelte";
import AddActivity from "./lib/AddActivity.svelte";
import Profile from "./lib/Profile.svelte";
import Login from "./pages/Login.svelte";
import Register from "./pages/Register.svelte";

export const route = writable<string>(window.location.pathname);

export const routes: Record<string, Component> = {
    "/": Home,
    "/about": About,
    "/add": AddActivity,
    "/profile": Profile,
    "/login": Login,
    "/register": Register,
};

export function navigate(path: string) {
    history.pushState({}, "", path);
    route.set(path);
    (window as any)._paq?.push(['setCustomUrl', path]);
    (window as any)._paq?.push(['trackPageView']);
}

window.addEventListener("popstate", () => {
    route.set(window.location.pathname);
    (window as any)._paq?.push(['setCustomUrl', window.location.pathname]);
    (window as any)._paq?.push(['trackPageView']);
});
```

Replace its full contents with:

```typescript
import { writable } from "svelte/store";
import type { Component } from "svelte";

import Home from "./pages/Home.svelte";
import About from "./pages/About.svelte";
import AddActivity from "./lib/AddActivity.svelte";
import Profile from "./lib/Profile.svelte";
import Login from "./pages/Login.svelte";
import Register from "./pages/Register.svelte";
import Impressum from "./pages/Impressum.svelte";

export const route = writable<string>(window.location.pathname);

export const routes: Record<string, Component> = {
    "/": Home,
    "/about": About,
    "/add": AddActivity,
    "/profile": Profile,
    "/login": Login,
    "/register": Register,
    "/impressum": Impressum,
};

export function navigate(path: string) {
    history.pushState({}, "", path);
    route.set(path);
}

window.addEventListener("popstate", () => {
    route.set(window.location.pathname);
});
```

(Task 2 adds the `/datenschutz` route to this same `routes` object — leave the structure exactly as above so that addition is a single extra line.)

- [ ] **Step 5: Render the footer on every page**

`frontend/src/App.svelte` currently reads:

```svelte
<script>
    import { onMount } from "svelte";
    import NavBar from "./lib/NavBar.svelte";
    import Router from "./lib/Router.svelte";
    import { fetchCurrentUser } from "./auth";

    onMount(() => {
        fetchCurrentUser();
    });
</script>
<NavBar />
<Router />
```

Replace it with:

```svelte
<script>
    import { onMount } from "svelte";
    import NavBar from "./lib/NavBar.svelte";
    import Router from "./lib/Router.svelte";
    import Footer from "./lib/Footer.svelte";
    import { fetchCurrentUser } from "./auth";

    onMount(() => {
        fetchCurrentUser();
    });
</script>
<NavBar />
<Router />
<Footer />
```

- [ ] **Step 6: Run the frontend type-check**

Run (from `frontend/`): `npm run check`
Expected: no new errors (there is one known pre-existing, unrelated type error in `FilterBar.svelte` — that one is fine to see, don't try to fix it, it's out of scope).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/lib/LegalPageLayout.svelte frontend/src/lib/Footer.svelte frontend/src/pages/Impressum.svelte frontend/src/router.ts frontend/src/App.svelte
git commit -m "feat: add Impressum page and site footer, remove dead analytics code"
```

---

### Task 2: Datenschutzerklärung page

**Files:**
- Create: `frontend/src/pages/Datenschutz.svelte`
- Modify: `frontend/src/router.ts` (add the `/datenschutz` route)
- Modify: `frontend/src/lib/Footer.svelte` (add the Datenschutz link)

**Interfaces:**
- Consumes: `LegalPageLayout.svelte` from Task 1, with the same `{ title: string; children?: Snippet }` props, used exactly like `Impressum.svelte` does.

- [ ] **Step 1: Create the Datenschutzerklärung page content**

Create `frontend/src/pages/Datenschutz.svelte`:

```svelte
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
        Profilbild-URL.
    </p>
    <p>
        <strong>Aktivitäten:</strong> Name, Beschreibung, Adresse bzw. Standort
        (inklusive der bei der Adresseingabe ermittelten Geokoordinaten),
        Foto-URLs, Kategorie, optionale Teilnehmer-Obergrenze.
    </p>
    <p>
        <strong>Bewertungen:</strong> von Nutzern abgegebene Bewertungen zu
        Aktivitäten und Anbietern.
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
        Einschränkung der Verarbeitung Ihrer Daten sowie ein Widerspruchsrecht.
        Wenden Sie sich dazu an die oben genannte E-Mail-Adresse. Außerdem
        haben Sie das Recht, sich bei einer Datenschutz-Aufsichtsbehörde zu
        beschweren.
    </p>
</LegalPageLayout>
```

- [ ] **Step 2: Register the `/datenschutz` route**

In `frontend/src/router.ts`, add the import alongside the existing `Impressum` import:

```typescript
import Impressum from "./pages/Impressum.svelte";
import Datenschutz from "./pages/Datenschutz.svelte";
```

And add the route to the `routes` object, right after `"/impressum": Impressum,`:

```typescript
export const routes: Record<string, Component> = {
    "/": Home,
    "/about": About,
    "/add": AddActivity,
    "/profile": Profile,
    "/login": Login,
    "/register": Register,
    "/impressum": Impressum,
    "/datenschutz": Datenschutz,
};
```

Nothing else in this file changes.

- [ ] **Step 3: Add the Datenschutz link to the footer**

`frontend/src/lib/Footer.svelte` currently has this markup (from Task 1):

```svelte
<footer>
    <Link href="/impressum" activeClass="active">Impressum</Link>
</footer>
```

Replace it with:

```svelte
<footer>
    <Link href="/impressum" activeClass="active">Impressum</Link>
    <Link href="/datenschutz" activeClass="active">Datenschutz</Link>
</footer>
```

The `<script>` block and `<style>` block are unchanged.

- [ ] **Step 4: Run the frontend type-check**

Run (from `frontend/`): `npm run check`
Expected: no new errors (same known pre-existing `FilterBar.svelte` error as Task 1, nothing else).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/Datenschutz.svelte frontend/src/router.ts frontend/src/lib/Footer.svelte
git commit -m "feat: add Datenschutzerklärung page"
```
