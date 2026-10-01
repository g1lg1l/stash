<script>
  import Icon from './Icon.svelte'

  let { onstash } = $props()
  let text = $state('')
  let busy = $state(false)
  const mac = /Mac|iPhone|iPad/.test(navigator.platform)

  async function submit(event) {
    event.preventDefault()
    if (!text.trim() || busy) return
    busy = true
    await onstash(text)
    busy = false
    text = ''
  }
</script>

<!-- Stash's icon is a stack of cards: the bar is the top one. -->
<form class="stack" onsubmit={submit}>
  <label class="bar">
    <span class="mark"><Icon name="add_link" /></span>
    <span class="visually-hidden">Link to save</span>
    <input bind:value={text} type="text" inputmode="url" autocomplete="off" spellcheck="false" placeholder="Paste a link to stash it" />
    <button class="primary" disabled={!text.trim() || busy}>Stash it</button>
  </label>
</form>
<p class="hint">Or press {mac ? '⌘V' : 'Ctrl+V'} anywhere on this page.</p>

<style>
  .stack {
    position: relative;
    margin-top: 12px;
    padding-top: 20px;
  }

  /* The two cards behind, as in the icon. */
  .stack::before,
  .stack::after {
    content: '';
    position: absolute;
    left: 50%;
    height: 40px;
    border-radius: var(--radius-card);
    translate: -50% 0;
  }

  .stack::before {
    top: 0;
    width: calc(100% - 56px);
    background: color-mix(in srgb, var(--accent) 22%, var(--paper));
  }

  .stack::after {
    top: 10px;
    width: calc(100% - 28px);
    background: color-mix(in srgb, var(--accent) 40%, var(--paper));
  }

  .bar {
    position: relative;
    z-index: 1;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 8px 8px 20px;
    border-radius: var(--radius-card);
    background: var(--paper);
    box-shadow: var(--lift), 0 0 0 1px var(--line);
    cursor: text;
  }

  .bar:focus-within {
    box-shadow: var(--lift), 0 0 0 2px var(--accent);
  }

  .mark {
    display: grid;
    color: var(--accent);
  }

  input {
    flex: 1;
    min-width: 0;
    height: 48px;
    border: 0;
    outline: 0;
    background: none;
    font-size: var(--text-l);
    font-weight: 500;
    letter-spacing: -0.01em;
  }

  input::placeholder {
    color: var(--ink-soft);
    opacity: 1;
  }

  .hint {
    margin: 12px 0 0 20px;
    color: var(--ink-soft);
    font-size: var(--text-s);
  }

  @media (max-width: 760px) {
    .hint {
      display: none;
    }

    input {
      font-size: var(--text-m);
    }
  }
</style>
