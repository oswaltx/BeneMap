<script lang="ts">
    import {onMount} from "svelte";
    import { ControlZoom } from 'sveaflet';
    import {Map, Marker, Popup, TileLayer} from "sveaflet";
    import Button from "./Button.svelte";
    import FilterBar from "./FilterBar.svelte";
    import VolunteerList from "./VolunteerList.svelte";


    let markers = [{category: "Brono", id: 1, lat: 50.9375, lng: 6.9603, name: "Erroror 1", address: "Erroror 1", dateTime: "2023-01-01T00:00:00Z" }];
    let categories = ["Brono", "Kino", "Kultur", "Sport"];
    onMount(async () => {
        const res = await fetch("http://localhost:8080/categories");
        categories = await res.json();
        fetchMarkers();
    });

    async function fetchMarkers(date = "", category = "", timeFrom = "", timeTo = "") {
        const params = new URLSearchParams();
        if (date) params.append("date", date);
        if (category) params.append("category", category);
        if (timeFrom !== null) params.append("timeFrom", timeFrom);
        if (timeTo !== null) params.append("timeTo", timeTo);

        const res = await fetch("http://localhost:8080/markers?" + params.toString());
        markers = await res.json();
    }

    function handleFilter(event: CustomEvent<{ date: "", category: "", timeFrom: "", timeTo: "" }>) {
        const { date, category, timeFrom, timeTo } = event.detail;
        fetchMarkers(date, category, timeFrom, timeTo);
    }
</script>

<FilterBar {categories} on:filter={handleFilter} />

<div style="width:600px;height:500px;">
    <Map options={{ center: [50.9375, 6.9603], zoom: 13 }}>
        <TileLayer url={'https://tile.openstreetmap.org/{z}/{x}/{y}.png'} />

        {#each markers as marker}
            <Marker latLng={[marker.lat, marker.lng]}>
                <Popup>
                    <h3>{marker.name}</h3>
                    <p>{marker.address}</p>
                    <p>{marker.category}</p>
                    <p>{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
                </Popup>
            </Marker>
        {/each}
    </Map>
</div>
<VolunteerList {markers}></VolunteerList>
