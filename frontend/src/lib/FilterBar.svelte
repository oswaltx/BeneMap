<script lang="ts">
    import { createEventDispatcher } from "svelte";

    export let categories: string[] = [];

    const dispatch = createEventDispatcher();

    let selectedCategory: string | null = null;
    let selectedDate: string | null = null;
    let selectedTimeSlot: { label: string; from: number; to: number } | null = null;

    const timeSlots = [
        { label: "Morgens (8–12)", from: 8, to: 12 },
        { label: "Mittags (12–15)", from: 12, to: 15 },
        { label: "Nachmittags (15–18)", from: 15, to: 18 },
        { label: "Abends (18–21)", from: 18, to: 21 },
    ];

    function apply() {
        dispatch("filter", {
            date: selectedDate,
            category: selectedCategory,
            timeFrom: selectedTimeSlot?.from ?? null,
            timeTo: selectedTimeSlot?.to ?? null,
        });
    }

    function reset() {
        selectedCategory = null;
        selectedDate = null;
        selectedTimeSlot = null;
        apply();
    }
</script>

<div>
    <select bind:value={selectedCategory} on:change={apply}>
        <option value={null}>Alle Kategorien</option>
        {#each categories as cat}
            <option value={cat}>{cat}</option>
        {/each}
    </select>

    <input type="date" bind:value={selectedDate} on:change={apply} />

    <div>
        {#each timeSlots as slot}
            <button
                    class:active={selectedTimeSlot === slot}
                    on:click={() => { selectedTimeSlot = slot; apply(); }}
            >
                {slot.label}
            </button>
        {/each}
    </div>

    <button on:click={reset}>Filter zurücksetzen</button>
</div>

<style>
    .active {
        font-weight: bold;
        outline: 2px solid black;
    }
</style>