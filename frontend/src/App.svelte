<script lang="ts">
    import { onMount } from 'svelte';

    type VolunteerActivity = {
        id: number;
        name: string;
        latitude: number;
        longitude: number;
    }

    let activities: VolunteerActivity[] = [];

    onMount(async () => {
        const res = await fetch('http://localhost:8080/activities');
        activities = await res.json(); // parse JSON automatically
    });
</script>

<h1>Volunteer Activities</h1>

{#if activities.length === 0}
    <p>Loading…</p>
{:else}
    <ul>
        {#each activities as activity}
            <li>
                <strong>{activity.name}</strong> — {activity.latitude}, {activity.longitude} — {activity.date}
            </li>
        {/each}
    </ul>
{/if}