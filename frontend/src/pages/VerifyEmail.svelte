<script lang="ts">
    import { verifyEmail } from "../auth";
    import Link from "../lib/Link.svelte";

    const token = new URLSearchParams(window.location.search).get("token") ?? "";

    let submitting = !!token;
    let errorMessage: string | null = null;
    let success = false;

    async function run() {
        const error = await verifyEmail(token);
        submitting = false;
        if (error) {
            errorMessage = error;
        } else {
            success = true;
        }
    }

    if (token) {
        run();
    }
</script>

<div class="page">
    {#if !token}
        <p class="notice">
            Ungültiger Link. Bitte fordere einen neuen an.
            <Link href="/login">Zum Login</Link>
        </p>
    {:else if submitting}
        <p class="notice">Bestätigt…</p>
    {:else if success}
        <p class="notice">
            Deine E-Mail-Adresse wurde bestätigt. Du kannst dich jetzt einloggen.
            <Link href="/login">Zum Login</Link>
        </p>
    {:else}
        <p class="warning">
            {errorMessage}
            <Link href="/login">Zum Login</Link>
        </p>
    {/if}
</div>

<style>
    .page {
        flex: 1;
        display: flex;
        justify-content: center;
        padding: 24px 16px;
    }

    .notice {
        color: var(--color-text-muted);
        font-size: 0.9rem;
        text-align: center;
        max-width: 420px;
    }

    .warning {
        color: var(--color-error);
        font-size: 0.9rem;
        text-align: center;
        max-width: 420px;
    }
</style>
