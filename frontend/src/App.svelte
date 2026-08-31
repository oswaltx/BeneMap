<script>
    import { onMount } from "svelte";
    import NavBar from "./lib/NavBar.svelte";
    import Router from "./lib/Router.svelte";
    import Footer from "./lib/Footer.svelte";
    import NoticeBanner from "./lib/NoticeBanner.svelte";
    import { fetchCurrentUser } from "./auth";

    onMount(() => {
        fetchCurrentUser();
    });
</script>
<NavBar />
<Router />
<Footer />
<NoticeBanner />

