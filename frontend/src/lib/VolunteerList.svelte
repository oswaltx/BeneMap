<script lang="ts">
    export let markers: {
        id: number;
        name: string;
        address: string;
        category: string;
        dateTime: string;
        lat: number;
        lng: number;
    }[] = [];
</script>


    {#each markers as marker}
            <strong>{marker.name}</strong>
            <p>{marker.address}</p>
            <p>{marker.category}</p>
            <p>{new Date(marker.dateTime).toLocaleString("de-DE")}</p>
    {/each}
