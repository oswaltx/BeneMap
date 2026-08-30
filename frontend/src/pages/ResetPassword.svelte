<script lang="ts">
    import { resetPassword } from "../auth";
    import Link from "../lib/Link.svelte";

    const token = new URLSearchParams(window.location.search).get("token") ?? "";

    let newPassword = "";
    let confirmPassword = "";
    let submitting = false;
    let errorMessage: string | null = null;
    let success = false;

    async function handleSubmit() {
        if (newPassword !== confirmPassword) {
            errorMessage = "Die Passwörter stimmen nicht überein.";
            return;
        }
        submitting = true;
        errorMessage = null;
        const error = await resetPassword(token, newPassword);
        submitting = false;
        if (error) {
            errorMessage = error;
        } else {
            success = true;
        }
    }
</script>

<div class="page">
    {#if success}
        <p class="notice">
            Dein Passwort wurde geändert. Du kannst dich jetzt einloggen.
            <Link href="/login">Zum Login</Link>
        </p>
    {:else if !token}
        <p class="notice">
            Ungültiger Link. Bitte fordere einen neuen an.
            <Link href="/forgot-password">Passwort vergessen</Link>
        </p>
    {:else}
        <form on:submit|preventDefault={handleSubmit}>
            <h2>Neues Passwort setzen</h2>

            <label>
                Neues Passwort
                <input type="password" bind:value={newPassword} required minlength="8" />
            </label>

            <label>
                Passwort bestätigen
                <input type="password" bind:value={confirmPassword} required minlength="8" />
            </label>

            <button type="submit" disabled={submitting}>
                {submitting ? "Setzt zurück…" : "Passwort setzen"}
            </button>

            {#if errorMessage}
                <p class="warning">{errorMessage}</p>
            {/if}
        </form>
    {/if}
</div>

<style>
    .page {
        flex: 1;
        display: flex;
        justify-content: center;
        padding: 24px 16px;
    }

    form {
        display: flex;
        flex-direction: column;
        gap: 12px;
        width: 100%;
        max-width: 420px;
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        padding: 20px;
        box-shadow: var(--shadow-panel);
        height: fit-content;
    }

    label {
        display: flex;
        flex-direction: column;
        gap: 4px;
        font-size: 0.9rem;
        color: var(--color-text);
    }

    input {
        font-family: inherit;
        font-size: 0.9rem;
        padding: 8px 10px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        color: var(--color-text);
    }

    input:focus {
        outline: none;
        border-color: var(--color-primary);
    }

    button {
        align-self: flex-start;
    }

    button:disabled {
        opacity: 0.6;
        cursor: default;
    }

    p.warning {
        color: var(--color-error);
        font-size: 0.85rem;
        margin: 0;
    }

    .notice {
        color: var(--color-text-muted);
        font-size: 0.9rem;
        text-align: center;
        max-width: 420px;
    }
</style>
