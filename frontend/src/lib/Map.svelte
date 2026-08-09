    <script lang="ts">
        import {onMount} from "svelte";
        import { ControlZoom } from 'sveaflet';
        import {Map, Marker, Popup, TileLayer} from "sveaflet";
        import Button from "./Button.svelte";
        import FilterBar from "./FilterBar.svelte";
        import SearchBar from "./SearchBar.svelte";
        import VolunteerList from "./VolunteerList.svelte";
        import { CircleMarker } from "sveaflet";



        let markers = [{category: "Brono", id: 1, lat: 50.9375, lng: 6.9603, name: "Erroror 1", address: "Erroror 1", dateTime: "2023-01-01T00:00:00Z" }];
        let categories = ["Brono", "Kino", "Kultur", "Sport"];

        let query = {
            date: "",
            category: "",
            timeFrom: "",
            timeTo: "",
            search: "",
        };

        onMount(async () => {
            const res = await fetch("http://localhost:8080/categories");
            categories = await res.json();
            fetchMarkers();
        });

        async function fetchMarkers() {
            const params = new URLSearchParams();

            if (query.date) params.append("date", query.date);
            if (query.category) params.append("category", query.category);
            if (query.timeFrom) params.append("timeFrom", query.timeFrom);
            if (query.timeTo) params.append("timeTo", query.timeTo);
            if (query.search) params.append("search", query.search);

            const res = await fetch(
                "http://localhost:8080/markers?" + params.toString()
            );

            markers = await res.json();
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

    <SearchBar on:search={handleSearch} />
    <FilterBar {categories} on:filter={handleFilter} />

    <div style="width:100%;height:500px;">
        <Map options={{ center: [50.9375, 6.9603], zoom: 13 }}>
            <TileLayer
                url={'https://cartodb-basemaps-a.global.ssl.fastly.net/light_nolabels/{z}/{x}/{y}.png'}
                options={{ attribution }}
            />

            {#each markers as marker}
                <CircleMarker  latLng={[marker.lat, marker.lng]}>
                    <Popup>
                        <h3>{marker.name}</h3>
                        <p>{marker.address}</p>
                        <p>{marker.category}</p>
                        <p>{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
                    </Popup>
                </CircleMarker >
            {/each}
        </Map>
    </div>
    <VolunteerList {markers}></VolunteerList>
