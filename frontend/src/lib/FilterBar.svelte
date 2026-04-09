<script lang="ts">
    import { createEventDispatcher } from "svelte";

    export let categories: string[] = [];

    const dispatch = createEventDispatcher<{
        filter: {
            date: string | null;
            category: string | null;
            timeFrom: number | null;
            timeTo: number | null;
        };
    }>();

    let selectedCategory: string | null = null;
    let selectedDate: string | null = null;
    let selectedWeekday: number | null = null;
    let selectedTimeSlot: { label: string; from: number; to: number } | null = null;

    const weekdays = [
        { label: "Mo", day: 1 },
        { label: "Di", day: 2 },
        { label: "Mi", day: 3 },
        { label: "Do", day: 4 },
        { label: "Fr", day: 5 },
        { label: "Sa", day: 6 },
        { label: "So", day: 0 },
    ];

    const timeSlots = [
        { label: "Jetzt", from: null, to: null },//TODO() add now
        { label: "Morgens (8–12)", from: 8, to: 12 },
        { label: "Mittags (12–15)", from: 12, to: 15 },
        { label: "Nachmittags (15–18)", from: 15, to: 18 },
        { label: "Abends (18–21)", from: 18, to: 21 },
    ];

    // Returns the next date (as YYYY-MM-DD) for a given weekday (0=Sun, 1=Mon, ...)
    function nextDateForWeekday(targetDay: number): string {
        const today = new Date();
        const currentDay = today.getDay();
        const diff = (targetDay - currentDay + 7) % 7; // 0 wenn heute//
        const next = new Date(today);
        next.setDate(today.getDate() + diff);
        return next.toISOString().split("T")[0];
    }

    function selectWeekday(day: number) {
        selectedWeekday = day;
        selectedDate = nextDateForWeekday(day);
        apply();
    }

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
        selectedWeekday = null;
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

    <div>
        {#each weekdays as wd}
            <button
                    class:active={selectedWeekday === wd.day}
                    on:click={() => {
                        if (selectedWeekday === wd.day) { selectedWeekday = null; selectedDate = null; apply(); }
                        else selectWeekday(wd.day);
                    }}
            >
                {wd.label}
            </button>
        {/each}
    </div>

    <div>
        {#each timeSlots as slot}
            <button
                    class:active={selectedTimeSlot === slot}
                    on:click={() => { selectedTimeSlot = selectedTimeSlot === slot ? null : slot; apply(); }}
            >
                {slot.label}
            </button>
        {/each}
    </div>

    <button on:click={reset}>Filter zurücksetzen</button>
</div>

<style>
    div {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
        margin-bottom: 6px;
    }

    select,
    button {
        font-family: inherit;
        font-size: 0.85rem;
        padding: 5px 10px;
        border: 1px solid #ccc;
        border-radius: 4px;
        background: white;
        cursor: pointer;
        transition: border-color 0.15s;
    }

    select:hover,
    button:hover {
        border-color: #888;
    }

    select:focus,
    button:focus {
        outline: none;
        border-color: #555;
    }

    .active {
        font-weight: bold;
        border-color: #333;
        background: #f0f0f0;
        outline: none;
    }
</style>