<script lang="ts">
    import Link from "./Link.svelte";
    import { currentUser, logout } from "../auth";
    import { navigate, route } from "../router";

    let menuOpen = false;

    async function handleLogout() {
        menuOpen = false;
        await logout();
        navigate("/");
    }

    $: if ($route) menuOpen = false;
</script>

<nav>
    <div class="nav-row">
        <span class="brand">Benemap</span>
        <button
            class="hamburger"
            aria-label={menuOpen ? "Menü schließen" : "Menü öffnen"}
            aria-expanded={menuOpen}
            on:click={() => (menuOpen = !menuOpen)}
        >
            <span class="bar"></span>
            <span class="bar"></span>
            <span class="bar"></span>
        </button>
    </div>
    <div class="links" class:open={menuOpen}>
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
        position: relative;
        background: var(--color-primary);
        color: var(--color-primary-text);
        padding: 12px 20px;
    }

    .nav-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
    }

    .brand {
        font-weight: 700;
        font-size: 1.2rem;
        color: var(--color-primary-text);
    }

    .hamburger {
        display: none;
        flex-direction: column;
        justify-content: center;
        align-items: center;
        gap: 4px;
        width: 44px;
        height: 44px;
        background: none;
        border: none;
        padding: 0;
        cursor: pointer;
    }

    .hamburger .bar {
        width: 22px;
        height: 2px;
        background: var(--color-primary-text);
        border-radius: 2px;
    }

    .links {
        display: flex;
        align-items: center;
        gap: 16px;
        flex-wrap: wrap;
        margin-top: 4px;
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

    @media (max-width: 720px) {
        nav {
            padding: 8px 16px;
        }

        .hamburger {
            display: flex;
        }

        .links {
            display: none;
            flex-direction: column;
            align-items: stretch;
            position: absolute;
            top: 100%;
            left: 0;
            right: 0;
            margin-top: 0;
            padding: 8px 16px 16px;
            background: var(--color-primary);
            box-shadow: var(--shadow-panel);
            z-index: 1200;
            gap: 4px;
        }

        .links.open {
            display: flex;
        }

        .links :global(a) {
            padding: 12px 4px;
            min-height: 44px;
            display: flex;
            align-items: center;
        }

        .user-info {
            padding: 8px 4px;
        }

        .logout {
            align-self: flex-start;
            min-height: 44px;
        }
    }
</style>
