<script lang="ts">
    import {onMount} from "svelte";
    import {Map, TileLayer, ControlZoom} from "sveaflet";
    import FilterBar from "./FilterBar.svelte";
    import SearchBar from "./SearchBar.svelte";
    import VolunteerList from "./VolunteerList.svelte";
    import PinDetailPanel from "./PinDetailPanel.svelte";
    import { groupByLocation } from "./groupByLocation";
    import ClusterMarker from "./ClusterMarker.svelte";
    import SingleMarkerPin from "./SingleMarkerPin.svelte";
    import { API_BASE } from "./apiBase";

    let markers: any[] = [];
    let showCityOffers = false;
    $: visibleMarkers = markers.filter((m) => showCityOffers || !m.sourceUrl);
    $: markerGroups = groupByLocation(visibleMarkers);
    let categories: string[] = [];
    let errorMessage: string | null = null;

    let leafletMap: any;

    // Three-state bottom sheet (peek/half/full), like Google Maps / Mapy on mobile.
    type SheetState = "peek" | "half" | "full";
    let sheetState: SheetState = "peek";
    let dragging = false;
    let dragStartY = 0;
    let dragStartHeight = 0;
    let liveHeight: number | null = null;
    let sheetEl: HTMLDivElement;

    const PEEK_PX = 64;

    function heightForState(state: SheetState): number {
        const vh = typeof window !== "undefined" ? window.innerHeight : 800;
        if (state === "peek") return PEEK_PX;
        if (state === "half") return Math.round(vh * 0.45);
        return Math.round(vh * 0.85);
    }

    $: sheetHeightPx = dragging && liveHeight != null ? liveHeight : heightForState(sheetState);

    function handleDragStart(e: PointerEvent) {
        dragging = true;
        dragStartY = e.clientY;
        dragStartHeight = sheetEl?.getBoundingClientRect().height ?? heightForState(sheetState);
        (e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId);
    }

    function handleDragMove(e: PointerEvent) {
        if (!dragging) return;
        const delta = dragStartY - e.clientY;
        const vh = window.innerHeight;
        liveHeight = Math.min(Math.round(vh * 0.9), Math.max(PEEK_PX, dragStartHeight + delta));
    }

    function handleDragEnd() {
        if (!dragging) return;
        dragging = false;
        const h = liveHeight ?? heightForState(sheetState);
        liveHeight = null;

        const states: SheetState[] = ["peek", "half", "full"];
        let closest: SheetState = "peek";
        let closestDist = Infinity;
        for (const s of states) {
            const d = Math.abs(heightForState(s) - h);
            if (d < closestDist) {
                closestDist = d;
                closest = s;
            }
        }
        sheetState = closest;
        if (sheetState === "peek" && selectedMarkerId != null && !isDesktop) {
            selectedMarkerId = null;
        }
    }

    function toggleSheet() {
        sheetState = sheetState === "peek" ? "half" : "peek";
    }

    function closeDetail() {
        selectedMarkerId = null;
        sheetState = "half";
    }

    let selectedMarkerId: number | null = null;
    $: selectedMarker = visibleMarkers.find((m) => m.id === selectedMarkerId) ?? null;

    let isDesktop = typeof window !== "undefined" ? window.innerWidth >= 1024 : false;
    function handleResize() {
        isDesktop = window.innerWidth >= 1024;
    }

    $: panelOpen = selectedMarker != null && isDesktop;
    $: sheetShowsDetail = selectedMarker != null && !isDesktop;

    let locating = false;
    let locateError: string | null = null;

    function locateMe() {
        if (typeof navigator === "undefined" || !("geolocation" in navigator)) {
            locateError = "Standortbestimmung wird von diesem Browser nicht unterstützt.";
            return;
        }
        locating = true;
        locateError = null;
        navigator.geolocation.getCurrentPosition(
            (pos) => {
                locating = false;
                const zoom = leafletMap ? Math.max(leafletMap.getZoom(), 15) : 15;
                leafletMap?.flyTo([pos.coords.latitude, pos.coords.longitude], zoom, { animate: true });
            },
            () => {
                locating = false;
                locateError = "Standort konnte nicht ermittelt werden.";
            },
            { enableHighAccuracy: true, timeout: 8000 }
        );
    }

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
            const res = await fetch(`${API_BASE}/categories`);
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
                `${API_BASE}/markers?` + params.toString()
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
        if (!isDesktop) {
            sheetState = "full";
            const m = visibleMarkers.find((mk) => mk.id === event.detail.id);
            if (m && leafletMap) {
                const zoom = Math.max(leafletMap.getZoom(), 16);
                leafletMap.flyTo([m.lat, m.lng], zoom, { animate: true });
            }
        }
    }

    function handleToggleCityOffers(event: CustomEvent<boolean>) {
        showCityOffers = event.detail;
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
            <FilterBar {categories} on:filter={handleFilter} on:toggleCityOffers={handleToggleCityOffers} />
            {#if errorMessage}<p class="error">{errorMessage}</p>{/if}
        </div>

        <div class="map-container">
            <Map
                bind:instance={leafletMap}
                options={{ center: [50.9375, 6.9603], zoom: 13, zoomControl: false, attributionControl: false }}
                onclick={() => (selectedMarkerId = null)}
            >
                <TileLayer
                    url={`https://{s}.basemaps.cartocdn.com/light_nolabels/{z}/{x}/{y}.png?key=${import.meta.env.VITE_CARTO_API_KEY}`}
                    options={{ attribution }}
                />
                <ControlZoom options={{ position: "bottomleft" }} />

                {#each markerGroups as group (group.key)}
                    {#if group.members.length === 1}
                        <SingleMarkerPin marker={group.members[0]} on:select={handleSelect} />
                    {:else}
                        <ClusterMarker
                            lat={group.lat}
                            lng={group.lng}
                            members={group.members}
                            on:select={handleSelect}
                        />
                    {/if}
                {/each}
            </Map>
        </div>

        <button
            class="locate-fab"
            class:locating
            on:click={locateMe}
            aria-label="Meinen Standort anzeigen"
            title="Meinen Standort anzeigen"
        >
            {#if locating}⏳{:else}📍{/if}
        </button>
        {#if locateError}<p class="locate-error">{locateError}</p>{/if}

        <div
            class="bottom-sheet"
            class:panel-open={panelOpen}
            class:dragging
            bind:this={sheetEl}
            style="height: {sheetHeightPx}px"
        >
            <div
                class="drag-handle"
                role="button"
                tabindex="0"
                aria-label="Ansicht ziehen zum Vergrößern oder Verkleinern"
                on:pointerdown={handleDragStart}
                on:pointermove={handleDragMove}
                on:pointerup={handleDragEnd}
                on:pointercancel={handleDragEnd}
                on:keydown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggleSheet(); } }}
            >
                <span class="grip"></span>
            </div>
            <div class="sheet-header">
                {#if sheetShowsDetail}
                    <button class="sheet-back" on:click={closeDetail}>← Zurück zur Liste</button>
                {:else}
                    <button class="sheet-toggle" on:click={toggleSheet}>
                        {visibleMarkers.length} Aktivitäten {sheetState === "peek" ? "▲" : "▼"}
                    </button>
                {/if}
                <span class="attribution">© CARTO © OpenStreetMap</span>
            </div>
            <div class="sheet-content" class:hidden={sheetState === "peek"}>
                {#if sheetShowsDetail && selectedMarker}
                    <PinDetailPanel embedded marker={selectedMarker} on:close={closeDetail} on:refresh={fetchMarkers} />
                {:else}
                    <VolunteerList markers={visibleMarkers} on:refresh={fetchMarkers} on:select={handleSelect} />
                {/if}
            </div>
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

    /* Keep zoom controls clear of the bottom sheet's peek height. */
    .map-container :global(.leaflet-bottom) {
        bottom: 76px;
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
        max-height: 90%;
        display: flex;
        flex-direction: column;
        overflow: hidden;
        transition: left 0.2s ease, height 0.25s ease;
    }

    .bottom-sheet.dragging {
        transition: left 0.2s ease;
    }

    .bottom-sheet.panel-open {
        left: 360px;
    }

    .drag-handle {
        display: flex;
        justify-content: center;
        padding: 8px 0 4px;
        cursor: grab;
        touch-action: none;
        flex-shrink: 0;
    }

    .grip {
        width: 40px;
        height: 5px;
        border-radius: var(--radius-pill);
        background: var(--color-border);
    }

    .sheet-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        padding: 0 12px 8px;
        flex-shrink: 0;
    }

    .sheet-toggle,
    .sheet-back {
        flex: 1;
        min-height: 44px;
        background: none;
        border: none;
        font-weight: 600;
        font-size: 0.95rem;
        color: var(--color-primary);
        cursor: pointer;
        text-align: left;
        padding: 0;
    }

    .attribution {
        font-size: 0.65rem;
        color: var(--color-text-muted);
        white-space: nowrap;
    }

    .sheet-content {
        overflow-y: auto;
        padding: 0 12px 12px;
        touch-action: pan-y;
    }

    .sheet-content.hidden {
        display: none;
    }

    .locate-fab {
        position: absolute;
        right: 12px;
        bottom: 80px;
        z-index: 1000;
        width: 48px;
        height: 48px;
        border-radius: 50%;
        background: var(--color-surface);
        color: var(--color-text);
        border: 1px solid var(--color-border);
        box-shadow: var(--shadow-panel);
        font-size: 1.2rem;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 0;
        cursor: pointer;
    }

    .locate-fab:hover {
        filter: none;
        border-color: var(--color-primary);
    }

    .locate-fab.locating {
        opacity: 0.7;
    }

    .locate-error {
        position: absolute;
        right: 12px;
        bottom: 134px;
        z-index: 1000;
        max-width: 220px;
        margin: 0;
        padding: 8px 10px;
        border-radius: var(--radius-md);
        background: var(--color-surface);
        border: 1px solid var(--color-error);
        color: var(--color-error);
        font-size: 0.75rem;
        box-shadow: var(--shadow-panel);
    }
</style>
