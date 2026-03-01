<script>
    import {onMount} from "svelte";
    import { ControlZoom } from 'sveaflet';
    import {Map, Marker, Popup, TileLayer} from "sveaflet";
    import Button from "./Button.svelte";

    let markers = [{ id: 1, lat: 50.9375, lng: 6.9603, name: "Erroror 1", address: "Erroror 1", dateTime: "2023-01-01T00:00:00Z" }];

    async function fetchMarkers(dateFilter = "none", category = "none") {
        console.log("dateFilter: "+dateFilter)
        console.log("categoryFilter"+category)
        try {
            const res = await fetch("http://localhost:8080/markers?dateFilter=" + dateFilter + "&category="+ category);
            // Expecting something like:
            // [{ id: 1, lat: 50.9375, lng: 6.9603, name: "Marker 1" }]
            markers = await res.json();
        } catch (error) {
            console.error("Failed to fetch markers:", error);
        }
    }

    onMount(() => {
        fetchMarkers();
    });
</script>

<div style="width:600px;height:500px;">
    <Map
            options={{
            center: [50.9375, 6.9603],
            zoom: 13
        }}
    >
        <TileLayer url={'https://tile.openstreetmap.org/{z}/{x}/{y}.png'} />

        {#each markers as marker}
            <Marker latLng={[marker.lat, marker.lng]}>
                <Popup>
                    <h3>{marker.name}</h3>
                    <p>Lat: {marker.lat}, Lng: {marker.lng}</p>
                    <p>Address: {marker.address}</p>
                    <p>Date: {marker.dateTime}</p>
                    <p>ID: {marker.id}</p>
                </Popup>
            </Marker>
        {/each}

    </Map>
</div>