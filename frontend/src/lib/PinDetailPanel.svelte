<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import RatingModal from "./RatingModal.svelte";

    export let marker: {
        id: number;
        name: string;
        address: string;
        category: string;
        description: string;
        dateTime: string;
        activityRating: number | null;
        activityRatingCount: number;
        providerId: number | null;
        providerName: string | null;
        providerRating: number | null;
        providerRatingCount: number;
    };

    const dispatch = createEventDispatcher<{ close: void; refresh: void }>();

    const categoryPalette = [
        { bg: "#FDEBB0", text: "#6B4E00" },
        { bg: "#CFE3D2", text: "#1F4A2C" },
        { bg: "#FBD8CC", text: "#8A3B22" },
        { bg: "#D7E4F0", text: "#204A6B" },
        { bg: "#E8DFF5", text: "#4A2E6B" },
    ];

    function categoryColor(category: string) {
        let hash = 0;
        for (let i = 0; i < category.length; i++) {
            hash = category.charCodeAt(i) + ((hash << 5) - hash);
        }
        const index = Math.abs(hash) % categoryPalette.length;
        return categoryPalette[index];
    }

    let openRating: { target: "activity" | "provider"; targetId: number; targetLabel: string } | null = null;

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

<style>
    .panel {
        width: 360px;
        flex-shrink: 0;
        height: 100%;
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
