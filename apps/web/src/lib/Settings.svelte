<script>
  import { deleteAccount, signOut, stash, tell } from './stash.svelte.js'

  let { open = $bindable(false) } = $props()
  let dialog = $state()
  let confirming = $state(false)
  let busy = $state(false)
  let theme = $state(storedTheme())

  function storedTheme() {
    try {
      return localStorage.getItem('theme') ?? 'system'
    } catch {
      return 'system'
    }
  }

  $effect(() => {
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  })

  function pick(value) {
    theme = value
    if (value === 'system') delete document.documentElement.dataset.theme
    else document.documentElement.dataset.theme = value
    try {
      if (value === 'system') localStorage.removeItem('theme')
      else localStorage.setItem('theme', value)
    } catch {}
  }

  async function destroy() {
    busy = true
    try {
      await deleteAccount()
      open = false
    } catch (error) {
      tell(error.message)
    } finally {
      busy = false
      confirming = false
    }
  }
</script>

<dialog bind:this={dialog} onclose={() => ((open = false), (confirming = false))} onclick={(e) => e.target === dialog && (open = false)} aria-labelledby="settings-title">
  <div class="sheet">
    <header>
      <h2 id="settings-title">Settings</h2>
      <button class="quiet" onclick={() => (open = false)} aria-label="Close">
        <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
      </button>
    </header>

    <section>
      <h3>Account</h3>
      <p class="email">{stash.session?.email}</p>
      <p class="note">Your phones sync with this account. What you change here reaches them on their next sync.</p>
      <div class="row">
        <button class="plain" onclick={() => ((open = false), signOut())}>Sign out</button>
        {#if confirming}
          <button class="plain danger" disabled={busy} onclick={destroy}>{busy ? 'Deleting…' : 'Delete account and saves on the server'}</button>
          <button class="plain" onclick={() => (confirming = false)}>Cancel</button>
        {:else}
          <button class="plain danger" onclick={() => (confirming = true)}>Delete account…</button>
        {/if}
      </div>
      {#if confirming}<p class="note">The copies on your phones stay there.</p>{/if}
    </section>

    <section>
      <h3>Appearance</h3>
      <div class="switch" role="radiogroup" aria-label="Appearance">
        {#each [['system', 'System'], ['light', 'Light'], ['dark', 'Dark']] as [value, label] (value)}
          <button role="radio" aria-checked={theme === value} onclick={() => pick(value)}>{label}</button>
        {/each}
      </div>
    </section>

    <section>
      <h3>About</h3>
      <p class="note">Stash is open source. <a href="https://github.com/g1lg1l/stash">See the code on GitHub</a>.</p>
    </section>
  </div>
</dialog>

<style>
  dialog {
    width: min(480px, calc(100vw - 32px));
    padding: 0;
    border: 0;
    border-radius: 28px;
    background: var(--sheet);
    color: var(--ink);
    box-shadow: var(--lift);
  }

  dialog::backdrop {
    background: var(--scrim);
  }

  .sheet {
    display: grid;
    gap: 28px;
    padding: 22px 26px 28px;
  }

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  h2 {
    font-size: var(--text-xl);
  }

  section {
    display: grid;
    gap: 8px;
  }

  h3 {
    color: var(--ink-soft);
    font-size: var(--text-s);
    font-weight: 600;
  }

  .email {
    font-size: var(--text-l);
    font-weight: 620;
    overflow-wrap: anywhere;
  }

  .note {
    color: var(--ink-soft);
    font-size: var(--text-s);
  }

  .row {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 6px;
  }

  .plain {
    min-height: 40px;
    padding: 0 16px;
    border: 0;
    border-radius: 999px;
    background: color-mix(in srgb, var(--ink) 7%, var(--sheet));
    font-weight: 600;
  }

  .plain:hover {
    background: color-mix(in srgb, var(--ink) 12%, var(--sheet));
  }

  .danger {
    color: #e5372e;
  }

  .switch {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    padding: 4px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--ink) 7%, var(--sheet));
  }

  .switch button {
    min-height: 38px;
    border: 0;
    border-radius: 999px;
    background: none;
    color: var(--ink-soft);
    font-weight: 600;
  }

  .switch [aria-checked='true'] {
    background: var(--raised);
    color: var(--ink);
    box-shadow: 0 1px 3px rgb(0 0 0 / 0.14);
  }
</style>
