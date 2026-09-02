<script>
    import { onMount } from "svelte";
    import NavBar from "./lib/NavBar.svelte";
    import Router from "./lib/Router.svelte";
    import Footer from "./lib/Footer.svelte";
    import NoticeBanner from "./lib/NoticeBanner.svelte";
    import { fetchCurrentUser } from "./auth";
    import { route } from "./router";

    onMount(() => {
        fetchCurrentUser();
    });
</script>
<NavBar />
<NoticeBanner />
<Router />
<div class:map-page={$route === "/"}>
    <Footer />
</div>

<style>
    @media (max-width: 640px) {
        .map-page {
            display: none;
        }
    }
</style>

