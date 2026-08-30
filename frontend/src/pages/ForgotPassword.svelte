<script lang="ts">
    import { requestPasswordReset } from "../auth";

    let email = "";
    let submitting = false;
    let message: string | null = null;
    let messageIsWarning = false;

    async function handleSubmit() {
        submitting = true;
        message = null;
        const error = await requestPasswordReset(email);
        submitting = false;
        if (error) {
            message = error;
            messageIsWarning = true;
        } else {
            message = "Falls ein Konto mit dieser E-Mail existiert, wurde eine E-Mail mit einem Link zum Zurücksetzen verschickt.";
            messageIsWarning = false;
        }
    }
</script>

<div class="page">
    <form on:submit|preventDefault={handleSubmit}>
        <h2>Passwort vergessen</h2>

        <label>
            E-Mail
            <input type="email" bind:value={email} required />
        </label>

        <button type="submit" disabled={submitting}>
            {submitting ? "Wird gesendet…" : "Link anfordern"}
        </button>

        {#if message}
            <p class:warning={messageIsWarning}>{message}</p>
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
</style>
