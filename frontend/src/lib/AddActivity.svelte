<script lang="ts">
    let name = "";
    let description = "";
    let addressText = "";
    let category = "";
    let dateTime = "";

    let submitting = false;
    let statusMessage: string | null = null;
    let statusIsWarning = false;

    async function handleSubmit() {
        if (!name.trim()) {
            statusMessage = "Name ist ein Pflichtfeld.";
            statusIsWarning = true;
            return;
        }

        submitting = true;
        statusMessage = null;

        try {
            const res = await fetch("http://localhost:8080/add", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    name,
                    description: description || null,
                    addressText: addressText || null,
                    category: category || null,
                    dateTime: dateTime ? dateTime + ":00" : undefined,
                }),
            });

            if (!res.ok) {
                statusMessage = "Fehler beim Speichern. Bitte versuche es erneut.";
                statusIsWarning = true;
                return;
            }

            const saved = await res.json();
            if (saved.latitude == null || saved.longitude == null) {
                statusMessage = "Gespeichert — die Adresse konnte aber nicht gefunden werden, der Eintrag erscheint noch nicht auf der Karte.";
                statusIsWarning = true;
            } else {
                statusMessage = "Aktivität wurde gespeichert.";
                statusIsWarning = false;
            }

            name = "";
            description = "";
            addressText = "";
            category = "";
            dateTime = "";
        } finally {
            submitting = false;
        }
    }
</script>

<form on:submit|preventDefault={handleSubmit}>
    <label>
        Name *
        <input type="text" bind:value={name} required />
    </label>

    <label>
        Beschreibung
        <textarea bind:value={description}></textarea>
    </label>

    <label>
        Adresse
        <input type="text" bind:value={addressText} placeholder="Straße, Hausnummer, Stadt" />
    </label>

    <label>
        Kategorie
        <input type="text" bind:value={category} />
    </label>

    <label>
        Datum/Uhrzeit
        <input type="datetime-local" bind:value={dateTime} />
    </label>

    <button type="submit" disabled={submitting}>
        {submitting ? "Speichert…" : "Aktivität hinzufügen"}
    </button>

    {#if statusMessage}
        <p class:warning={statusIsWarning}>{statusMessage}</p>
    {/if}
</form>

<style>
    form {
        display: flex;
        flex-direction: column;
        gap: 10px;
        max-width: 420px;
        margin: 16px 0;
    }

    label {
        display: flex;
        flex-direction: column;
        gap: 4px;
        font-size: 0.9rem;
    }

    input,
    textarea {
        font-family: inherit;
        font-size: 0.9rem;
        padding: 6px 8px;
        border: 1px solid #ccc;
        border-radius: 4px;
    }

    button {
        align-self: flex-start;
        font-family: inherit;
        font-size: 0.9rem;
        padding: 6px 14px;
        border: 1px solid #333;
        border-radius: 4px;
        background: white;
        cursor: pointer;
    }

    button:disabled {
        opacity: 0.6;
        cursor: default;
    }

    p.warning {
        color: #a15c00;
    }
</style>
