<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import { fetchWithSessionCheck } from "../auth";
    import { API_BASE, resolvePhotoUrl } from "./apiBase";

    export let urls: string[] = [];
    export let maxPhotos = 10;

    const dispatch = createEventDispatcher<{ change: string[] }>();

    let dragOver = false;
    let uploading = false;
    let errorMessage: string | null = null;
    let usedBytes: number | null = null;
    let maxBytes: number | null = null;
    let fileInput: HTMLInputElement;

    async function refreshQuota() {
        try {
            const res = await fetchWithSessionCheck(`${API_BASE}/uploads/quota`, { credentials: "include" });
            if (res.ok) {
                const quota = await res.json();
                usedBytes = quota.usedBytes;
                maxBytes = quota.maxBytes;
            }
        } catch {
            // Speicheranzeige ist rein informativ — schlägt der Abruf fehl, bleibt sie leer.
        }
    }
    refreshQuota();

    function formatMb(bytes: number): string {
        return (bytes / (1024 * 1024)).toFixed(1);
    }

    async function uploadFiles(files: FileList | File[]) {
        errorMessage = null;
        const remaining = maxPhotos - urls.length;
        if (remaining <= 0) {
            errorMessage = `Maximal ${maxPhotos} ${maxPhotos === 1 ? "Foto" : "Fotos"}.`;
            return;
        }

        const toUpload = Array.from(files).slice(0, remaining);
        uploading = true;
        for (const file of toUpload) {
            if (!file.type.startsWith("image/")) {
                errorMessage = `"${file.name}" ist kein Bild.`;
                continue;
            }

            const formData = new FormData();
            formData.append("file", file);

            try {
                const res = await fetchWithSessionCheck(`${API_BASE}/uploads/photos`, {
                    method: "POST",
                    credentials: "include",
                    body: formData,
                });

                if (!res.ok) {
                    const body = await res.json().catch(() => null);
                    errorMessage = body?.error ?? `Hochladen von "${file.name}" fehlgeschlagen.`;
                    continue;
                }

                const result = await res.json();
                urls = [...urls, result.url];
                usedBytes = result.usedBytes;
                maxBytes = result.maxBytes;
                dispatch("change", urls);
            } catch (e) {
                errorMessage = "Server nicht erreichbar. Bitte versuche es später erneut.";
            }
        }
        uploading = false;
    }

    function handleDrop(event: DragEvent) {
        event.preventDefault();
        dragOver = false;
        if (event.dataTransfer?.files?.length) {
            uploadFiles(event.dataTransfer.files);
        }
    }

    function handleFileInput(event: Event) {
        const input = event.target as HTMLInputElement;
        if (input.files?.length) {
            uploadFiles(input.files);
        }
        // Ohne Zurücksetzen würde eine erneute Auswahl derselben Datei kein "change" mehr auslösen.
        input.value = "";
    }

    function removePhoto(index: number) {
        urls = urls.filter((_, i) => i !== index);
        dispatch("change", urls);
    }
</script>

<div class="dropzone-wrapper">
    <button
        type="button"
        class="dropzone"
        class:drag-over={dragOver}
        on:click={() => fileInput.click()}
        on:dragover|preventDefault={() => (dragOver = true)}
        on:dragleave={() => (dragOver = false)}
        on:drop={handleDrop}
        disabled={uploading || urls.length >= maxPhotos}
    >
        {#if uploading}
            Lädt hoch…
        {:else if urls.length >= maxPhotos}
            Maximal {maxPhotos} {maxPhotos === 1 ? "Foto" : "Fotos"} erreicht
        {:else}
            Bilder hierher ziehen oder klicken zum Auswählen
        {/if}
    </button>
    <input
        type="file"
        accept="image/*"
        multiple={maxPhotos > 1}
        bind:this={fileInput}
        on:change={handleFileInput}
        hidden
    />

    {#if usedBytes != null && maxBytes != null}
        <p class="quota">Fotospeicher: {formatMb(usedBytes)} / {formatMb(maxBytes)} MB belegt</p>
    {/if}

    {#if errorMessage}
        <p class="warning">{errorMessage}</p>
    {/if}

    {#if urls.length > 0}
        <div class="thumbnails">
            {#each urls as url, i (url)}
                <div class="thumb">
                    <img src={resolvePhotoUrl(url)} alt="" />
                    <button type="button" class="remove" on:click={() => removePhoto(i)} aria-label="Foto entfernen">×</button>
                </div>
            {/each}
        </div>
    {/if}
</div>

<style>
    .dropzone-wrapper {
        display: flex;
        flex-direction: column;
        gap: 8px;
    }

    .dropzone {
        font-family: inherit;
        font-size: 0.85rem;
        color: var(--color-text-muted);
        text-align: center;
        padding: 18px 10px;
        border: 2px dashed var(--color-border);
        border-radius: var(--radius-md);
        background: var(--color-bg);
        cursor: pointer;
    }

    .dropzone:hover,
    .dropzone.drag-over {
        border-color: var(--color-primary);
        color: var(--color-text);
    }

    .dropzone:disabled {
        cursor: default;
        opacity: 0.7;
    }

    .quota {
        margin: 0;
        font-size: 0.75rem;
        color: var(--color-text-muted);
    }

    p.warning {
        color: var(--color-error);
        font-size: 0.85rem;
        margin: 0;
    }

    .thumbnails {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
    }

    .thumb {
        position: relative;
        width: 64px;
        height: 64px;
        border-radius: var(--radius-md);
        overflow: hidden;
    }

    .thumb img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
    }

    .thumb .remove {
        position: absolute;
        top: 2px;
        right: 2px;
        width: 18px;
        height: 18px;
        line-height: 16px;
        padding: 0;
        border: none;
        border-radius: 50%;
        background: rgba(0, 0, 0, 0.6);
        color: #fff;
        font-size: 0.85rem;
        cursor: pointer;
    }
</style>
