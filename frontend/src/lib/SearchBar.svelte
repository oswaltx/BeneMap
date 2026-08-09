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

<input
        type="search"
        bind:value={search}
        placeholder="Suche nach Name oder Adresse..."
        on:input={handleInput}
/>
