<script lang="ts">
    import { createEventDispatcher, onDestroy } from "svelte";
    import { CircleMarker, Popup } from "sveaflet";
    import { categoryColor } from "./categoryColor";

    export let marker: {
        id: number;
        lat: number;
        lng: number;
        name: string;
        category: string;
        dateTime: string | null;
        activityRating: number | null;
        activityRatingCount: number;
        sourceUrl: string | null;
    };

    const dispatch = createEventDispatcher<{ select: { id: number } }>();

    let popupOpen = false;
    let closeTimer: ReturnType<typeof setTimeout> | null = null;

    function openNow() {
        if (closeTimer) {
            clearTimeout(closeTimer);
            closeTimer = null;
        }
        popupOpen = true;
    }

    function scheduleClose() {
        if (closeTimer) clearTimeout(closeTimer);
        closeTimer = setTimeout(() => {
            popupOpen = false;
            closeTimer = null;
        }, 200);
    }

    function select() {
        if (closeTimer) {
            clearTimeout(closeTimer);
            closeTimer = null;
        }
        popupOpen = false;
        dispatch("select", { id: marker.id });
    }

    onDestroy(() => {
        if (closeTimer) clearTimeout(closeTimer);
    });
</script>

<CircleMarker
    latLng={[marker.lat, marker.lng]}
    options={marker.sourceUrl
        ? { radius: 10, bubblingMouseEvents: false, color: "#F4C542", dashArray: "4, 4" }
        : { radius: 10, bubblingMouseEvents: false }}
    onmouseover={openNow}
    onmouseout={scheduleClose}
    onclick={select}
></CircleMarker>

{#if popupOpen}
    <Popup
        latLng={[marker.lat, marker.lng]}
        options={{
            closeButton: false,
            autoClose: false,
            closeOnClick: false,
            offset: [0, -10],
            autoPan: false,
            closeOnEscapeKey: false,
        }}
    >
        <button
            type="button"
            class="marker-popup"
            on:mouseenter={openNow}
            on:mouseleave={scheduleClose}
            on:click={select}
        >
            <div class="tooltip-header">
                <strong>{marker.name}</strong>
                {#if marker.category}
                    <span
                        class="tooltip-tag"
                        style="background:{categoryColor(marker.category).bg}; color:{categoryColor(marker.category).text};"
                    >{marker.category}</span>
                {/if}
            </div>
            {#if marker.dateTime}
                <p class="tooltip-date">{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
            {/if}
            <p class="tooltip-rating">
                {marker.activityRating != null ? `★ ${marker.activityRating.toFixed(1)} (${marker.activityRatingCount})` : "Noch keine Bewertung"}
            </p>
        </button>
    </Popup>
{/if}

<style>
    .marker-popup {
        display: block;
        min-width: 160px;
        border: none;
        background: none;
        padding: 0;
        margin: 0;
        font: inherit;
        text-align: left;
        cursor: pointer;
    }

    .tooltip-header {
        display: flex;
        align-items: center;
        gap: 6px;
        flex-wrap: wrap;
    }

    .tooltip-header strong {
        color: var(--color-text);
        font-size: 0.85rem;
    }

    .tooltip-tag {
        font-size: 0.65rem;
        padding: 1px 6px;
        border-radius: var(--radius-pill);
        white-space: nowrap;
    }

    .tooltip-date,
    .tooltip-rating {
        margin: 3px 0 0;
        font-size: 0.75rem;
        color: var(--color-text-muted);
    }
</style>
