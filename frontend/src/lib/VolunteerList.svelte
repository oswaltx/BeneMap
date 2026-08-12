<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import RatingModal from "./RatingModal.svelte";

    export let markers: {
        id: number;
        name: string;
        address: string;
        category: string;
        dateTime: string;
        lat: number;
        lng: number;
        activityRating: number | null;
        activityRatingCount: number;
        providerId: number | null;
        providerRating: number | null;
        providerRatingCount: number;
    }[] = [];

    const dispatch = createEventDispatcher<{ refresh: void }>();

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

    function openActivityRating(marker: (typeof markers)[number]) {
        openRating = { target: "activity", targetId: marker.id, targetLabel: marker.name };
    }

    function openProviderRating(marker: (typeof markers)[number]) {
        if (marker.providerId == null) return;
        openRating = { target: "provider", targetId: marker.providerId, targetLabel: "Anbieter" };
    }

    function handleRated() {
        openRating = null;
        dispatch("refresh");
    }
</script>

<div class="list">
    {#each markers as marker}
        <div class="card">
            <div class="card-header">
                <strong>{marker.name}</strong>
                {#if marker.category}
                    <span
                        class="tag"
                        style="background:{categoryColor(marker.category).bg}; color:{categoryColor(marker.category).text};"
                    >{marker.category}</span>
                {/if}
            </div>
            <p class="address">{marker.address}</p>
            <p class="date">{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
            <div class="ratings">
                <button class="rating-badge" on:click={() => openActivityRating(marker)}>
                    {marker.activityRating != null ? `★ ${marker.activityRating.toFixed(1)} (${marker.activityRatingCount})` : "Noch keine Bewertung"}
                </button>
                {#if marker.providerId != null}
                    <button class="rating-badge" on:click={() => openProviderRating(marker)}>
                        Anbieter: {marker.providerRating != null ? `★ ${marker.providerRating.toFixed(1)} (${marker.providerRatingCount})` : "Noch keine Bewertung"}
                    </button>
                {/if}
            </div>
        </div>
    {/each}
    {#if markers.length === 0}
        <p class="empty">Keine Aktivitäten gefunden.</p>
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
    .list {
        display: flex;
        flex-direction: column;
        gap: 8px;
    }
    .card {
        background: var(--color-bg);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        padding: 10px 12px;
    }
    .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 8px;
    }
    .tag {
        font-size: 0.7rem;
        padding: 2px 8px;
        border-radius: var(--radius-pill);
        white-space: nowrap;
    }
    .address,
    .date {
        margin: 4px 0 0;
        font-size: 0.85rem;
        color: var(--color-text-muted);
    }
    .ratings {
        display: flex;
        gap: 6px;
        margin-top: 6px;
        flex-wrap: wrap;
    }
    .rating-badge {
        font-size: 0.75rem;
        padding: 3px 8px;
        border-radius: var(--radius-pill);
        border: 1px solid var(--color-border);
        background: var(--color-surface);
        color: var(--color-text);
        cursor: pointer;
    }
    .rating-badge:hover {
        border-color: var(--color-primary);
    }
    .empty {
        color: var(--color-text-muted);
        font-size: 0.85rem;
        text-align: center;
        padding: 12px 0;
    }
</style>
