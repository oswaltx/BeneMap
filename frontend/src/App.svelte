<script>
    import { onMount } from "svelte";
    import NavBar from "./lib/NavBar.svelte";
    import Router from "./lib/Router.svelte";
    import { fetchCurrentUser } from "./auth";

    onMount(() => {
        fetchCurrentUser();
    });
</script>
<NavBar />
<Router />

