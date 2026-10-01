<script>
  /** Where the confirmation email's link lands: confirmed (and signed in here), or why the link failed. */
  import { stash } from './stash.svelte.js'

  let { link, ondone } = $props()
</script>

<main class="landing">
  <img src="{import.meta.env.BASE_URL}icon.png" alt="" width="88" height="88" />
  {#if link.confirmed}
    <h1>You're all set.</h1>
    <p>
      {link.email ? `${link.email} is confirmed` : 'Your email is confirmed'}, and you're signed in here. In the Stash
      app, sign in with the same email and password to keep your saves in sync.
    </p>
    <button class="primary" onclick={ondone}>Open your stash</button>
  {:else}
    <h1>This link didn't work.</h1>
    <p>{link.reason} Sign in if you already confirmed your email. If you didn't, create the account again to get a new link.</p>
    <button class="primary" onclick={ondone}>{stash.session ? 'Open your stash' : 'Sign in'}</button>
  {/if}
</main>

<style>
  .landing {
    display: grid;
    align-content: center;
    justify-items: start;
    gap: 28px;
    max-width: 720px;
    min-height: 100dvh;
    margin: 0 auto;
    padding: 48px clamp(20px, 5vw, 64px);
  }

  img {
    border-radius: 22%;
  }

  h1 {
    font-size: clamp(3rem, 7.5vw, 5.75rem);
    letter-spacing: -0.032em;
    line-height: 0.94;
  }

  p {
    max-width: 44ch;
    font-size: var(--text-l);
    line-height: 1.45;
  }

  .primary {
    min-height: 52px;
    padding: 0 28px;
  }
</style>
