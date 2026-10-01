<script>
  import { signIn } from './stash.svelte.js'

  let create = $state(false)
  let email = $state('')
  let password = $state('')
  let busy = $state(false)
  let message = $state(null)

  async function submit(event) {
    event.preventDefault()
    busy = true
    message = null
    try {
      message = await signIn(email.trim(), password, create)
    } catch (error) {
      message = error.message
    } finally {
      busy = false
    }
  }
</script>

<main class="welcome">
  <section class="intro">
    <img src="{import.meta.env.BASE_URL}icon.png" alt="" width="88" height="88" />
    <h1>Share it now. Find it later.</h1>
    <p>
      That recipe on Instagram, the trail on YouTube, the café a friend sent you on Maps. Stash keeps them, sorted and
      searchable. Here, your stash is on a bigger screen.
    </p>
    <p class="apps">
      The <a href="https://github.com/g1lg1l/stash/tree/main/apps/ios">iPhone</a> and
      <a href="https://github.com/g1lg1l/stash/tree/main/apps/android">Android</a> apps work without an account. Sign in with
      the same one here to see what you saved there.
    </p>
  </section>

  <form class="auth" onsubmit={submit}>
    <div class="switch" role="tablist" aria-label="Account">
      <button type="button" role="tab" aria-selected={!create} onclick={() => ((create = false), (message = null))}>Sign in</button>
      <button type="button" role="tab" aria-selected={create} onclick={() => ((create = true), (message = null))}>Create account</button>
    </div>
    <label>
      <span>Email</span>
      <input bind:value={email} type="email" autocomplete="email" required />
    </label>
    <label>
      <span>Password</span>
      <input bind:value={password} type="password" autocomplete={create ? 'new-password' : 'current-password'} minlength="6" required />
      {#if create}<small>At least 6 characters.</small>{/if}
    </label>
    {#if message}<p class="message" role="alert">{message}</p>{/if}
    <button class="primary" disabled={busy || !email.trim() || password.length < 6}>
      {busy ? (create ? 'Creating account…' : 'Signing in…') : create ? 'Create account' : 'Sign in'}
    </button>
  </form>
</main>

<style>
  .welcome {
    display: grid;
    grid-template-columns: minmax(0, 1.15fr) minmax(320px, 420px);
    align-items: center;
    gap: clamp(40px, 8vw, 120px);
    max-width: 1180px;
    min-height: 100dvh;
    margin: 0 auto;
    padding: 48px clamp(20px, 5vw, 64px);
  }

  .intro img {
    border-radius: 22%;
    margin-bottom: 36px;
  }

  h1 {
    max-width: 11ch;
    font-size: clamp(3rem, 7.5vw, 5.75rem);
    letter-spacing: -0.032em;
    line-height: 0.94;
  }

  .intro p {
    max-width: 46ch;
    margin-top: 28px;
    font-size: var(--text-l);
    line-height: 1.45;
  }

  .intro .apps {
    margin-top: 16px;
    color: var(--ink-soft);
    font-size: var(--text-m);
  }

  .auth {
    display: grid;
    gap: 18px;
    padding: 28px;
    border-radius: 28px;
    background: var(--wash);
  }

  .switch {
    display: grid;
    grid-template-columns: 1fr 1fr;
    padding: 4px;
    border-radius: 999px;
    background: var(--wash-strong);
  }

  .switch button {
    min-height: 40px;
    border: 0;
    border-radius: 999px;
    background: none;
    color: var(--ink-soft);
    font-weight: 600;
  }

  .switch [aria-selected='true'] {
    background: var(--paper);
    color: var(--ink);
    box-shadow: 0 1px 3px rgb(0 0 0 / 0.12);
  }

  label {
    display: grid;
    gap: 6px;
    font-size: var(--text-s);
    font-weight: 600;
  }

  input {
    height: 50px;
    padding: 0 16px;
    border: 1px solid var(--line);
    border-radius: 14px;
    background: var(--paper);
    font-size: var(--text-m);
    font-weight: 450;
  }

  input:focus {
    outline: 2px solid var(--accent);
    outline-offset: -1px;
    border-color: transparent;
  }

  small {
    color: var(--ink-soft);
    font-size: var(--text-xs);
    font-weight: 450;
  }

  .message {
    color: var(--accent);
    font-size: var(--text-s);
    font-weight: 560;
  }

  .auth .primary {
    margin-top: 4px;
    min-height: 52px;
  }

  @media (max-width: 860px) {
    .welcome {
      grid-template-columns: 1fr;
      align-content: start;
      gap: 40px;
      padding-top: 40px;
    }

    .intro img {
      width: 64px;
      height: 64px;
      margin-bottom: 24px;
    }
  }
</style>
