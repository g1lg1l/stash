<script>
  import Icon from './Icon.svelte'
  import { category } from './feed.js'

  let { save, class: className = '', iconSize = 26 } = $props()
  let failed = $state(false)
  const tint = $derived(category(save).tint)

  $effect(() => {
    save.thumbnail_url
    failed = false
  })
</script>

{#if save.thumbnail_url && !failed}
  <img class="thumb {className}" src={save.thumbnail_url} alt="" loading="lazy" referrerpolicy="no-referrer" onerror={() => (failed = true)} />
{:else}
  <!-- No picture yet (or it's gone): the category's own tile, as in the apps. -->
  <span class="thumb fallback {className}" style:--tint={tint}><Icon name={category(save).icon} size={iconSize} /></span>
{/if}

<style>
  .thumb {
    width: 100%;
    height: 100%;
    object-fit: cover;
    background: var(--wash);
  }

  .fallback {
    display: grid;
    place-items: center;
    background: color-mix(in srgb, var(--tint) 16%, var(--paper));
    color: var(--tint);
  }
</style>
