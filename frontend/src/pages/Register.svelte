<script lang="ts">
    import { register, resendVerification } from "../auth";
    import type { Role } from "../auth";

    let email = "";
    let password = "";
    let name = "";
    let role: Role = "USER";
    let submitting = false;
    let errorMessage: string | null = null;
    let registered = false;
    let resendStatus: string | null = null;
    let resendSubmitting = false;

    async function handleSubmit() {
        submitting = true;
        errorMessage = null;
        const error = await register(email, password, name, role);
        submitting = false;
        if (error) {
            errorMessage = error;
        } else {
            registered = true;
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
    {#if registered}
        <div class="notice-box">
            <h2>Fast geschafft!</h2>
            <p>
                Wir haben dir eine E-Mail an <strong>{email}</strong> mit einem Bestätigungslink
                geschickt. Klicke darauf, um dein Konto zu aktivieren.
            </p>
            <button type="button" on:click={handleResend} disabled={resendSubmitting}>
                {resendSubmitting ? "Wird gesendet…" : "E-Mail nicht angekommen? Erneut senden"}
            </button>
            {#if resendStatus}
                <p class="notice">{resendStatus}</p>
            {/if}
        </div>
    {:else}
        <form on:submit|preventDefault={handleSubmit}>
            <h2>Registrieren</h2>

            <label>
                Name
                <input type="text" bind:value={name} required />
            </label>

            <label>
                E-Mail
                <input type="email" bind:value={email} required />
            </label>

            <label>
                Passwort
                <input type="password" bind:value={password} required minlength="8" />
            </label>

            <label>
                Ich bin...
                <select bind:value={role}>
                    <option value="USER">Freiwillige:r (möchte Aktivitäten finden)</option>
                    <option value="ANBIETER">Anbieter (möchte Aktivitäten einstellen)</option>
                </select>
            </label>

            <button type="submit" disabled={submitting}>
                {submitting ? "Wird angelegt…" : "Registrieren"}
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

    input,
    select {
        font-family: inherit;
        font-size: 0.9rem;
        padding: 8px 10px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        color: var(--color-text);
    }

    input:focus,
    select:focus {
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

    .notice-box {
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

    .notice-box h2 {
        margin: 0;
    }

    .notice-box p {
        margin: 0;
        line-height: 1.5;
    }

    .notice-box button {
        align-self: flex-start;
    }

    .notice {
        color: var(--color-text-muted);
        font-size: 0.85rem;
        margin: 0;
    }
</style>
