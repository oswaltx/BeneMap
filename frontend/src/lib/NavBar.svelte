<script lang="ts">
    import Link from "./Link.svelte";
</script>

<nav>
    <span class="brand">Benemap</span>
    <div class="links">
        <Link href="/" activeClass="active">Home</Link>
        <Link href="/add" activeClass="active">Aktivität hinzufügen</Link>
        <Link href="/about" activeClass="active">About</Link>
    </div>
</nav>

<style>
    nav {
        display: flex;
        align-items: center;
        justify-content: space-between;
        background: var(--color-primary);
        color: var(--color-primary-text);
        padding: 12px 20px;
        flex-wrap: wrap;
        gap: 8px;
    }

    .brand {
        font-weight: 700;
        font-size: 1.2rem;
        color: var(--color-primary-text);
    }

    .links {
        display: flex;
        gap: 16px;
        flex-wrap: wrap;
    }

    .links :global(a) {
        color: var(--color-primary-text);
        font-weight: 500;
        font-size: 0.9rem;
        opacity: 0.85;
    }

    .links :global(a:hover),
    .links :global(a.active) {
        opacity: 1;
        text-decoration: underline;
    }
</style>
