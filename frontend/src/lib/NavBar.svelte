<script lang="ts">
    import Link from "./Link.svelte";
    import { currentUser, logout } from "../auth";
    import { navigate } from "../router";

    async function handleLogout() {
        await logout();
        navigate("/");
    }
</script>

<nav>
    <span class="brand">Benemap</span>
    <div class="links">
        <Link href="/" activeClass="active">Home</Link>
        {#if $currentUser?.role === "ANBIETER"}
            <Link href="/add" activeClass="active">Aktivität hinzufügen</Link>
        {/if}
        {#if $currentUser}
            <Link href="/profile" activeClass="active">Mein Profil</Link>
        {/if}
        <Link href="/about" activeClass="active">About</Link>
        {#if $currentUser}
            <span class="user-info">Hallo {$currentUser.name}</span>
            <button class="logout" on:click={handleLogout}>Abmelden</button>
        {:else}
            <Link href="/login" activeClass="active">Login</Link>
            <Link href="/register" activeClass="active">Registrieren</Link>
        {/if}
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
        align-items: center;
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

    .user-info {
        color: var(--color-primary-text);
        font-size: 0.9rem;
        opacity: 0.85;
    }

    .logout {
        background: none;
        border: 1px solid var(--color-primary-text);
        color: var(--color-primary-text);
        padding: 4px 10px;
        font-size: 0.85rem;
    }

    .logout:hover {
        filter: none;
        opacity: 1;
        background: rgba(255, 255, 255, 0.1);
    }
</style>
