<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import RatingModal from "./RatingModal.svelte";
    import EditActivityModal from "./EditActivityModal.svelte";
    import { categoryColor } from "./categoryColor";
    import { deleteActivity } from "./activityActions";
    import { currentUser } from "../auth";

    export let marker: {
        id: number;
        name: string;
        address: string;
        category: string;
        description: string;
        photoUrls: string[];
        dateTime: string;
        activityRating: number | null;
        activityRatingCount: number;
        providerId: number | null;
        providerName: string | null;
        providerRating: number | null;
        providerRatingCount: number;
    };

    const dispatch = createEventDispatcher<{ close: void; refresh: void }>();

    let openRating: { target: "activity" | "provider"; targetId: number; targetLabel: string } | null = null;
    $: isOwner = $currentUser?.id === marker.providerId;
    let editing = false;

    async function handleDelete() {
        if (await deleteActivity(marker.id)) {
            dispatch("refresh");
        }
    }

    function openActivityRating() {
        openRating = { target: "activity", targetId: marker.id, targetLabel: marker.name };
    }

    function openProviderRating() {
        if (marker.providerId == null) return;
        openRating = { target: "provider", targetId: marker.providerId, targetLabel: marker.providerName ?? "Anbieter" };
    }

    function handleRated() {
        openRating = null;
        dispatch("refresh");
    }
</script>

<div class="panel">
    <div class="panel-header">
        {#if marker.category}
            <span
                class="tag"
                style="background:{categoryColor(marker.category).bg}; color:{categoryColor(marker.category).text};"
            >{marker.category}</span>
        {/if}
        {#if isOwner}
            <button class="edit-link" on:click={() => (editing = true)}>Bearbeiten</button>
            <button class="edit-link" on:click={handleDelete}>Löschen</button>
        {/if}
        <button class="close" on:click={() => dispatch("close")} aria-label="Schließen">×</button>
    </div>

    <h3>{marker.name}</h3>
    <p class="meta">{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
    <p class="meta">{marker.address}</p>

    {#if marker.description}
        <p class="description">{marker.description}</p>
    {/if}

    <button class="rating-badge" on:click={openActivityRating}>
        {marker.activityRating != null ? `★ ${marker.activityRating.toFixed(1)} (${marker.activityRatingCount})` : "Noch keine Bewertung"}
    </button>

    {#if marker.providerId != null}
        <div class="provider">
            <span class="provider-name">{marker.providerName}</span>
            <button class="rating-badge" on:click={openProviderRating}>
                {marker.providerRating != null ? `★ ${marker.providerRating.toFixed(1)} (${marker.providerRatingCount})` : "Noch keine Bewertung"}
            </button>
        </div>
    {/if}
</div>

{#if openRating}
    <RatingModal
        target={openRating.target}
        targetId={openRating.targetId}
        targetLabel={openRating.targetLabel}
        on:close={() => (openRating = null)}
        on:rated={handleRated}
    />
{/if}

{#if editing}
    <EditActivityModal
        marker={{ id: marker.id, name: marker.name, description: marker.description, address: marker.address, category: marker.category, dateTime: marker.dateTime, photoUrls: marker.photoUrls }}
        on:close={() => (editing = false)}
        on:saved={() => dispatch("refresh")}
    />
{/if}

<style>
    .panel {
        position: absolute;
        top: 0;
        left: 0;
        bottom: 0;
        width: 360px;
        z-index: 1002;
        overflow-y: auto;
        background: var(--color-surface);
        border-right: 1px solid var(--color-border);
        box-shadow: var(--shadow-panel);
        padding: 20px;
        display: flex;
        flex-direction: column;
        gap: 10px;
    }

    .panel-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
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

    .edit-link {
        background: none;
        border: none;
        font-size: 0.75rem;
        color: var(--color-text-muted);
        cursor: pointer;
        padding: 2px 6px;
        text-decoration: underline;
    }

    .edit-link:hover {
        color: var(--color-primary);
    }

    .tag {
        font-size: 0.75rem;
        padding: 2px 10px;
        border-radius: var(--radius-pill);
        white-space: nowrap;
    }

    h3 {
        font-size: 1.15rem;
    }

    .meta {
        margin: 0;
        font-size: 0.9rem;
        color: var(--color-text-muted);
    }

    .description {
        margin: 6px 0 0;
        font-size: 0.9rem;
        color: var(--color-text);
        line-height: 1.5;
    }

    .rating-badge {
        align-self: flex-start;
        font-size: 0.8rem;
        padding: 4px 10px;
        border-radius: var(--radius-pill);
        border: 1px solid var(--color-border);
        background: var(--color-bg);
        color: var(--color-text);
        cursor: pointer;
    }

    .rating-badge:hover {
        border-color: var(--color-primary);
    }

    .provider {
        display: flex;
        flex-direction: column;
        gap: 4px;
        margin-top: 4px;
        padding-top: 10px;
        border-top: 1px solid var(--color-border);
    }

    .provider-name {
        font-size: 0.85rem;
        font-weight: 600;
        color: var(--color-text);
    }
</style>
