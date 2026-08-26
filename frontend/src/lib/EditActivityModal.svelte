<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import { fetchWithSessionCheck } from "../auth";

    export let marker: {
        id: number;
        name: string;
        description: string;
        address: string;
        category: string;
        dateTime: string | null;
        photoUrls: string[];
    };

    const dispatch = createEventDispatcher<{ close: void; saved: void }>();

    let name = marker.name;
    let description = marker.description;
    let addressText = marker.address;
    let category = marker.category;
    let dateTime = marker.dateTime ? marker.dateTime.slice(0, 16) : "";
    let photoUrlsText = marker.photoUrls.join("\n");

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
            const res = await fetchWithSessionCheck(`http://localhost:8080/activities/${marker.id}`, {
                method: "PUT",
                credentials: "include",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    name,
                    description: description || null,
                    addressText: addressText || null,
                    category: category || null,
                    dateTime: dateTime ? dateTime + ":00" : undefined,
                    photoUrls: photoUrlsText.trim() || undefined,
                }),
            });

            if (!res.ok) {
                statusMessage = "Fehler beim Speichern. Bitte versuche es erneut.";
                statusIsWarning = true;
                if (res.status === 404) {
                    dispatch("saved");
                }
                return;
            }

            const saved = await res.json();
            if (saved.geocodingFailed || saved.activity.latitude == null || saved.activity.longitude == null) {
                statusMessage = "Gespeichert — aber ohne gültige Adresse erscheint die Aktivität nicht auf der Karte.";
                statusIsWarning = true;
            } else {
                statusMessage = "Aktivität wurde aktualisiert.";
                statusIsWarning = false;
            }
            dispatch("saved");
        } catch (e) {
            statusMessage = "Server nicht erreichbar. Bitte versuche es später erneut.";
            statusIsWarning = true;
        } finally {
            submitting = false;
        }
    }
</script>

<button class="backdrop" aria-label="Schließen" on:click={() => dispatch("close")}></button>
<div class="modal">
    <div class="modal-header">
        <h3>Aktivität bearbeiten</h3>
        <button class="close" on:click={() => dispatch("close")} aria-label="Schließen">×</button>
    </div>

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

        <label>
            Foto-URLs (eine pro Zeile)
            <textarea bind:value={photoUrlsText} rows="3" placeholder={"https://...\nhttps://..."}></textarea>
        </label>

        <button type="submit" disabled={submitting}>
            {submitting ? "Speichert…" : "Speichern"}
        </button>

        {#if statusMessage}
            <p class:warning={statusIsWarning}>{statusMessage}</p>
        {/if}
    </form>
</div>

<style>
    .backdrop {
        position: fixed;
        inset: 0;
        z-index: 2000;
        background: rgba(42, 42, 34, 0.4);
        border: none;
        padding: 0;
        cursor: default;
    }

    .modal {
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        z-index: 2001;
        background: var(--color-surface);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-panel);
        padding: 20px;
        width: min(420px, 90vw);
        max-height: 80vh;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: 12px;
    }

    .modal-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
    }

    .modal-header h3 {
        font-size: 1.05rem;
    }

    .close {
        background: none;
        border: none;
        font-size: 1.3rem;
        line-height: 1;
        cursor: pointer;
        color: var(--color-text-muted);
        padding: 0;
    }

    form {
        display: flex;
        flex-direction: column;
        gap: 12px;
    }

    label {
        display: flex;
        flex-direction: column;
        gap: 4px;
        font-size: 0.9rem;
        color: var(--color-text);
    }

    input,
    textarea {
        font-family: inherit;
        font-size: 0.9rem;
        padding: 8px 10px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        color: var(--color-text);
    }

    input:focus,
    textarea:focus {
        outline: none;
        border-color: var(--color-primary);
    }

    button[type="submit"] {
        align-self: flex-start;
    }

    button[type="submit"]:disabled {
        opacity: 0.6;
        cursor: default;
    }

    p.warning {
        color: var(--color-error);
        font-size: 0.85rem;
        margin: 0;
    }
</style>
