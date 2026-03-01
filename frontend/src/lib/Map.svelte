<script>
    import {onMount} from "svelte";
    import {Map, Marker, Popup, TileLayer} from "sveaflet";

    let markers = [{ id: 1, lat: 50.9375, lng: 6.9603, name: "Erroror 1" }];

    async function fetchMarkers() {
        try {
            const res = await fetch("http://localhost:8080/markers");
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
                </Popup>
            </Marker>
        {/each}

    </Map>
</div>