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
        toggleCityOffers: boolean;
    }>();

    let selectedCategory: string | null = null;
    let selectedDate: string | null = null;
    let selectedWeekday: number | null = null;
    let selectedTimeSlot: { label: string; from: number; to: number } | null = null;
    let showCityOffers = false;

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
        showCityOffers = false;
        dispatch("toggleCityOffers", false);
        apply();
    }

    let expanded = false;

    $: activeCount = [selectedCategory, selectedWeekday, selectedTimeSlot, showCityOffers ? true : null].filter(
        (v) => v !== null
    ).length;
</script>

<div class="filter">
    <button
            class="toggle"
            class:active={activeCount > 0}
            on:click={() => (expanded = !expanded)}
    >
        Filter
        {#if activeCount > 0}<span class="badge">{activeCount}</span>{/if}
    </button>

    {#if expanded}
        <button
                class="backdrop"
                aria-label="Filter schließen"
                on:click={() => (expanded = false)}
        ></button>

        <div class="popover">
            <label class="group">
                <span class="group-label">Kategorie</span>
                <select bind:value={selectedCategory} on:change={apply}>
                    <option value={null}>Alle Kategorien</option>
                    {#each categories as cat}
                        <option value={cat}>{cat}</option>
                    {/each}
                </select>
            </label>

            <div class="group">
                <span class="group-label">Wochentag</span>
                <div class="pill-row">
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
            </div>

            <div class="group">
                <span class="group-label">Uhrzeit</span>
                <div class="pill-row">
                    {#each timeSlots as slot}
                        <button
                                class:active={selectedTimeSlot === slot}
                                on:click={() => { selectedTimeSlot = selectedTimeSlot === slot ? null : slot; apply(); }}
                        >
                            {slot.label}
                        </button>
                    {/each}
                </div>
            </div>

            <div class="group">
                <label class="checkbox-row">
                    <input
                        type="checkbox"
                        bind:checked={showCityOffers}
                        on:change={() => dispatch("toggleCityOffers", showCityOffers)}
                    />
                    Städtische Angebote (Köln) anzeigen
                </label>
            </div>

            <button class="reset" on:click={reset}>Filter zurücksetzen</button>
        </div>
    {/if}
</div>

<style>
    .filter {
        position: relative;
    }

    .toggle {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        font-family: inherit;
        font-size: 0.85rem;
        padding: 5px 10px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-surface);
        color: var(--color-text);
        cursor: pointer;
        transition: border-color 0.15s, background 0.15s;
    }

    .toggle:hover {
        border-color: var(--color-primary);
    }

    .toggle:focus,
    .toggle:focus-visible {
        outline: none;
        border-color: var(--color-primary);
    }

    .toggle.active {
        border-color: var(--color-primary);
        color: var(--color-primary);
        font-weight: 600;
    }

    .badge {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 16px;
        height: 16px;
        padding: 0 4px;
        border-radius: var(--radius-pill);
        background: var(--color-accent);
        color: var(--color-accent-text);
        font-size: 0.7rem;
        font-weight: 700;
    }

    .backdrop {
        position: fixed;
        inset: 0;
        z-index: 1500;
        background: transparent;
        border: none;
        padding: 0;
        cursor: default;
    }

    .popover {
        position: absolute;
        top: calc(100% + 8px);
        left: 0;
        width: min(360px, 90vw);
        z-index: 1600;
        display: flex;
        flex-direction: column;
        gap: 10px;
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        box-shadow: var(--shadow-panel);
        padding: 12px;
    }

    .group {
        display: flex;
        flex-direction: column;
        gap: 6px;
    }

    .group-label {
        font-size: 0.75rem;
        font-weight: 600;
        color: var(--color-text-muted);
    }

    .checkbox-row {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 0.85rem;
        color: var(--color-text);
        cursor: pointer;
    }

    .pill-row {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
    }

    select,
    .pill-row button,
    .reset {
        font-family: inherit;
        font-size: 0.85rem;
        padding: 5px 10px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        color: var(--color-text);
        cursor: pointer;
        transition: border-color 0.15s, background 0.15s;
    }

    select:hover,
    .pill-row button:hover,
    .reset:hover {
        border-color: var(--color-primary);
    }

    select:focus,
    .pill-row button:focus,
    .reset:focus {
        outline: none;
        border-color: var(--color-primary);
    }

    .pill-row button.active {
        font-weight: bold;
        border-color: var(--color-primary);
        background: var(--color-accent);
        color: var(--color-accent-text);
    }

    .reset {
        align-self: flex-start;
    }
</style>