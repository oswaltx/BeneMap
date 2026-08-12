<script lang="ts">
    import {onMount} from "svelte";
    import {Map, TileLayer} from "sveaflet";
    import FilterBar from "./FilterBar.svelte";
    import SearchBar from "./SearchBar.svelte";
    import VolunteerList from "./VolunteerList.svelte";
    import PinDetailPanel from "./PinDetailPanel.svelte";
    import { CircleMarker } from "sveaflet";

    let markers: any[] = [];
    let categories: string[] = [];
    let errorMessage: string | null = null;
    let sheetExpanded = false;

    let selectedMarkerId: number | null = null;
    $: selectedMarker = markers.find((m) => m.id === selectedMarkerId) ?? null;

    let isDesktop = typeof window !== "undefined" ? window.innerWidth >= 1024 : false;
    function handleResize() {
        isDesktop = window.innerWidth >= 1024;
    }

    $: panelOpen = selectedMarker != null && isDesktop;

    let query = {
        date: "",
        category: "",
        timeFrom: "",
        timeTo: "",
        search: "",
    };

    onMount(() => {
        window.addEventListener("resize", handleResize);
        return () => window.removeEventListener("resize", handleResize);
    });

    onMount(async () => {
        try {
            const res = await fetch("http://localhost:8080/categories");
            categories = await res.json();
        } catch (e) {
            errorMessage = "Kategorien konnten nicht geladen werden. Ist der Server erreichbar?";
        }
        fetchMarkers();
    });

    let fetchMarkersSeq = 0;

    async function fetchMarkers() {
        const seq = ++fetchMarkersSeq;
        const params = new URLSearchParams();

        if (query.date) params.append("date", query.date);
        if (query.category) params.append("category", query.category);
        if (query.timeFrom) params.append("timeFrom", query.timeFrom);
        if (query.timeTo) params.append("timeTo", query.timeTo);
        if (query.search) params.append("search", query.search);

        try {
            const res = await fetch(
                "http://localhost:8080/markers?" + params.toString()
            );
            if (!res.ok) throw new Error("Request failed");
            const data = await res.json();
            if (seq !== fetchMarkersSeq) return;
            markers = data;
            errorMessage = null;
        } catch (e) {
            if (seq !== fetchMarkersSeq) return;
            errorMessage = "Aktivitäten konnten nicht geladen werden. Ist der Server erreichbar?";
        }
    }

    function handleSearch(event: CustomEvent<string>) {
        query = { ...query, search: event.detail };
        fetchMarkers();
    }

    function handleFilter(event: CustomEvent<{
        date: string | null;
        category: string | null;
        timeFrom: number | null;
        timeTo: number | null;
    }>) {
        const { date, category, timeFrom, timeTo } = event.detail;
        query = {
            ...query,
            date: date ?? "",
            category: category ?? "",
            timeFrom: timeFrom?.toString() ?? "",
            timeTo: timeTo?.toString() ?? "",
        };
        fetchMarkers();
    }

    function handleSelect(event: CustomEvent<{ id: number }>) {
        selectedMarkerId = event.detail.id;
    }

    const attribution = '&copy; <a href="https://carto.com/">CARTO</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
</script>

<div class="map-shell">
    <div class="map-area">
        {#if panelOpen && selectedMarker}
            <PinDetailPanel
                marker={selectedMarker}
                on:close={() => (selectedMarkerId = null)}
                on:refresh={fetchMarkers}
            />
        {/if}

        <div class="search-panel" class:panel-open={panelOpen}>
            <SearchBar on:search={handleSearch} />
            <FilterBar {categories} on:filter={handleFilter} />
            {#if errorMessage}<p class="error">{errorMessage}</p>{/if}
        </div>

        <div class="map-container">
            <Map
                options={{ center: [50.9375, 6.9603], zoom: 13, zoomControl: false, attributionControl: false }}
                onclick={() => (selectedMarkerId = null)}
            >
                <TileLayer
                    url={'https://cartodb-basemaps-a.global.ssl.fastly.net/light_nolabels/{z}/{x}/{y}.png'}
                    options={{ attribution }}
                />

                {#each markers as marker (marker.id)}
                    <CircleMarker
                        latLng={[marker.lat, marker.lng]}
                        options={{ radius: 10, bubblingMouseEvents: false }}
                        onclick={() => (selectedMarkerId = marker.id)}
                    />
                {/each}
            </Map>
        </div>

        <div class="bottom-sheet" class:expanded={sheetExpanded} class:panel-open={panelOpen}>
            <div class="sheet-header">
                <button class="sheet-handle" on:click={() => sheetExpanded = !sheetExpanded}>
                    {markers.length} Aktivitäten {sheetExpanded ? "▼" : "▲"}
                </button>
                <span class="attribution">© CARTO © OpenStreetMap</span>
            </div>
            {#if sheetExpanded}
                <div class="sheet-content">
                    <VolunteerList {markers} on:refresh={fetchMarkers} on:select={handleSelect} />
                </div>
            {/if}
        </div>
    </div>
</div>

<style>
    .map-shell {
        height: 100%;
        width: 100%;
    }

    .map-area {
        position: relative;
        height: 100%;
        width: 100%;
    }

    .map-container {
        position: absolute;
        inset: 0;
    }

    .map-container :global(.leaflet-container) {
        height: 100%;
        width: 100%;
    }

    .search-panel {
        position: absolute;
        top: 12px;
        left: 12px;
        right: 12px;
        z-index: 1001;
        background: var(--color-surface);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-panel);
        padding: 10px 12px;
        display: flex;
        flex-direction: column;
        gap: 8px;
        transition: left 0.2s ease;
    }

    .search-panel.panel-open {
        left: 372px;
    }

    .error {
        color: var(--color-error);
        font-size: 0.8rem;
        margin: 0;
    }

    .bottom-sheet {
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        z-index: 1001;
        background: var(--color-surface);
        border-radius: var(--radius-lg) var(--radius-lg) 0 0;
        box-shadow: 0 -3px 10px rgba(47, 82, 51, 0.15);
        max-height: 60%;
        display: flex;
        flex-direction: column;
        transition: left 0.2s ease;
    }

    .bottom-sheet.panel-open {
        left: 360px;
    }

    .sheet-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        padding: 0 12px;
    }

    .sheet-handle {
        flex: 1;
        background: none;
        border: none;
        border-radius: var(--radius-lg) var(--radius-lg) 0 0;
        padding: 12px 0;
        font-weight: 600;
        color: var(--color-primary);
        cursor: pointer;
        text-align: left;
    }

    .attribution {
        font-size: 0.65rem;
        color: var(--color-text-muted);
        white-space: nowrap;
    }

    .sheet-content {
        overflow-y: auto;
        padding: 0 12px 12px;
    }
</style>
