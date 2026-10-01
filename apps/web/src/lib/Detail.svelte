<script>
  import { untrack } from 'svelte'
  import Icon from './Icon.svelte'
  import { categories, category, displayTitle, relativeTime, sourceName } from './feed.js'
  import { remove, setCategory, setSeen, tell } from './stash.svelte.js'

  let { save, onclose } = $props()
  const cat = $derived(category(save))
  const sourceIcons = { instagram: 'photo_camera', youtube: 'smart_display', tiktok: 'smart_display', reddit: 'forum', x: 'chat', spotify: 'music_note', maps: 'location_on' }

  // Opening a save is seeing it, as in the apps. Only when another one opens, not when it's marked unseen here.
  $effect(() => {
    save.id
    untrack(() => setSeen(save, true))
  })

  async function share() {
    if (navigator.share) {
      navigator.share({ title: displayTitle(save), url: save.url }).catch(() => {})
    } else {
      await navigator.clipboard.writeText(save.url)
      tell('Link copied.')
    }
  }

  // Props are live: once the panel closes, `save` reads as nothing. Hand it over first.
  function destroy() {
    remove(save)
    onclose()
  }
</script>

<article class="detail" aria-label={displayTitle(save)}>
  <div class="bar">
    <button class="quiet" onclick={onclose} aria-label="Close"><Icon name="close" size={20} /></button>
    <span class="actions">
      <button class="quiet" onclick={() => setSeen(save, save.opened_at == null)} aria-label={save.opened_at == null ? 'Mark as seen' : 'Mark as unseen'} title={save.opened_at == null ? 'Mark as seen' : 'Mark as unseen'}>
        <Icon name={save.opened_at == null ? 'visibility' : 'visibility_off'} size={20} />
      </button>
      <button class="quiet" onclick={share} aria-label="Share" title="Share"><Icon name="share" size={20} /></button>
      <button class="quiet" onclick={destroy} aria-label="Delete" title="Delete"><Icon name="delete" size={20} /></button>
    </span>
  </div>

  {#if save.thumbnail_url}
    <img class="media" src={save.thumbnail_url} alt="" referrerpolicy="no-referrer" onerror={(e) => (e.currentTarget.hidden = true)} />
  {/if}

  <div class="body">
    <div class="labels">
      <span class="source"><Icon name={sourceIcons[save.source] ?? 'language'} size={18} />{sourceName(save)}</span>
      <label class="category" style:--tint={cat.tint}>
        <span class="visually-hidden">Category</span>
        {cat.name}
        <select value={categories[save.category] ? save.category : 'other'} onchange={(e) => setCategory(save, e.currentTarget.value)}>
          {#each Object.entries(categories) as [key, { name }] (key)}
            <option value={key}>{name}</option>
          {/each}
        </select>
        <Icon name="expand_more" size={18} />
      </label>
    </div>

    <h1 class:serif={save.content_type === 'article'}>{displayTitle(save)}</h1>
    {#if save.author}<p class="author">{save.author}</p>{/if}

    <a class="primary open" href={save.url} target="_blank" rel="noopener noreferrer" onclick={() => setSeen(save, true)}>
      <Icon name="open_in_new" size={20} />Open original
    </a>

    {#if save.summary}
      <h2>Summary</h2>
      <p class="text">{save.summary}</p>
    {/if}
    {#if save.description_text}<p class="text" class:serif={save.content_type === 'article'}>{save.description_text}</p>{/if}

    {#if save.tags.length}
      <ul class="tags">
        {#each save.tags as tag (tag)}<li>#{tag}</li>{/each}
      </ul>
    {/if}

    <footer>
      {#if save.status === 'pending'}<p>Title and picture arrive once one of your phones syncs.</p>{/if}
      {#if save.status === 'failed'}<p>Details couldn't be loaded. The link is saved.</p>{/if}
      <p>Saved {relativeTime(save.last_saved_at)}</p>
      <p class="url">{save.url}</p>
    </footer>
  </div>
</article>

<style>
  .detail {
    position: relative;
    min-height: 100%;
    background: var(--paper);
  }

  .bar {
    position: sticky;
    top: 0;
    z-index: 2;
    display: flex;
    justify-content: space-between;
    padding: 14px 16px;
    pointer-events: none;
  }

  .bar :global(button) {
    pointer-events: auto;
    background: color-mix(in srgb, var(--paper) 82%, transparent);
    backdrop-filter: blur(16px);
  }

  .actions {
    display: flex;
    gap: 8px;
  }

  .media {
    width: 100%;
    max-height: 420px;
    margin-top: -68px;
    object-fit: cover;
    background: var(--wash);
  }

  .body {
    display: grid;
    gap: 16px;
    padding: 20px 28px 40px;
  }

  .labels {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    color: var(--ink-soft);
    font-size: var(--text-s);
  }

  .source {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }

  .category {
    position: relative;
    display: inline-flex;
    align-items: center;
    gap: 2px;
    padding-left: 15px;
  }

  .category::before {
    content: '';
    position: absolute;
    left: 0;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--tint);
  }

  /* Invisible over the label: the system menu, with the label sized to the current choice. */
  select {
    position: absolute;
    inset: 0;
    opacity: 0;
    cursor: pointer;
  }

  .category:has(select:focus-visible) {
    outline: 2px solid var(--accent);
    outline-offset: 4px;
    border-radius: 6px;
  }

  h1 {
    font-size: var(--text-xl);
    line-height: 1.12;
    overflow-wrap: anywhere;
  }

  .serif {
    font-family: ui-serif, 'New York', Georgia, serif;
    letter-spacing: -0.01em;
  }

  .author {
    margin-top: -8px;
    color: var(--ink-soft);
    font-size: var(--text-m);
    font-weight: 560;
  }

  .open {
    margin: 4px 0;
    min-height: 52px;
  }

  h2 {
    font-size: var(--text-m);
    letter-spacing: 0;
    margin-bottom: -10px;
  }

  .text {
    font-size: var(--text-m);
    line-height: 1.55;
    max-width: 62ch;
  }

  .text.serif {
    line-height: 1.62;
  }

  .tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .tags li {
    padding: 6px 13px;
    border-radius: 999px;
    background: var(--wash);
    font-size: var(--text-s);
    font-weight: 520;
  }

  footer {
    display: grid;
    gap: 4px;
    margin-top: 8px;
    color: var(--ink-soft);
    font-size: var(--text-xs);
  }

  .url {
    overflow-wrap: anywhere;
    user-select: all;
  }
</style>
