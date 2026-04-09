<script lang="ts">
  import { route, routes } from "../router";
  import type { Component } from "svelte";

  let currentComponent: Component | null = $derived(routes[$route] ?? routes["/"]);
</script>

{#if currentComponent}
  <svelte:component this={currentComponent} />
{/if}
