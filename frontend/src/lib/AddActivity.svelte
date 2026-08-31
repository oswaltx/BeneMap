<script lang="ts">
    import Link from "./Link.svelte";
    import { currentUser, authChecked, fetchWithSessionCheck } from "../auth";
    import { ACTIVITY_CATEGORIES } from "./categories";
    import { API_BASE } from "./apiBase";

    let name = "";
    let description = "";
    let addressText = "";
    let category = "";
    let dateTime = "";
    let photoUrlsText = "";
    let maxParticipants = "";
    let isRecurring = false;
    let recurrenceCount = 1;
    let recurrenceUnit: "days" | "weeks" = "weeks";

    let submitting = false;
    let statusMessage: string | null = null;
    let statusIsWarning = false;

    async function handleSubmit() {
        if (!name.trim()) {
            statusMessage = "Name ist ein Pflichtfeld.";
            statusIsWarning = true;
            return;
        }

        if (isRecurring && (!recurrenceCount || recurrenceCount < 1)) {
            statusMessage = "Bitte eine Zahl ab 1 für die Wiederholung angeben.";
            statusIsWarning = true;
            return;
        }

        if (isRecurring && !dateTime) {
            statusMessage = "Für eine Wiederholung wird ein Startdatum benötigt.";
            statusIsWarning = true;
            return;
        }

        submitting = true;
        statusMessage = null;

        const baseBody = {
            name,
            description: description || null,
            addressText: addressText || null,
            category: category || null,
            dateTime: dateTime ? dateTime + ":00" : undefined,
            photoUrls: photoUrlsText.trim() || undefined,
            maxParticipants: maxParticipants ? Number(maxParticipants) : null,
        };

        const wasRecurring = isRecurring;
        const endpoint = isRecurring
            ? `${API_BASE}/add-recurring`
            : `${API_BASE}/add`;
        const body = isRecurring
            ? {
                  ...baseBody,
                  recurrenceIntervalDays: recurrenceUnit === "weeks" ? recurrenceCount * 7 : recurrenceCount,
              }
            : baseBody;

        try {
            const res = await fetchWithSessionCheck(endpoint, {
                method: "POST",
                credentials: "include",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body),
            });

            if (!res.ok) {
                statusMessage = "Fehler beim Speichern. Bitte versuche es erneut.";
                statusIsWarning = true;
                return;
            }

            const saved = await res.json();

            if (wasRecurring) {
                const activities: { latitude: number | null; longitude: number | null }[] = saved;
                const missingCoords = activities.some((a) => a.latitude == null || a.longitude == null);
                if (missingCoords) {
                    statusMessage = `${activities.length} Termine wurden gespeichert — die Adresse konnte aber nicht gefunden werden, sie erscheinen noch nicht auf der Karte.`;
                    statusIsWarning = true;
                } else {
                    statusMessage = `${activities.length} Termine wurden angelegt.`;
                    statusIsWarning = false;
                }
            } else if (saved.latitude == null || saved.longitude == null) {
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
            photoUrlsText = "";
            maxParticipants = "";
            isRecurring = false;
            recurrenceCount = 1;
            recurrenceUnit = "weeks";
        } catch (e) {
            statusMessage = "Server nicht erreichbar. Bitte versuche es später erneut.";
            statusIsWarning = true;
        } finally {
            submitting = false;
        }
    }
</script>

{#if !$authChecked}
    <div class="page"><p>Lädt…</p></div>
{:else if $currentUser?.role !== "ANBIETER"}
    <div class="page">
        <p class="notice">
            Nur eingeloggte Anbieter können Aktivitäten hinzufügen.
            <Link href="/login">Jetzt einloggen</Link> oder
            <Link href="/register">registrieren</Link>.
        </p>
    </div>
{:else}
    <div class="page">
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
                <select bind:value={category}>
                    <option value="">– bitte wählen –</option>
                    {#each ACTIVITY_CATEGORIES as cat}
                        <option value={cat}>{cat}</option>
                    {/each}
                </select>
            </label>

            <label>
                Datum/Uhrzeit
                <input type="datetime-local" bind:value={dateTime} />
            </label>

            <label>
                Foto-URLs (eine pro Zeile)
                <textarea bind:value={photoUrlsText} rows="3" placeholder={"https://...\nhttps://..."}></textarea>
            </label>

            <label>
                Maximale Teilnehmerzahl (optional)
                <input type="number" min="1" bind:value={maxParticipants} placeholder="unbegrenzt" />
            </label>

            <label class="checkbox-label">
                <input type="checkbox" bind:checked={isRecurring} />
                Wiederholt sich
            </label>

            {#if isRecurring}
                <div class="recurrence-fields">
                    <label>
                        Alle
                        <input type="number" min="1" bind:value={recurrenceCount} />
                    </label>
                    <label>
                        Einheit
                        <select bind:value={recurrenceUnit}>
                            <option value="days">Tage</option>
                            <option value="weeks">Wochen</option>
                        </select>
                    </label>
                </div>
            {/if}

            <button type="submit" disabled={submitting}>
                {submitting ? "Speichert…" : "Aktivität hinzufügen"}
            </button>

            {#if statusMessage}
                <p class:warning={statusIsWarning}>{statusMessage}</p>
            {/if}
        </form>
    </div>
{/if}

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

    .checkbox-label {
        flex-direction: row;
        align-items: center;
        gap: 8px;
    }

    .recurrence-fields {
        display: flex;
        gap: 12px;
    }

    .recurrence-fields label {
        flex: 1;
    }

    input,
    select,
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
    select:focus,
    textarea:focus {
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
