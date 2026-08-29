<script lang="ts">
    import type { Snippet } from "svelte";

    interface Props {
        title: string;
        children?: Snippet;
    }

    let { title, children }: Props = $props();
</script>

<div class="page">
    <div class="content">
        <h1>{title}</h1>
        {@render children?.()}
    </div>
</div>

<style>
    .page {
        flex: 1;
        display: flex;
        justify-content: center;
        padding: 24px 16px;
    }

    .content {
        width: 100%;
        max-width: 720px;
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        padding: 24px 28px;
        box-shadow: var(--shadow-panel);
        color: var(--color-text);
    }

    .content :global(h2) {
        margin-top: 24px;
        margin-bottom: 8px;
        font-size: 1.1rem;
    }

    .content :global(p) {
        margin: 0 0 12px;
        line-height: 1.6;
    }

    .content :global(a) {
        color: var(--color-primary);
    }

    .content :global(.placeholder) {
        color: var(--color-error);
        font-weight: 600;
    }
</style>
