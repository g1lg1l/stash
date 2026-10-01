<script>
  import { tick } from 'svelte'
  import Icon from './lib/Icon.svelte'
  import SaveList from './lib/SaveList.svelte'
  import PasteBar from './lib/PasteBar.svelte'
  import Detail from './lib/Detail.svelte'
  import Explore from './lib/Explore.svelte'
  import Welcome from './lib/Welcome.svelte'
  import Settings from './lib/Settings.svelte'
  import EmailLink from './lib/EmailLink.svelte'
  import { add, emailLink, load, stash, tell } from './lib/stash.svelte.js'
  import { byDay, categories, category, greeting, search } from './lib/feed.js'

  let route = $state(parse(location.hash))
  let selectedId = $state(null)
  let settingsOpen = $state(false)
  let query = $state('')
  let searchField = $state()
  let fresh = $state(null)
  let lastLoad = 0
  let landing = $state(emailLink)

  /** #/explore, #/search, #/category/food; anything else is Home. */
  function parse(hash) {
    const [view, arg] = hash.replace(/^#\/?/, '').split('/')
    if (view === 'explore' || view === 'search') return { view }
    if (view === 'category' && categories[arg]) return { view, category: arg }
    return { view: 'home' }
  }

  const saves = $derived(stash.saves ?? [])
  const selected = $derived(saves.find((save) => save.id === selectedId) ?? null)
  const unseen = $derived(saves.filter((save) => save.opened_at == null).length)
  const counts = $derived(
    Object.entries(categories)
      .map(([key, cat]) => ({ key, cat, count: saves.filter((save) => category(save) === cat).length }))
      .filter((entry) => entry.count),
  )
  const inCategory = $derived(route.category ? saves.filter((save) => category(save) === categories[route.category]) : [])
  const results = $derived(search(query, saves))

  $effect(() => {
    if (stash.session && stash.saves === null) refresh(true)
  })

  /** On sign-in, and whenever the tab comes back, so saves from the phones show up. */
  async function refresh(force = false) {
    if (!stash.session || (!force && Date.now() - lastLoad < 20_000)) return
    lastLoad = Date.now()
    try {
      await load()
    } catch (error) {
      tell(error.message)
    }
  }

  async function stashText(text) {
    const save = await add(text)
    if (!save) return
    fresh = save.id
    if (route.view !== 'home') location.hash = ''
  }

  /** ⌘V anywhere that isn't a text field stashes the link. */
  function onpaste(event) {
    if (!stash.saves || event.target.closest?.('input, textarea, select, [contenteditable]')) return
    const text = event.clipboardData?.getData('text')
    if (!text) return
    event.preventDefault()
    stashText(text)
  }

  async function onkeydown(event) {
    if (event.key === 'Escape' && selectedId && !settingsOpen) selectedId = null
    const typing = event.target.closest?.('input, textarea, select')
    if (((event.metaKey || event.ctrlKey) && event.key === 'k') || (event.key === '/' && !typing)) {
      event.preventDefault()
      location.hash = '#/search'
      route = { view: 'search' } // hashchange comes later, and the field must exist to take focus.
      await tick()
      searchField?.focus()
    }
  }

  const mac = /Mac|iPhone|iPad/.test(navigator.platform)
  const nav = [
    { view: 'home', href: '#/', label: 'Home', icon: 'home', active: 'home_filled' },
    { view: 'explore', href: '#/explore', label: 'Explore', icon: 'grid_view', active: 'grid_view_filled' },
    { view: 'search', href: '#/search', label: 'Search', icon: 'search', active: 'search' },
  ]
</script>

<svelte:window
  onhashchange={() => (route = parse(location.hash))}
  onfocus={() => refresh()}
  {onpaste}
  {onkeydown}
/>
<svelte:document onvisibilitychange={() => document.visibilityState === 'visible' && refresh()} />

{#if landing}
  <EmailLink link={landing} ondone={() => (landing = null)} />
{:else if !stash.session}
  <Welcome />
{:else}
  <div class="shell" class:open={selected}>
    <aside class="sidebar">
      <a class="brand" href="#/">
        <img src="{import.meta.env.BASE_URL}icon.png" alt="" width="32" height="32" />
        Stash
      </a>
      <nav aria-label="Main">
        {#each nav as item (item.view)}
          <a href={item.href} aria-current={route.view === item.view ? 'page' : undefined}>
            <Icon name={route.view === item.view ? item.active : item.icon} size={22} />
            {item.label}
            {#if item.view === 'search'}<kbd>{mac ? '⌘K' : 'Ctrl K'}</kbd>{/if}
          </a>
        {/each}
      </nav>
      {#if counts.length}
        <h2>Categories</h2>
        <nav aria-label="Categories" class="categories">
          {#each counts as { key, cat, count } (key)}
            <a href="#/category/{key}" style:--tint={cat.tint} aria-current={route.category === key ? 'page' : undefined}>
              <span class="dot"></span>{cat.name}<span class="count">{count}</span>
            </a>
          {/each}
        </nav>
      {/if}
      <button class="account" onclick={() => (settingsOpen = true)}>
        <Icon name="account_circle" size={22} />
        <span>{stash.session.email}</span>
        <Icon name="settings" size={20} />
      </button>
    </aside>

    <main>
      {#if route.view === 'home'}
        <header class="hello">
          <h1>{greeting()}</h1>
          {#if stash.saves}<p>{saves.length} saved, {unseen} unseen</p>{/if}
        </header>
        <PasteBar onstash={stashText} />
        <div class="feed">
          {#if stash.saves === null}
            <p class="empty">Opening your stash…</p>
          {:else if !saves.length}
            <div class="empty">
              <h2>Nothing saved yet</h2>
              <p>Paste a link above, or share one to Stash from your phone. It shows up here after the next sync.</p>
            </div>
          {:else}
            <SaveList sections={byDay(saves)} {selectedId} {fresh} onselect={(id) => (selectedId = id)} />
          {/if}
        </div>
      {:else if route.view === 'explore'}
        <Explore {saves} onselect={(id) => (selectedId = id)} />
      {:else if route.view === 'category'}
        {@const cat = categories[route.category]}
        <header class="hello" style:--tint={cat.tint}>
          <h1 class="titled">{cat.name}</h1>
          <p>{inCategory.length === 1 ? '1 save' : `${inCategory.length} saves`}</p>
        </header>
        <div class="feed">
          {#if inCategory.length}
            <SaveList sections={byDay(inCategory)} {selectedId} onselect={(id) => (selectedId = id)} />
          {:else}
            <p class="empty">Nothing in {cat.name} yet.</p>
          {/if}
        </div>
      {:else}
        <header class="hello">
          <h1>Search</h1>
        </header>
        <label class="search">
          <Icon name="search" />
          <span class="visually-hidden">Search your stash</span>
          <input bind:this={searchField} bind:value={query} type="search" placeholder="Titles, notes, tags, links" autocomplete="off" />
        </label>
        <div class="feed">
          {#if !query.trim()}
            <p class="empty">Every word has to match, in any order. Accents don't matter.</p>
          {:else if !results.length}
            <p class="empty">Nothing matches “{query.trim()}”. Try fewer words.</p>
          {:else}
            <SaveList sections={[{ title: null, saves: results }]} {selectedId} onselect={(id) => (selectedId = id)} />
          {/if}
        </div>
      {/if}
    </main>

    {#if selected}
      <button class="scrim" aria-label="Close" tabindex="-1" onclick={() => (selectedId = null)}></button>
      <div class="panel">
        {#key selectedId}
          <Detail save={selected} onclose={() => (selectedId = null)} />
        {/key}
      </div>
    {/if}

    <nav class="tabbar" aria-label="Main">
      {#each nav as item (item.view)}
        <a href={item.href} aria-current={route.view === item.view ? 'page' : undefined}>
          <Icon name={route.view === item.view ? item.active : item.icon} size={24} />
          {item.label}
        </a>
      {/each}
      <button onclick={() => (settingsOpen = true)}><Icon name="settings" size={24} />Settings</button>
    </nav>
  </div>
  <Settings bind:open={settingsOpen} />
{/if}

{#if stash.notice}
  <div class="notice" role="status">
    <span>{stash.notice.text}</span>
    {#if stash.notice.undo}
      <button onclick={() => (stash.notice.undo(), (stash.notice = null))}>Undo</button>
    {/if}
  </div>
{/if}

<style>
  .shell {
    display: grid;
    grid-template-columns: var(--sidebar) minmax(0, 1fr);
    min-height: 100dvh;
  }

  .shell.open {
    grid-template-columns: var(--sidebar) minmax(0, 1fr) var(--detail);
  }

  /* Sidebar */

  .sidebar {
    position: sticky;
    top: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
    height: 100dvh;
    padding: 22px 14px 16px;
    border-right: 1px solid var(--line);
    overflow-y: auto;
  }

  .brand {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 0 10px 22px;
    font-size: var(--text-l);
    font-weight: 720;
    letter-spacing: -0.03em;
    text-decoration: none;
  }

  .brand img {
    border-radius: 22%;
  }

  .sidebar nav a {
    display: flex;
    align-items: center;
    gap: 12px;
    min-height: 42px;
    padding: 0 12px;
    border-radius: 12px;
    font-weight: 560;
    text-decoration: none;
  }

  .sidebar nav a:hover {
    background: var(--wash);
  }

  .sidebar nav a[aria-current='page'] {
    background: var(--wash-strong);
    color: var(--accent);
  }

  kbd {
    margin-left: auto;
    color: var(--ink-soft);
    font: inherit;
    font-size: var(--text-xs);
  }

  .sidebar h2 {
    margin: 26px 12px 6px;
    color: var(--ink-soft);
    font-size: var(--text-s);
    font-weight: 600;
    letter-spacing: 0;
  }

  .categories a {
    min-height: 38px;
    font-weight: 500;
  }

  .categories a[aria-current='page'] {
    color: var(--ink);
  }

  .dot {
    width: 8px;
    height: 8px;
    margin: 0 4px 0 6px;
    border-radius: 50%;
    background: var(--tint);
  }

  .count {
    margin-left: auto;
    color: var(--ink-soft);
    font-size: var(--text-s);
    font-variant-numeric: tabular-nums;
  }

  .account {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: auto;
    padding: 10px 12px;
    border: 0;
    border-radius: 14px;
    background: none;
    color: var(--ink-soft);
    font-size: var(--text-s);
    text-align: left;
  }

  .account span {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .account:hover {
    background: var(--wash);
    color: var(--ink);
  }

  /* Main column */

  main {
    width: 100%;
    max-width: 780px;
    padding: 56px clamp(20px, 4vw, 56px) 96px;
  }

  .hello h1 {
    font-size: var(--text-hero);
  }

  .hello p {
    margin-top: 10px;
    color: var(--ink-soft);
    font-size: var(--text-m);
  }

  .titled {
    display: flex;
    align-items: center;
    gap: 0.28em;
  }

  .titled::before {
    content: '';
    width: 0.28em;
    height: 0.28em;
    border-radius: 50%;
    background: var(--tint);
  }

  .feed {
    margin-top: 40px;
    margin-left: -8px;
  }

  .empty {
    margin-left: 8px;
    color: var(--ink-soft);
  }

  .empty h2 {
    color: var(--ink);
    font-size: var(--text-xl);
    margin-bottom: 8px;
  }

  .empty p {
    max-width: 48ch;
  }

  .search {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 28px;
    padding: 0 20px;
    border-radius: var(--radius-card);
    background: var(--wash);
    color: var(--ink-soft);
  }

  .search:focus-within {
    box-shadow: 0 0 0 2px var(--accent);
  }

  .search input {
    flex: 1;
    min-width: 0;
    height: 60px;
    border: 0;
    outline: 0;
    background: none;
    color: var(--ink);
    font-size: var(--text-l);
    font-weight: 500;
  }

  /* Detail */

  .panel {
    position: sticky;
    top: 0;
    height: 100dvh;
    overflow-y: auto;
    border-left: 1px solid var(--line);
    background: var(--paper);
    animation: slide 260ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  @keyframes slide {
    from {
      transform: translateX(24px);
      opacity: 0;
    }
  }

  .scrim {
    display: none;
  }

  /* Notice */

  .notice {
    position: fixed;
    left: 50%;
    bottom: 28px;
    z-index: 20;
    display: flex;
    align-items: center;
    gap: 18px;
    max-width: calc(100vw - 32px);
    padding: 14px 22px;
    border-radius: 999px;
    background: var(--ink);
    color: var(--paper);
    font-size: var(--text-s);
    font-weight: 560;
    translate: -50% 0;
    box-shadow: var(--lift);
    animation: rise 220ms cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .notice button {
    padding: 0;
    border: 0;
    background: none;
    color: var(--accent);
    font-weight: 700;
  }

  @keyframes rise {
    from {
      translate: -50% 12px;
      opacity: 0;
    }
  }

  .tabbar {
    display: none;
  }

  /* Narrower: the detail floats over the list instead of taking a column. */
  @media (max-width: 1180px) {
    .shell.open {
      grid-template-columns: var(--sidebar) minmax(0, 1fr);
    }

    .panel {
      position: fixed;
      inset: 0 0 0 auto;
      z-index: 10;
      width: min(var(--detail), 100vw);
      box-shadow: var(--lift);
    }

    .scrim {
      position: fixed;
      inset: 0;
      z-index: 9;
      display: block;
      border: 0;
      background: var(--scrim);
      cursor: default;
    }
  }

  /* Phone: tabs at the bottom, the detail full screen. */
  @media (max-width: 760px) {
    .shell,
    .shell.open {
      grid-template-columns: minmax(0, 1fr);
    }

    .sidebar {
      display: none;
    }

    main {
      padding: 28px 16px 120px;
    }

    .feed {
      margin-top: 28px;
    }

    .panel {
      width: 100vw;
      border-left: 0;
    }

    .tabbar {
      position: fixed;
      inset: auto 0 0;
      z-index: 5;
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      padding: 8px 8px calc(8px + env(safe-area-inset-bottom));
      border-top: 1px solid var(--line);
      background: color-mix(in srgb, var(--paper) 86%, transparent);
      backdrop-filter: blur(20px);
    }

    .tabbar a,
    .tabbar button {
      display: grid;
      justify-items: center;
      gap: 2px;
      padding: 4px 0;
      border: 0;
      background: none;
      color: var(--ink-soft);
      font-size: 0.6875rem;
      font-weight: 600;
      text-decoration: none;
    }

    .tabbar [aria-current='page'] {
      color: var(--accent);
    }

    .notice {
      bottom: calc(88px + env(safe-area-inset-bottom));
    }
  }
</style>
