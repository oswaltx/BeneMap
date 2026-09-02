<script lang="ts">
    import { createEventDispatcher } from "svelte";

    const dispatch = createEventDispatcher();

    let search = "";
    let timeout: ReturnType<typeof setTimeout>;

    function handleInput() {
        clearTimeout(timeout);

        timeout = setTimeout(() => {
            dispatch("search", search);
        }, 300);
    }
</script>

<style>
    input {
        width: 100%;
        font-family: inherit;
        font-size: 0.9rem;
        padding: 8px 12px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        color: var(--color-text);
    }
    input:focus {
        outline: none;
        border-color: var(--color-primary);
    }

    @media (max-width: 640px) {
        input {
            padding: 12px 14px;
            /* 16px avoids iOS Safari auto-zooming the page on focus */
            font-size: 16px;
        }
    }
</style>

<input
        type="search"
        bind:value={search}
        placeholder="Suche nach Name oder Adresse..."
        on:input={handleInput}
/>
