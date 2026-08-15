<script lang="ts">
    import { Marker, DivIcon } from "sveaflet";

    export let lat: number;
    export let lng: number;
    export let members: {
        id: number;
        name: string;
        category: string;
        address: string;
        dateTime: string;
    }[] = [];
</script>

<Marker latLng={[lat, lng]}>
    <DivIcon options={{ className: "cluster-divicon", iconSize: [34, 34], iconAnchor: [17, 17] }}>
        <div class="cluster-pin">{members.length}</div>
    </DivIcon>
</Marker>

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
</style>
