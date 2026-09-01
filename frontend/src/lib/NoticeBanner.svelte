<script lang="ts">
    import Link from "./Link.svelte";

    const STORAGE_KEY = "benemap-notice-dismissed-v1";

    let visible = $state(false);

    $effect(() => {
        try {
            visible = localStorage.getItem(STORAGE_KEY) !== "1";
        } catch {
            visible = true;
        }
    });

    function dismiss() {
        visible = false;
        try {
            localStorage.setItem(STORAGE_KEY, "1");
        } catch {
            // localStorage unavailable (e.g. blocked) — banner just reappears next visit
        }
    }
</script>

{#if visible}
    <div class="banner" role="note">
        <p>
            Benemap befindet sich in der <strong>Beta-Phase</strong> — es kann noch zu Änderungen
            und gelegentlichen Fehlern kommen. Wir setzen ein technisch notwendiges Cookie
            (Login-Session) sowie eine cookielose, anonymisierte Reichweitenmessung
            (selbst gehostetes Matomo) ein — keine Werbe-Cookies, kein Tracking über
            mehrere Seiten hinweg. Mehr dazu in der
            <Link href="/datenschutz">Datenschutzerklärung</Link>.
        </p>
        <button onclick={dismiss}>Verstanden</button>
    </div>
{/if}

<style>
    .banner {
        display: flex;
        align-items: center;
        gap: 16px;
        flex-wrap: wrap;
        padding: 12px 20px;
        background: var(--color-primary);
        color: var(--color-primary-text);
        flex-shrink: 0;
    }

    p {
        flex: 1;
        min-width: 240px;
        margin: 0;
        font-size: 0.85rem;
        line-height: 1.5;
    }

    p :global(a) {
        color: var(--color-primary-text);
        text-decoration: underline;
    }

    button {
        flex-shrink: 0;
        padding: 8px 18px;
        border: none;
        border-radius: var(--radius-md, 6px);
        background: var(--color-accent);
        color: var(--color-accent-text);
        font-weight: 600;
        cursor: pointer;
    }

    button:hover {
        filter: brightness(1.05);
    }
</style>
