<script>
  import Thumb from './Thumb.svelte'
  import { byline, category, displayTitle } from './feed.js'

  /** sections: [{ title, saves }]; a null title draws no heading. */
  let { sections, selectedId = null, fresh = null, onselect } = $props()
</script>

{#each sections as section (section.title)}
  <section>
    {#if section.title}<h2>{section.title}</h2>{/if}
    <ul>
      {#each section.saves as save (save.id)}
        {@const cat = category(save)}
        <li class:fresh={save.id === fresh}>
          <button class="row" class:selected={save.id === selectedId} aria-current={save.id === selectedId} onclick={() => onselect(save.id)}>
            <span class="unseen" class:on={save.opened_at == null}>
              {#if save.opened_at == null}<span class="visually-hidden">Unseen.</span>{/if}
            </span>
            <span class="picture"><Thumb {save} /></span>
            <span class="text">
              <span class="title">{displayTitle(save)}</span>
              <span class="meta">
                <span class="category" style:--tint={cat.tint}>{cat.name}</span>
                <span class="by">{byline(save)}</span>
              </span>
            </span>
          </button>
        </li>
      {/each}
    </ul>
  </section>
{/each}

<style>
  section + section {
    margin-top: 36px;
  }

  h2 {
    margin: 0 0 6px 28px;
    color: var(--ink-soft);
    font-size: var(--text-s);
    font-weight: 600;
    letter-spacing: 0;
  }

  ul {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .row {
    display: grid;
    grid-template-columns: 12px 60px 1fr;
    align-items: center;
    gap: 16px;
    width: 100%;
    padding: 10px 14px 10px 8px;
    border: 0;
    border-radius: 18px;
    background: none;
    text-align: left;
    transition: background 120ms;
  }

  .row:hover {
    background: var(--wash);
  }

  .row.selected {
    background: var(--wash-strong);
  }

  .unseen.on {
    width: 9px;
    height: 9px;
    margin-left: 2px;
    border-radius: 50%;
    background: var(--accent);
  }

  .picture {
    width: 60px;
    height: 60px;
    overflow: hidden;
    border-radius: var(--radius-thumb);
  }

  .text {
    display: grid;
    gap: 3px;
    min-width: 0;
  }

  .title {
    display: -webkit-box;
    overflow: hidden;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    font-size: var(--text-m);
    font-weight: 600;
    line-height: 1.28;
    overflow-wrap: anywhere;
  }

  .meta {
    display: flex;
    gap: 14px;
    min-width: 0;
    color: var(--ink-soft);
    font-size: var(--text-s);
    white-space: nowrap;
  }

  .category {
    display: inline-flex;
    align-items: center;
    gap: 7px;
  }

  .category::before {
    content: '';
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--tint);
  }

  .by {
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* The one entrance: a link just stashed drops in under the paste bar. */
  .fresh .row {
    animation: arrive 700ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  @keyframes arrive {
    from {
      transform: translateY(-14px);
      opacity: 0;
      background: color-mix(in srgb, var(--accent) 14%, transparent);
    }
    60% {
      transform: none;
      opacity: 1;
      background: color-mix(in srgb, var(--accent) 10%, transparent);
    }
  }
</style>
