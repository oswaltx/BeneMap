<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import { Marker, DivIcon, Popup } from "sveaflet";
    import { categoryColor } from "./categoryColor";

    export let lat: number;
    export let lng: number;
    export let members: {
        id: number;
        name: string;
        category: string;
        address: string;
        dateTime: string;
    }[] = [];

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

    function handleMarkerClick() {
        if (popupOpen) {
            scheduleClose();
        } else {
            openNow();
        }
    }

    function selectMember(id: number) {
        if (closeTimer) {
            clearTimeout(closeTimer);
            closeTimer = null;
        }
        popupOpen = false;
        dispatch("select", { id });
    }
</script>

<Marker
    latLng={[lat, lng]}
    onmouseover={openNow}
    onmouseout={scheduleClose}
    onclick={handleMarkerClick}
>
    <DivIcon options={{ className: "cluster-divicon", iconSize: [34, 34], iconAnchor: [17, 17] }}>
        <div class="cluster-pin">{members.length}</div>
    </DivIcon>
</Marker>

{#if popupOpen}
    <Popup
        latLng={[lat, lng]}
        options={{ closeButton: false, autoClose: false, closeOnClick: false, offset: [0, -16] }}
    >
        <div class="cluster-popup" on:mouseenter={openNow} on:mouseleave={scheduleClose}>
            <p class="cluster-popup-title">{members.length} Aktivitäten hier</p>
            <p class="cluster-popup-address">{members[0]?.address ?? ""}</p>
            {#each members as member (member.id)}
                <button type="button" class="cluster-row" on:click={() => selectMember(member.id)}>
                    <span class="cluster-row-text">
                        <span class="cluster-row-name">{member.name}</span>
                        <span class="cluster-row-date">{new Date(member.dateTime).toLocaleString("de-DE")}</span>
                    </span>
                    {#if member.category}
                        <span
                            class="cluster-row-tag"
                            style="background:{categoryColor(member.category).bg}; color:{categoryColor(member.category).text};"
                        >{member.category}</span>
                    {/if}
                </button>
            {/each}
        </div>
    </Popup>
{/if}

<style>
    :global(.cluster-divicon) {
        background: none;
        border: none;
    }
    .cluster-pin {
        width: 34px;
        height: 34px;
        border-radius: 50%;
        background: var(--color-primary);
        color: var(--color-primary-text);
        border: 3px solid var(--color-accent);
        box-shadow: 0 2px 5px rgba(0, 0, 0, 0.35);
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: 700;
        font-size: 0.8rem;
    }

    .cluster-popup {
        min-width: 220px;
        max-width: 260px;
    }

    .cluster-popup-title {
        margin: 0;
        font-size: 0.8rem;
        font-weight: 700;
        color: var(--color-text);
    }

    .cluster-popup-address {
        margin: 2px 0 8px;
        font-size: 0.75rem;
        color: var(--color-text-muted);
    }

    .cluster-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        width: 100%;
        padding: 6px 8px;
        margin-bottom: 4px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        cursor: pointer;
        font: inherit;
        text-align: left;
    }

    .cluster-row:hover {
        border-color: var(--color-primary);
    }

    .cluster-row-text {
        display: flex;
        flex-direction: column;
    }

    .cluster-row-name {
        font-weight: 600;
        font-size: 0.8rem;
        color: var(--color-text);
    }

    .cluster-row-date {
        font-size: 0.7rem;
        color: var(--color-text-muted);
    }

    .cluster-row-tag {
        font-size: 0.65rem;
        padding: 1px 6px;
        border-radius: var(--radius-pill);
        white-space: nowrap;
    }
</style>
