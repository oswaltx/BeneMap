<script lang="ts">
    import Link from "./Link.svelte";
</script>

<footer>
    <Link href="/impressum" activeClass="active">Impressum</Link>
    <Link href="/datenschutz" activeClass="active">Datenschutz</Link>
    <a href="https://github.com/oswaltx/BeneMap" target="_blank" rel="noopener noreferrer">Quellcode</a>
</footer>

<style>
    footer {
        display: flex;
        justify-content: center;
        gap: 16px;
        padding: 12px 20px;
        border-top: 1px solid var(--color-border);
        font-size: 0.85rem;
    }

    footer :global(a) {
        color: var(--color-text-muted);
    }

    footer :global(a:hover),
    footer :global(a.active) {
        color: var(--color-text);
        text-decoration: underline;
    }
</style>
