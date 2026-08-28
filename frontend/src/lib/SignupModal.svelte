<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import Link from "./Link.svelte";
    import { currentUser, fetchWithSessionCheck } from "../auth";

    export let activityId: number;
    export let isOwner: boolean;

    const dispatch = createEventDispatcher<{ close: void; changed: void }>();

    interface SignupEntry {
        name: string;
        email: string;
    }

    interface SignupStatusResponse {
        count: number;
        maxParticipants: number | null;
        signedUp: boolean;
        participants: SignupEntry[];
    }

    let count = 0;
    let maxParticipants: number | null = null;
    let signedUp = false;
    let participants: SignupEntry[] = [];
    let loading = true;
    let loadError: string | null = null;
    let submitting = false;
    let submitError: string | null = null;

    async function loadStatus() {
        loading = true;
        loadError = null;
        try {
            const res = await fetch(`http://localhost:8080/activities/${activityId}/signups`, { credentials: "include" });
            if (!res.ok) throw new Error("Request failed");
            const data: SignupStatusResponse = await res.json();
            count = data.count;
            maxParticipants = data.maxParticipants;
            signedUp = data.signedUp;
            participants = data.participants;
        } catch (e) {
            loadError = "Teilnahme-Status konnte nicht geladen werden.";
        } finally {
            loading = false;
        }
    }

    loadStatus();

    $: full = maxParticipants != null && count >= maxParticipants && !signedUp;

    async function handleToggle() {
        submitting = true;
        submitError = null;
        try {
            const res = await fetchWithSessionCheck(`http://localhost:8080/activities/${activityId}/signup`, {
                method: signedUp ? "DELETE" : "POST",
                credentials: "include",
            });
            if (!res.ok) {
                submitError = signedUp
                    ? "Abmelden fehlgeschlagen. Bitte versuche es erneut."
                    : res.status === 409
                        ? "Diese Aktivität ist bereits ausgebucht."
                        : "Anmelden fehlgeschlagen. Bitte versuche es erneut.";
                return;
            }
            await loadStatus();
            dispatch("changed");
        } catch (e) {
            submitError = "Server nicht erreichbar. Bitte versuche es später erneut.";
        } finally {
            submitting = false;
        }
    }
</script>

<button class="backdrop" aria-label="Schließen" on:click={() => dispatch("close")}></button>
<div class="modal">
    <div class="modal-header">
        <h3>Teilnahme</h3>
        <button class="close" on:click={() => dispatch("close")} aria-label="Schließen">×</button>
    </div>

    {#if loading}
        <p>Lädt…</p>
    {:else if loadError}
        <p class="warning">{loadError}</p>
    {:else}
        <p class="summary">
            {#if maxParticipants != null}
                {count} von {maxParticipants} Plätzen belegt
            {:else}
                {count} Teilnehmende
            {/if}
        </p>

        {#if isOwner}
            {#if participants.length === 0}
                <p class="notice">Noch niemand angemeldet.</p>
            {:else}
                <div class="participant-list">
                    {#each participants as p}
                        <div class="participant-entry">
                            <strong>{p.name}</strong>
                            <span class="participant-email">{p.email}</span>
                        </div>
                    {/each}
                </div>
            {/if}
        {:else if $currentUser?.role === "USER"}
            <button type="button" on:click={handleToggle} disabled={submitting || full}>
                {#if submitting}
                    Speichert…
                {:else if signedUp}
                    Angemeldet ✓ — Zurückziehen
                {:else if full}
                    Ausgebucht
                {:else}
                    Ich mache mit
                {/if}
            </button>
            {#if submitError}<p class="warning">{submitError}</p>{/if}
        {:else}
            <p class="notice">
                Nur eingeloggte User können sich anmelden.
                <Link href="/login">Jetzt einloggen</Link> oder
                <Link href="/register">registrieren</Link>.
            </p>
        {/if}
    {/if}
</div>

<style>
    .backdrop {
        position: fixed;
        inset: 0;
        z-index: 2000;
        background: rgba(42, 42, 34, 0.4);
        border: none;
        padding: 0;
        cursor: default;
    }

    .modal {
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        z-index: 2001;
        background: var(--color-surface);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-panel);
        padding: 20px;
        width: min(420px, 90vw);
        max-height: 80vh;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: 12px;
    }

    .modal-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
    }

    .modal-header h3 {
        font-size: 1.05rem;
    }

    .close {
        background: none;
        border: none;
        font-size: 1.3rem;
        line-height: 1;
        cursor: pointer;
        color: var(--color-text-muted);
        padding: 0;
    }

    .summary {
        font-weight: 600;
        color: var(--color-text);
        margin: 0;
    }

    .participant-list {
        display: flex;
        flex-direction: column;
        gap: 8px;
        max-height: 200px;
        overflow-y: auto;
    }

    .participant-entry {
        display: flex;
        flex-direction: column;
        border-top: 1px solid var(--color-border);
        padding-top: 8px;
        font-size: 0.85rem;
    }

    .participant-entry:first-child {
        border-top: none;
        padding-top: 0;
    }

    .participant-email {
        color: var(--color-text-muted);
        font-size: 0.8rem;
    }

    button[type="button"] {
        align-self: flex-start;
    }

    button[type="button"]:disabled {
        opacity: 0.6;
        cursor: default;
    }

    .warning {
        color: var(--color-error);
        font-size: 0.85rem;
        margin: 0;
    }

    .notice {
        color: var(--color-text-muted);
        font-size: 0.9rem;
        text-align: center;
        margin: 0;
        border-top: 1px solid var(--color-border);
        padding-top: 12px;
    }
</style>
