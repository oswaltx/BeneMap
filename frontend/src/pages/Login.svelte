<script lang="ts">
    import { login, resendVerification } from "../auth";
    import { navigate } from "../router";
    import Link from "../lib/Link.svelte";

    let email = "";
    let password = "";
    let submitting = false;
    let errorMessage: string | null = null;
    let showResend = false;
    let resendStatus: string | null = null;
    let resendSubmitting = false;

    async function handleSubmit() {
        submitting = true;
        errorMessage = null;
        showResend = false;
        resendStatus = null;
        const result = await login(email, password);
        submitting = false;
        if (result.ok) {
            navigate("/");
        } else {
            errorMessage = result.error;
            showResend = result.unverified;
        }
    }

    async function handleResend() {
        resendSubmitting = true;
        resendStatus = null;
        await resendVerification(email);
        resendSubmitting = false;
        resendStatus = "Falls das Konto noch nicht bestätigt ist, haben wir einen neuen Link geschickt.";
    }
</script>

<div class="page">
    <form on:submit|preventDefault={handleSubmit}>
        <h2>Login</h2>

        <label>
            E-Mail
            <input type="email" bind:value={email} required />
        </label>

        <label>
            Passwort
            <input type="password" bind:value={password} required />
        </label>

        <button type="submit" disabled={submitting}>
            {submitting ? "Wird geprüft…" : "Einloggen"}
        </button>

        <Link href="/forgot-password">Passwort vergessen?</Link>

        {#if errorMessage}
            <p class="warning">{errorMessage}</p>
        {/if}

        {#if showResend}
            <button type="button" on:click={handleResend} disabled={resendSubmitting}>
                {resendSubmitting ? "Wird gesendet…" : "Bestätigungslink erneut senden"}
            </button>
        {/if}

        {#if resendStatus}
            <p class="notice">{resendStatus}</p>
        {/if}
    </form>
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
        font-size: 0.85rem;
        margin: 0;
    }
</style>
