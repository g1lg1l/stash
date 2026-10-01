# Stash on the web

Svelte 5 (runes) + Vite, plain JavaScript, no other runtime dependency. Deployed to GitHub Pages (`g1lg1l.github.io/stash/`, hence `base: '/stash/'`) by `.github/workflows/web.yml` on every push to `main` that touches `apps/web`. Repo-wide rules and the workflow are in the root `CLAUDE.md`; commands below run from `apps/web`.

```sh
npm install
npm run dev      # http://localhost:5173/stash/
npm test         # node:test, no framework: links, feed
npm run build    # dist/, what Pages serves
```

## Layout

```
src/
  App.svelte          Shell: sidebar / bottom tabs, routes (#/, #/explore, #/search, #/category/<key>), detail panel, ⌘V and ⌘K
  app.css             Tokens (color, type scale, radii), light and dark, shared .primary and .quiet buttons
  lib/
    stash.svelte.js   State and Supabase: auth, refresh, load, add, seen, category, delete with Undo
    links.js          UrlSourceDetector port: source, type and the canonical_url dedupe key
    feed.js           Categories, search, day sections, Worth another look, relative times
    icons.js          Material Symbols paths, copied from the Android drawables
    *.svelte          One component per piece: PasteBar, SaveList, Detail, Explore, Welcome, Settings, Thumb, Icon
public/icon.png       The iOS icon export
```

## How it works

- **No local database.** Per `REQUIREMENTS.md`, the web reads and writes Supabase directly with `fetch`, no SDK. The session lives in `localStorage`. Everything loads on sign-in and again when the tab comes back (at most every 20 s): saves from the phones show up then.
- **Writes are optimistic.** The list changes at once; a failed request puts it back and says why. Delete sets `deleted_at` (the phones pick up the tombstone), Undo clears it.
- **Adding.** `links.js` must give the same `canonical_url` as the apps, byte for byte, or dedupe breaks across devices. `links.test.js` pins strings produced by the Android `UrlSourceDetector`; regenerate them from Android when its rules change. New saves go up as `pending` with category `other`: the browser can't fetch other sites' metadata (CORS), so a phone enriches and classifies them on its next sync (#19 moves this to the server).

## Gotchas

- Saves in `$state` are proxies, never `===` the object they came from: compare by `id`.
- Props are live getters. Once a parent clears what it passes (`selected`), the prop reads as null, even inside a handler that's still running: grab the value before closing anything.
- `{#key x.id}` inside `{#if x}` crashes when `x` becomes null; key on something that's never null.
- Against the local stack (`supabase start`), put `VITE_SUPABASE_URL=http://127.0.0.1:54321` and the local `VITE_SUPABASE_KEY` in `.env.local` (git-ignored).

## Screenshots

Sample saves in the local stack (sign up a demo user, insert the apps' `SampleData` rows through REST with `last_saved_at` counted back from 9:41 today), `npm run dev`, then headless Chrome with `Date` pinned to 9:41 today so the greeting and the relative times match the apps' shots. `docs/screenshots/`: `home`, `explore`, `home-dark`, `welcome` at 1440×900 resized to 1200 wide; `wide.png` is 1440×800 at 2x, Home with the cacio e pepe open, for the README image.
