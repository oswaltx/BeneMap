<script lang="ts">
    import { register } from "../auth";
    import { navigate } from "../router";
    import type { Role } from "../auth";

    let email = "";
    let password = "";
    let name = "";
    let role: Role = "USER";
    let submitting = false;
    let errorMessage: string | null = null;

    async function handleSubmit() {
        submitting = true;
        errorMessage = null;
        const error = await register(email, password, name, role);
        submitting = false;
        if (error) {
            errorMessage = error;
        } else {
            navigate("/");
        }
    }
</script>

<div class="page">
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
</style>
