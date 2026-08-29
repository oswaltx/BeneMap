<script lang="ts">
    import { currentUser, authChecked, fetchWithSessionCheck, deleteAccount, getDeletionImpact } from "../auth";
    import { navigate } from "../router";

    let photoUrl = "";
    let websiteUrl = "";
    let prefilled = false;

    $: if ($currentUser && !prefilled) {
        photoUrl = $currentUser.photoUrl ?? "";
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
            const res = await fetchWithSessionCheck("http://localhost:8080/auth/me", {
                method: "PUT",
                credentials: "include",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    photoUrl: photoUrl.trim() || null,
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
                        Profilbild-URL
                        <input type="text" bind:value={photoUrl} placeholder="https://..." />
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

    .notice {
        color: var(--color-text-muted);
        font-size: 0.9rem;
        text-align: center;
        max-width: 420px;
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
