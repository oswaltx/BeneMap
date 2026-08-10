<script lang="ts">
    import {onMount} from "svelte";
    import { ControlZoom } from 'sveaflet';
    import {Map, Marker, Popup, TileLayer} from "sveaflet";
    import FilterBar from "./FilterBar.svelte";
    import SearchBar from "./SearchBar.svelte";
    import VolunteerList from "./VolunteerList.svelte";
    import { CircleMarker } from "sveaflet";

    let markers: any[] = [];
    let categories: string[] = [];
    let errorMessage: string | null = null;
    let sheetExpanded = false;

    let query = {
        date: "",
        category: "",
        timeFrom: "",
        timeTo: "",
        search: "",
    };

    onMount(async () => {
        try {
            const res = await fetch("http://localhost:8080/categories");
            categories = await res.json();
        } catch (e) {
            errorMessage = "Kategorien konnten nicht geladen werden. Ist der Server erreichbar?";
        }
        fetchMarkers();
    });

    async function fetchMarkers() {
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
            markers = await res.json();
            errorMessage = null;
        } catch (e) {
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
    const attribution = '&copy; <a href="https://carto.com/">CARTO</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
</script>

<div class="map-shell">
    <div class="search-panel">
        <SearchBar on:search={handleSearch} />
        <FilterBar {categories} on:filter={handleFilter} />
        {#if errorMessage}<p class="error">{errorMessage}</p>{/if}
    </div>

    <div class="map-container">
        <Map options={{ center: [50.9375, 6.9603], zoom: 13 }}>
            <TileLayer
                url={'https://cartodb-basemaps-a.global.ssl.fastly.net/light_nolabels/{z}/{x}/{y}.png'}
                options={{ attribution }}
            />

            {#each markers as marker}
                <CircleMarker latLng={[marker.lat, marker.lng]}>
                    <Popup>
                        <h3>{marker.name}</h3>
                        <p>{marker.address}</p>
                        <p>{marker.category}</p>
                        <p>{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
                    </Popup>
                </CircleMarker>
            {/each}
        </Map>
    </div>

    <div class="bottom-sheet" class:expanded={sheetExpanded}>
        <button class="sheet-handle" on:click={() => sheetExpanded = !sheetExpanded}>
            {markers.length} Aktivitäten {sheetExpanded ? "▼" : "▲"}
        </button>
        {#if sheetExpanded}
            <div class="sheet-content">
                <VolunteerList {markers} />
            </div>
        {/if}
    </div>
</div>

<style>
    .map-shell {
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
    }

    .sheet-handle {
        width: 100%;
        background: none;
        border: none;
        border-radius: var(--radius-lg) var(--radius-lg) 0 0;
        padding: 12px;
        font-weight: 600;
        color: var(--color-primary);
        cursor: pointer;
    }

    .sheet-content {
        overflow-y: auto;
        padding: 0 12px 12px;
    }
</style>
