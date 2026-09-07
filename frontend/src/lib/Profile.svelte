<script lang="ts">
    import { currentUser, authChecked, fetchWithSessionCheck, deleteAccount, getDeletionImpact } from "../auth";
    import { navigate } from "../router";
    import { API_BASE } from "./apiBase";
    import Link from "./Link.svelte";
    import PhotoDropzone from "./PhotoDropzone.svelte";

    let photoUrls: string[] = [];
    let websiteUrl = "";
    let prefilled = false;

    let hours: { totalHours: number; completedActivityCount: number } | null = null;
    let hoursLoaded = false;

    $: if ($currentUser?.role === "USER" && !hoursLoaded) {
        hoursLoaded = true;
        loadHours();
    }

    async function loadHours() {
        try {
            const res = await fetchWithSessionCheck(`${API_BASE}/auth/me/hours`, { credentials: "include" });
            if (res.ok) hours = await res.json();
        } catch {
            // Stunden-Anzeige ist rein informativ — schlägt der Abruf fehl, bleibt sie leer.
        }
    }

    let calendarToken: string | null = null;
    let calendarTokenLoaded = false;
    let regeneratingToken = false;

    $: if ($currentUser?.role === "USER" && !calendarTokenLoaded) {
        calendarTokenLoaded = true;
        loadCalendarToken();
    }

    async function loadCalendarToken() {
        try {
            const res = await fetchWithSessionCheck(`${API_BASE}/auth/me/calendar-token`, { credentials: "include" });
            if (res.ok) calendarToken = (await res.json()).token;
        } catch {
            // Kalender-Link ist ein Komfort-Feature — schlägt der Abruf fehl, bleibt er leer.
        }
    }

    async function regenerateCalendarToken() {
        regeneratingToken = true;
        try {
            const res = await fetchWithSessionCheck(`${API_BASE}/auth/me/calendar-token/regenerate`, {
                method: "POST",
                credentials: "include",
            });
            if (res.ok) calendarToken = (await res.json()).token;
        } catch {
            // s.o.
        } finally {
            regeneratingToken = false;
        }
    }

    function calendarUrl(token: string, scheme: "http" | "webcal"): string {
        const base = API_BASE || window.location.origin;
        const withHttp = `${base}/calendar/${token}.ics`;
        return scheme === "webcal" ? withHttp.replace(/^https?:/, "webcal:") : withHttp;
    }

    $: if ($currentUser && !prefilled) {
        photoUrls = $currentUser.photoUrl ? [$currentUser.photoUrl] : [];
        websiteUrl = $currentUser.websiteUrl ?? "";
        prefilled = true;
    }

    let submitting = false;
    let statusMessage: string | null = null;
    let statusIsWarning = false;

    async function handleSubmit() {
        submitting = true;
        statusMessage = null;

        try {
            const res = await fetchWithSessionCheck(`${API_BASE}/auth/me`, {
                method: "PUT",
                credentials: "include",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    photoUrl: photoUrls[0] ?? null,
                    websiteUrl: websiteUrl.trim() || null,
                }),
            });

            if (!res.ok) {
                statusMessage = "Fehler beim Speichern. Bitte versuche es erneut.";
                statusIsWarning = true;
                return;
            }

            currentUser.set(await res.json());
            statusMessage = "Profil wurde aktualisiert.";
            statusIsWarning = false;
        } catch (e) {
            statusMessage = "Server nicht erreichbar. Bitte versuche es später erneut.";
            statusIsWarning = true;
        } finally {
            submitting = false;
        }
    }

    let deleteExpanded = false;
    let deleteActivityCount = 0;
    let deletePassword = "";
    let deleteSubmitting = false;
    let deleteError: string | null = null;

    async function openDelete() {
        deleteExpanded = true;
        deleteError = null;
        const impact = await getDeletionImpact();
        deleteActivityCount = impact.activityCount;
    }

    function cancelDelete() {
        deleteExpanded = false;
        deletePassword = "";
        deleteError = null;
    }

    async function confirmDelete() {
        deleteSubmitting = true;
        deleteError = null;
        const error = await deleteAccount(deletePassword);
        deleteSubmitting = false;
        if (error) {
            deleteError = error;
            return;
        }
        navigate("/");
    }
</script>

{#if !$authChecked}
    <div class="page"><p>Lädt…</p></div>
{:else if !$currentUser}
    <div class="page">
        <p class="notice">Nur eingeloggte Nutzer haben ein Konto.</p>
    </div>
{:else}
    <div class="page">
        <div class="stack">
            {#if $currentUser.role === "ANBIETER"}
                <form on:submit|preventDefault={handleSubmit}>
                    <label>
                        Profilbild
                        <PhotoDropzone bind:urls={photoUrls} maxPhotos={1} />
                    </label>

                    <label>
                        Website
                        <input type="text" bind:value={websiteUrl} placeholder="https://..." />
                    </label>

                    <button type="submit" disabled={submitting}>
                        {submitting ? "Speichert…" : "Speichern"}
                    </button>

                    {#if statusMessage}
                        <p class:warning={statusIsWarning}>{statusMessage}</p>
                    {/if}
                </form>
            {/if}

            {#if $currentUser.role === "USER" && hours}
                <div class="hours-card">
                    <h3>Ehrenamtsstunden</h3>
                    <p class="hours-total">{hours.totalHours} Stunden</p>
                    <p class="hours-meta">
                        {hours.completedActivityCount} abgeschlossene {hours.completedActivityCount === 1 ? "Aktivität" : "Aktivitäten"}
                    </p>
                    <a class="button-link" href={`${API_BASE}/auth/me/certificate.pdf`} download="ehrenamt-zertifikat.pdf">
                        Zertifikat herunterladen (PDF)
                    </a>
                </div>
            {/if}

            {#if $currentUser.role === "USER" && calendarToken}
                <div class="hours-card">
                    <h3>Kalender-Abo</h3>
                    <p class="hours-meta">
                        Abonniere deine Anmeldungen in Google/Apple/Outlook-Kalender — neue Anmeldungen erscheinen
                        automatisch.
                    </p>
                    <a class="button-link" href={calendarUrl(calendarToken, "webcal")}>Kalender abonnieren</a>
                    <button
                        type="button"
                        class="regenerate-link"
                        on:click={regenerateCalendarToken}
                        disabled={regeneratingToken}
                    >
                        {regeneratingToken ? "Erzeugt neuen Link…" : "Neuen Abo-Link erzeugen (alter wird ungültig)"}
                    </button>
                </div>
            {/if}

            <div class="danger-zone">
                <h3>Konto löschen</h3>
                {#if !deleteExpanded}
                    <button type="button" class="danger" on:click={openDelete}>Konto löschen</button>
                {:else}
                    <p>Diese Aktion ist unwiderruflich.</p>
                    {#if deleteActivityCount > 0}
                        <p class="warning">
                            Du hast {deleteActivityCount} {deleteActivityCount === 1 ? "Aktivität" : "Aktivitäten"} — diese werden mitgelöscht.
                        </p>
                    {/if}
                    <label>
                        Passwort zur Bestätigung
                        <input type="password" bind:value={deletePassword} />
                    </label>
                    <p class="forgot-password-hint">
                        Passwort nicht mehr bekannt? <Link href="/forgot-password">Passwort vergessen?</Link>
                    </p>
                    <div class="delete-actions">
                        <button type="button" class="danger" disabled={deleteSubmitting} on:click={confirmDelete}>
                            {deleteSubmitting ? "Löscht…" : "Endgültig löschen"}
                        </button>
                        <button type="button" on:click={cancelDelete} disabled={deleteSubmitting}>Abbrechen</button>
                    </div>
                    {#if deleteError}
                        <p class="warning">{deleteError}</p>
                    {/if}
                {/if}
            </div>
        </div>
    </div>
{/if}

<style>
    .page {
        flex: 1;
        display: flex;
        justify-content: center;
        padding: 24px 16px;
    }

    .stack {
        display: flex;
        flex-direction: column;
        gap: 16px;
        width: 100%;
        max-width: 420px;
    }

    form {
        display: flex;
        flex-direction: column;
        gap: 12px;
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        padding: 20px;
        box-shadow: var(--shadow-panel);
        height: fit-content;
    }

    label {
        display: flex;
        flex-direction: column;
        gap: 4px;
        font-size: 0.9rem;
        color: var(--color-text);
    }

    input {
        font-family: inherit;
        font-size: 0.9rem;
        padding: 8px 10px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        color: var(--color-text);
    }

    input:focus {
        outline: none;
        border-color: var(--color-primary);
    }

    button {
        align-self: flex-start;
    }

    button:disabled {
        opacity: 0.6;
        cursor: default;
    }

    p.warning {
        color: var(--color-error);
        font-size: 0.85rem;
        margin: 0;
    }

    .forgot-password-hint {
        margin: 0;
        font-size: 0.85rem;
        color: var(--color-text-muted);
    }

    .notice {
        color: var(--color-text-muted);
        font-size: 0.9rem;
        text-align: center;
        max-width: 420px;
    }

    .hours-card {
        display: flex;
        flex-direction: column;
        gap: 4px;
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        padding: 20px;
        box-shadow: var(--shadow-panel);
    }

    .hours-card h3 {
        margin: 0;
        font-size: 1rem;
    }

    .hours-total {
        margin: 0;
        font-size: 1.4rem;
        font-weight: 700;
        color: var(--color-primary);
    }

    .hours-meta {
        margin: 0;
        font-size: 0.85rem;
        color: var(--color-text-muted);
    }

    .button-link {
        align-self: flex-start;
        margin-top: 6px;
        font-size: 0.85rem;
        font-weight: 600;
        color: var(--color-primary);
        text-decoration: none;
        padding: 8px 12px;
        border: 1px solid var(--color-primary);
        border-radius: var(--radius-md);
    }

    .button-link:hover {
        background: var(--color-accent);
    }

    .regenerate-link {
        align-self: flex-start;
        margin-top: 4px;
        background: none;
        border: none;
        padding: 0;
        font-size: 0.75rem;
        color: var(--color-text-muted);
        text-decoration: underline;
        cursor: pointer;
    }

    .regenerate-link:hover {
        color: var(--color-primary);
    }

    .danger-zone {
        display: flex;
        flex-direction: column;
        gap: 10px;
        background: var(--color-surface);
        border: 1px solid var(--color-error);
        border-radius: var(--radius-lg);
        padding: 20px;
        box-shadow: var(--shadow-panel);
    }

    .danger-zone h3 {
        margin: 0;
        font-size: 1rem;
        color: var(--color-error);
    }

    .delete-actions {
        display: flex;
        gap: 8px;
    }

    button.danger {
        background: var(--color-error);
        color: var(--color-primary-text);
    }
</style>
