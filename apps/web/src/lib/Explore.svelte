<script>
  import Thumb from './Thumb.svelte'
  import { categories, category, displayTitle, rediscovery, relativeTime } from './feed.js'

  let { saves, onselect } = $props()
  const picks = $derived(rediscovery(saves))
  // Each category with saves, shown by its newest picture, as in the apps.
  const tiles = $derived(
    Object.entries(categories)
      .map(([key, cat]) => {
        const inside = saves.filter((save) => category(save) === cat)
        return { key, cat, count: inside.length, cover: inside.find((save) => save.thumbnail_url) ?? inside[0] }
      })
      .filter((tile) => tile.count),
  )
</script>

<header>
  <h1>Explore</h1>
</header>

{#if picks.length}
  <section>
    <h2>Worth another look</h2>
    <p class="sub">You saved these a while ago.</p>
    <ul class="picks">
      {#each picks as save (save.id)}
        <li>
          <button onclick={() => onselect(save.id)}>
            <span class="cover"><Thumb {save} /></span>
            <span class="title">{displayTitle(save)}</span>
            <span class="when">Saved {relativeTime(save.last_saved_at)}</span>
          </button>
        </li>
      {/each}
    </ul>
  </section>
{/if}

<section>
  <h2>Categories</h2>
  {#if tiles.length}
    <ul class="tiles">
      {#each tiles as { key, cat, count, cover } (key)}
        <li>
          <a href="#/category/{key}" style:--tint={cat.tint} class:picture={cover.thumbnail_url}>
            <Thumb save={cover} iconSize={44} />
            <span class="label">
              <span class="name">{cat.name}</span>
              <span class="count">{count === 1 ? '1 save' : `${count} saves`}</span>
            </span>
          </a>
        </li>
      {/each}
    </ul>
  {:else}
    <p class="sub">Categories fill in as you save links.</p>
  {/if}
</section>

<style>
  h1 {
    font-size: var(--text-hero);
  }

  section {
    margin-top: 44px;
  }

  h2 {
    font-size: var(--text-xl);
  }

  .sub {
    margin-top: 6px;
    color: var(--ink-soft);
  }

  ul {
    margin: 18px 0 0;
    padding: 0;
    list-style: none;
  }

  .picks {
    display: grid;
    grid-auto-flow: column;
    grid-auto-columns: minmax(220px, 260px);
    gap: 18px;
    overflow-x: auto;
    padding-bottom: 8px;
    scroll-snap-type: x mandatory;
  }

  .picks li {
    scroll-snap-align: start;
  }

  .picks button {
    display: grid;
    gap: 6px;
    width: 100%;
    padding: 0;
    border: 0;
    background: none;
    text-align: left;
  }

  .cover {
    aspect-ratio: 4 / 3;
    overflow: hidden;
    border-radius: var(--radius-card);
    margin-bottom: 6px;
  }

  .picks button:hover .cover {
    filter: brightness(0.94);
  }

  .title {
    display: -webkit-box;
    overflow: hidden;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    font-weight: 600;
    line-height: 1.28;
  }

  .when {
    color: var(--ink-soft);
    font-size: var(--text-s);
  }

  .tiles {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
    gap: 16px;
  }

  .tiles a {
    position: relative;
    display: block;
    aspect-ratio: 4 / 5;
    overflow: hidden;
    border-radius: var(--radius-card);
    text-decoration: none;
  }

  .tiles a.picture::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(to bottom, transparent 45%, rgb(0 0 0 / 0.62));
  }

  .tiles a:hover :global(.thumb) {
    scale: 1.03;
  }

  .tiles :global(.thumb) {
    transition: scale 300ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .label {
    position: absolute;
    inset: auto 18px 16px;
    z-index: 1;
    display: grid;
    color: var(--ink);
  }

  .picture .label {
    color: #fff;
  }

  .name {
    font-size: var(--text-l);
    font-weight: 700;
    letter-spacing: -0.02em;
  }

  .count {
    font-size: var(--text-s);
    opacity: 0.85;
  }
</style>
