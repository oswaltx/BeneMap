<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import Link from "./Link.svelte";
    import { currentUser, fetchWithSessionCheck } from "../auth";

    export let target: "activity" | "provider";
    export let targetId: number;
    export let targetLabel: string;

    const dispatch = createEventDispatcher<{ close: void; rated: void }>();

    interface RatingEntry {
        userName: string;
        stars: number;
        comment: string | null;
        createdAt: string;
    }

    interface RatingListResponse {
        average: number | null;
        count: number;
        ratings: RatingEntry[];
        myRating: RatingEntry | null;
    }

    const endpoint = target === "activity" ? `activities/${targetId}/ratings` : `providers/${targetId}/ratings`;

    let ratings: RatingEntry[] = [];
    let average: number | null = null;
    let count = 0;
    let loading = true;
    let loadError: string | null = null;

    let selectedStars = 0;
    let comment = "";
    let submitting = false;
    let submitError: string | null = null;

    async function loadRatings() {
        loading = true;
        loadError = null;
        try {
            const res = await fetch(`http://localhost:8080/${endpoint}`, { credentials: "include" });
            if (!res.ok) throw new Error("Request failed");
            const data: RatingListResponse = await res.json();
            average = data.average;
            count = data.count;
            ratings = data.ratings;
            if (data.myRating) {
                selectedStars = data.myRating.stars;
                comment = data.myRating.comment ?? "";
            }
        } catch (e) {
            loadError = "Bewertungen konnten nicht geladen werden.";
        } finally {
            loading = false;
        }
    }

    loadRatings();

    async function handleSubmit() {
        if (selectedStars < 1) {
            submitError = "Bitte wähle eine Sternebewertung.";
            return;
        }
        submitting = true;
        submitError = null;
        try {
            const res = await fetchWithSessionCheck(`http://localhost:8080/${endpoint}`, {
                method: "POST",
                credentials: "include",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ stars: selectedStars, comment: comment || null }),
            });
            if (!res.ok) {
                submitError = "Bewertung konnte nicht gespeichert werden.";
                return;
            }
            await loadRatings();
            dispatch("rated");
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
        <h3>{targetLabel}</h3>
        <button class="close" on:click={() => dispatch("close")} aria-label="Schließen">×</button>
    </div>

    {#if loading}
        <p>Lädt…</p>
    {:else if loadError}
        <p class="warning">{loadError}</p>
    {:else}
        <p class="summary">
            {#if average != null}
                ★ {average.toFixed(1)} ({count} Bewertung{count === 1 ? "" : "en"})
            {:else}
                Noch keine Bewertungen.
            {/if}
        </p>

        <div class="rating-list">
            {#each ratings as r}
                <div class="rating-entry">
                    <div class="rating-entry-header">
                        <strong>{r.userName}</strong>
                        <span class="stars">{"★".repeat(r.stars)}{"☆".repeat(5 - r.stars)}</span>
                    </div>
                    <p class="rating-date">{new Date(r.createdAt).toLocaleDateString("de-DE")}</p>
                    {#if r.comment}<p>{r.comment}</p>{/if}
                </div>
            {/each}
        </div>

        {#if $currentUser?.role === "USER"}
            <form class="rate-form" on:submit|preventDefault={handleSubmit}>
                <div class="star-input">
                    {#each [1, 2, 3, 4, 5] as n}
                        <button
                                type="button"
                                class="star-button"
                                class:selected={n <= selectedStars}
                                on:click={() => (selectedStars = n)}
                                aria-label={`${n} Sterne`}
                        >★</button>
                    {/each}
                </div>
                <textarea bind:value={comment} placeholder="Kommentar (optional)"></textarea>
                <button type="submit" disabled={submitting}>
                    {submitting ? "Speichert…" : "Bewertung abschicken"}
                </button>
                {#if submitError}<p class="warning">{submitError}</p>{/if}
            </form>
        {:else}
            <p class="notice">
                Nur eingeloggte User können bewerten.
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

    .rating-list {
        display: flex;
        flex-direction: column;
        gap: 8px;
        max-height: 200px;
        overflow-y: auto;
    }

    .rating-entry {
        border-top: 1px solid var(--color-border);
        padding-top: 8px;
    }

    .rating-entry:first-child {
        border-top: none;
        padding-top: 0;
    }

    .rating-entry-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-size: 0.85rem;
    }

    .stars {
        color: var(--color-accent-text);
    }

    .rating-entry p {
        margin: 4px 0 0;
        font-size: 0.85rem;
        color: var(--color-text-muted);
    }

    .rate-form {
        display: flex;
        flex-direction: column;
        gap: 8px;
        border-top: 1px solid var(--color-border);
        padding-top: 12px;
    }

    .star-input {
        display: flex;
        gap: 4px;
    }

    .star-button {
        background: none;
        border: none;
        font-size: 1.4rem;
        line-height: 1;
        cursor: pointer;
        color: var(--color-border);
        padding: 0;
    }

    .star-button.selected {
        color: var(--color-accent-text);
    }

    textarea {
        font-family: inherit;
        font-size: 0.9rem;
        padding: 8px 10px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        color: var(--color-text);
        resize: vertical;
        min-height: 60px;
    }

    textarea:focus {
        outline: none;
        border-color: var(--color-primary);
    }

    button[type="submit"] {
        align-self: flex-start;
    }

    button[type="submit"]:disabled {
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
