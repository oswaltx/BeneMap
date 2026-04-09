<script lang="ts">
  import { navigate, route } from "../router";
  import type { Snippet } from "svelte";

  interface Props {
    href: string;
    class?: string;
    activeClass?: string;
    children?: Snippet;
  }

  let { href, class: className = "", activeClass = "", children }: Props = $props();

  let isActive = $derived($route === href);
  let finalClass = $derived(isActive && activeClass ? `${className} ${activeClass}` : className);

  function handleClick(e: MouseEvent) {
    e.preventDefault();
    navigate(href);
  }
</script>

<a {href} class={finalClass} onclick={handleClick}>
  {@render children?.()}
</a>
