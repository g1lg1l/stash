# Stash requirements

Stash ships on both platforms as the same app with the same features. The iPhone app is in `apps/ios` (Swift 6, SwiftUI, SwiftData, iOS 26+), the Android app in `apps/android`. They share no code, so this document is the contract between them: when a behavior changes on one side, change it here and on the other side.

## Platform

| | |
|---|---|
| Minimum | Android 12 (API 31). That's where Material You starts: dynamic color, themed icons, the new widget APIs. |
| Target | The latest SDK (API 37) |
| Language and UI | Kotlin, Jetpack Compose, Material 3 |
| Account, backend, sync | None, same as iOS. The schema stays sync-friendly for the backend planned in the GitHub issues. |
| Offline | Saving never needs the network. Enrichment catches up later. |

### Material You

- **Dynamic color.** Every screen, the share sheet and the widget take their colors from the wallpaper (`dynamicLightColorScheme` / `dynamicDarkColorScheme`).
- **Themed icon.** The adaptive launcher icon has a monochrome layer, so it follows the user's themed icons.
- **Edge to edge.** Content draws behind the system bars. Media on the detail screen runs under the status bar.
- **Predictive back.** Back gestures preview the screen underneath.
- **Material 3 components.** Navigation bar, large collapsing top app bars, filter chips, segmented buttons, bottom sheets, swipe to dismiss, pull to refresh, snackbars with undo.
- **Wide screens.** Cards flow into more columns on tablets and foldables; category tiles fill the width.
- **Motion and haptics.** Cards press in, images cross-fade, and toggles, saves and category changes give haptic feedback.

## Features (must match iOS)

### 1. Saving from other apps
- Stash appears in the **share sheet** as "Stash". It accepts a URL or text containing one (`ACTION_SEND`, `text/plain`).
- **Save to Stash** appears in the **text selection menu** of any app (`ACTION_PROCESS_TEXT`). It fills the role of the iOS "Save to Stash" action.
- Share → saved → gone. The sheet shows a spinner, then "Saved to Stash / From YouTube. Stash will organize it for you." and closes itself after 1.2 s. There's a Done button. No form, no choices.
- Errors: "No link to save / Stash saves links. Try sharing the page or post itself." and "Couldn't save".
- The link is taken from the shared text (the first http(s) link, ignoring emails and phone numbers), or from the whole text when it's a bare link like `youtu.be/abc`.

### 2. Link detection (offline, from the URL alone)
Port `URLSourceDetector` rule for rule:
- Accept any http(s) link, with or without a scheme. Reject emails, `mailto:`, `tel:`, app schemes, hosts without a dot, and anything with spaces.
- **Source:** Instagram, YouTube, TikTok, Reddit, X, Spotify, Maps (Google and Apple, `goo.gl/maps`, `maps.app.goo.gl`), Web.
- **Content type:** video, article, post, product, place, music, other. Same per-source rules: Instagram reels are videos; `music.youtube` is music; on the web, `/product/`, `/dp/` and similar paths are products, a bare domain is "other", and any other path is an article.
- **Canonical key** for dedupe: https, no `www.`/`m.`/`mobile.`, one key per YouTube video however it's linked (`watch`, `youtu.be`, `shorts`, `embed`, `live`), X posts under any handle, Reddit host aliases, and share tokens dropped. On the web, `utm_*` and click IDs are dropped, query items are sorted, trailing slashes and plain anchors are ignored, and hash routes are kept.
- The shared URL is stored as the source of truth. The key only dedupes.

### 3. Storage and dedupe
- One `Save` per resource. Saving it again bumps `lastSavedAt` and keeps the earliest `createdAt`, in any order.
- Fields: id, url, canonicalUrl, source, contentType, category, status (pending, enriched or failed), categoryIsManual, title, descriptionText, thumbnailUrl, author, tags, summary, createdAt, lastSavedAt, openedAt.
- Enums are stored as the same lowercase raw strings as iOS (`food`, `youtube`…). Unknown values fall back (`unknown`, `other`, `pending`).
- No unique constraint: duplicates are resolved in code (sync-friendly, same as iOS).
- Delete removes the save and its cached thumbnail.

### 4. Enrichment (background, fills gaps only)
- oEmbed for YouTube, TikTok, X and Spotify. For X, the post text becomes the title. Spotify pages send browsers (Safari included) to an open-in-the-app redirect with no metadata, so oEmbed is the only way to get a title and artwork. **The iOS app doesn't do this yet: port it back.** Then the page's Open Graph tags, Twitter cards, then `<title>` and `<meta name=description>`, from the first 1 MB of the page.
- Strip a trailing " | Site" or " - Site" from titles. A title that is only the site name is no title.
- Map links with no useful title take the place name from the URL (`?q=`, `/place/<name>`).
- `og:type` refines the content type of web saves.
- **Never overwrite** a field that's already set. **A failure never loses a save.** Offline or timed out: stays pending and is retried. The page answered with nothing: failed, retried once per launch and on pull to refresh.
- At most 4 fetches at a time. Runs when the app comes to the foreground, after a share and on pull to refresh.

### 5. Classification (local, explainable)
- Structure first: Maps or a place → Places, Spotify or music → Music, a product → Shopping.
- Otherwise keyword scoring, ignoring case and accents. Title words count double; description, tags, host and path count once. Same vocabulary (English plus common Italian) and the same tie order as iOS.
- A category picked by hand (`categoryIsManual`) always wins. Enrichment only classifies saves still in "Other".

### 6. Home
- The title greets by time of day ("Good morning", "Good afternoon", "Good evening"). The subtitle reads "12 saved · 3 unseen".
- Newest first (by `lastSavedAt`), grouped into Today / Yesterday / Last 7 Days / Earlier.
- **Cards** or **Compact** layout, remembered. Cards follow the content: media cards (video, product, place, anything with an image) with a source badge and a play button on videos; an editorial serif card on a soft category tint for text-only articles and posts; a horizontal card with artwork for music.
- Compact rows have an unseen dot, a thumbnail, the title, and "Category · byline".
- Filters: **Unseen** and **content type** (only the types the user has), as filter chips under the title. "Nothing matches / Show all" when the filters hide everything.
- Pull to refresh. Empty state: "Your stash is empty. When you find something worth keeping, share it to Stash."
- Settings is in the top bar.

### 7. Save actions (everywhere a save is listed)
- Touch and hold: Open original (marks it seen), Share, Mark as seen/unseen, Category, Delete.
- Compact rows swipe: start → toggle seen, end → delete. A delete from a list offers Undo (the cached thumbnail goes once Undo has passed).
- A quick double tap opens a screen once.

### 8. Explore
- **Worth another look**: up to 6 unseen saves at least 3 days old, oldest first. A horizontal snapping row, hidden when off in Settings or empty.
- **Categories**: only the categories the user has, biggest first, "Other" last. Tiles use the newest save's image as a cover, with the name and "N saves". Tap a tile to open the category feed.
- Empty state: "Nothing to explore yet".

### 9. Search
- Instant, local. Every word must match some field (title, description, summary, author, category, source, URL, tags), ignoring case and accents. Results keep newest-first order.
- An empty query shows "Recently saved" (8). No results shows "No results for “…”".

### 10. Save detail
- Media runs edge to edge under the status bar with a scrim, at the content type's aspect ratio (video 16:9, article 16:10, product and music 1:1, else 4:3). Text-only saves start below the top bar.
- Source, and a category picker that sticks. Title (serif for articles), author, a big **Open original** button (links open in their own app when one is installed, otherwise in the browser), summary, description, #tags.
- Footer: "Getting details…" or "Details couldn't be loaded. The link is saved.", then "Saved 2 days ago" and the URL (selectable).
- Opening marks the save as seen. Share and Delete (confirmed: "Delete this save? It will be removed from your stash for good.") are in the top bar.

### 11. Settings
- Appearance: Theme System / Light / Dark, applied to the whole app and its splash screen.
- Explore: Worth another look on/off.
- Your data: Export links (every title and link as plain text, through the share sheet). "No account. Your N saves stay on this phone."
- About: Get the most out of Stash (setup tips), Version (`versionName (versionCode)`), Source code on GitHub.

### 12. Home Screen widget
- One save: the Worth another look pick, else the newest unseen, else the newest. Headline: "Worth another look", "Waiting for you" or "Latest save".
- Starts at 4×2 and shrinks to 2×2. Small: the image full bleed with a scrim, headline and title. Wider: the image beside the headline, title and "byline · 2 days ago". Without an image: the category tint and symbol. Its colors follow Material You.
- The widget picker shows a preview image of the wide layout.
- Tapping it opens that save in Stash. Empty: "Your stash is empty / Share a link to Stash."
- Refreshes when the app goes to the background, after enrichment, and every 4 hours (saves age into Worth another look).

### 13. Developer affordances
- `--ez sampleData true` (debug builds) opens an in-memory store with the same 15 sample saves as iOS and never touches the real one.
- `versionCode` goes up for each build installed on a phone.

## Account and sync (Supabase)
The device's own database stays the source of truth. The app works fully offline and without an account; signing in only turns on sync, and it can happen at any time.

### Signing in
- First launch shows a welcome screen once: what Stash does, **Sign in**, **Create account** and **Not now**. Not now goes straight to Home. It is never shown again (`seenWelcome`), whatever the choice.
- Settings → Account, signed out: "Sync your saves" with Sign in / Create account. Signed in: the email, "Last synced" (relative time, or "Syncing…", or the last error), **Sync now**, **Sign out**, **Delete account** (confirmation dialog: deletes the account and the copy on the server, keeps the saves on this device).
- One auth screen with email and password, switching between Sign in and Create account. The password needs 6+ characters. The server's message is shown on failure (`msg`, else `error_description`, else `message` as the REST API sends it); no connection shows "Couldn't connect. Check your connection and try again." If Create account returns no session (email confirmation is on), say "Check your email to confirm, then sign in."
- Sign out keeps every save on the device, forgets the session and resets both sync cursors, so the next sign-in, to any account, uploads everything again.
- Email and password only for now. Sign in with Apple / Google need the paid Apple program and a Google Cloud client (#17).

### Server
`supabase/migrations` is the schema. Base URL `https://kzgmhvqbvrylwydqskcm.supabase.co`, publishable key `sb_publishable_dCk3njz-F4vws7Y5ky6XNA__pOe081Z` (public by design: row level security protects the data). Every request sends `apikey: <key>`; signed-in requests also send `Authorization: Bearer <access_token>`. Plain HTTPS from the platform's own client (URLSession, OkHttp), no Supabase SDK.

- Sign up: `POST /auth/v1/signup` `{"email","password"}`. Sign in: `POST /auth/v1/token?grant_type=password`, same body. Both answer `access_token`, `refresh_token`, `expires_at` (Unix seconds) and `user.id`/`user.email`. Errors are 4xx with `msg`.
- Refresh: `POST /auth/v1/token?grant_type=refresh_token` `{"refresh_token"}`, before `expires_at` or after a 401, once. If refresh fails with 4xx the session is gone: sign out locally and show "Signed out. Sign in again to keep syncing."
- Sign out: `POST /auth/v1/logout`, best effort.
- Delete account: `POST /rest/v1/rpc/delete_account` (empty JSON body `{}`), then sign out locally.
- Store the session in the Keychain (iOS) / app-private preferences (Android).

The `saves` table has the `Save` fields in snake_case (`canonical_url`, `content_type`, `category_is_manual`, `description_text`, `thumbnail_url`, `created_at`, `last_saved_at`, `opened_at`), plus `user_id` (set by the server, never sent), `updated_at` (set by the server on every write, never sent) and `deleted_at`. Ids are UUIDs, lowercase. Dates are ISO 8601 in UTC with milliseconds when sent; the server answers microseconds and `+00:00`.

### Sync
Local additions: `modifiedAt` on every save (set to now on every local change: add, re-save, edit, seen, category, enrichment result), and a `Tombstone` record (`id`, `deletedAt`) written when a save is deleted while signed in (signed out, delete stays a plain delete). Stored next to the session: `lastPushedAt` (local time) and `pullCursor` (the server's last `updated_at`, kept as the exact string it sent).

`sync()` runs on launch / return to the foreground (with enrichment), after a share is saved, on pull to refresh, on Sync now, right after signing in, and when the app goes to the background. One at a time; a call while one runs is dropped. Offline or failing, it just stops and tries again next time: it never changes or loses local data on failure. The last error is kept (it survives a relaunch) and shown in Settings until a sync succeeds.

1. **Push.** `pushStart = now` (taken first). Upsert every save with `modifiedAt > lastPushedAt`: `POST /rest/v1/saves` with `Prefer: resolution=merge-duplicates,return=minimal` and a JSON array (500 rows per request). Each row sends `"deleted_at": null`, so a save brought back with Undo after its delete reached the server comes back there too (Undo removes the tombstone and bumps `modifiedAt`). Then tombstones: `PATCH /rest/v1/saves?id=in.(<ids>)` with `{"deleted_at": "<deletedAt>"}` (ids of rows the server never had are simply ignored). When all succeed: `lastPushedAt = pushStart`, delete the pushed tombstones.
2. **Pull.** `GET /rest/v1/saves?select=*&updated_at=gt.<pullCursor>&order=updated_at.asc,id.asc&limit=500` (no `updated_at` filter on the first pull), repeated while a page is full. **URL-encode the cursor**: its `+00:00` must go out as `%2B00:00`, or the server rejects it. Apply each page in one transaction, then save the last row's `updated_at` as `pullCursor`. For each row:
   - A local save with the same id and `modifiedAt > pushStart` (changed during this sync): skip it, the next push wins.
   - `deleted_at` set: delete the local save with that id, if any. No tombstone.
   - A local save with the same id: replace its fields with the server's. `modifiedAt` stays as it was, so it isn't pushed back.
   - A local save with a different id but the same `canonicalUrl` (the same link saved on two devices): keep the id that sorts first as a lowercase string, so every device picks the same survivor. Merge as `add` does (earliest `createdAt`, latest `lastSavedAt`, `openedAt` if either has one, a manual category wins and otherwise the local one beats the server's `other`, `enriched` if either is, the server's metadata and tags with local values filling its gaps) into the survivor, tombstone the other id, and set the survivor's `modifiedAt` to now so it goes back up. (Keeping "the server's id" instead loses the save: two devices pulling each other's copy would each tombstone their own.)
   - Otherwise insert it with `modifiedAt = 0`.
3. Unknown enum strings fall back as everywhere else. Widget and screens refresh as after any other write.

Last write to reach the server wins. Each platform keeps the pull rules (step 2) in a pure function with JVM / Swift Testing checks: skip changed-during-sync, delete, update without re-push, merge by `canonicalUrl`, insert.

## Platform mapping

| iOS | Android |
|---|---|
| SwiftUI | Jetpack Compose + Material 3 |
| SwiftData | Room (SQLite), observed as a `Flow` |
| Share extension + action extension, App Group, `share-inbox/` JSON hand-off | A share activity inside the app's own process. It writes to the database directly, so no inbox is needed. |
| `@AppStorage` | `SharedPreferences`, mirrored into Compose state |
| Theme via `preferredColorScheme` | `UiModeManager.setApplicationNightMode` (API 31). It covers the splash screen too. |
| WidgetKit | Jetpack Glance |
| `AsyncImage` + `URLCache` | Coil (memory and disk cache) |
| `URLSession` | OkHttp (already brought in by Coil), one client for pages and images. It sends a browser identity: Wikimedia and some image CDNs refuse OkHttp's default one. |
| `NSDataDetector` | A small http(s) link regex |
| SF Symbols | Material Symbols Rounded |
| Liquid Glass | Translucent Material surfaces over media, tonal containers elsewhere |
| Context menu | Bottom sheet on touch and hold |
| Swipe actions | `SwipeToDismissBox` |
| Zoom transition | The card's image grows into the detail screen (shared element), with predictive back |
| `sensoryFeedback` | `HapticFeedback` |
| Launch arguments | Intent extras |
| Swift Testing | JUnit (JVM) for the logic, instrumented tests for the database |

## Tests to port
One focused check per behavior, as on iOS: source and type detection, scheme-less links, rejected inputs, the shared URL kept as is, canonical keys (same resource merges, different resources never do), Open Graph / Twitter / title parsing, `og:type` mapping, entity decoding, oEmbed (including X), map place names, title cleaning, classification (structure, titles, weighting, URL words), fill-gaps enrichment and failure statuses, search (case, accents, every word, fields, order), day sections, rediscovery (rule and cap), the widget pick, link extraction from shared text, dedupe dates, delete, manual category through enrichment, unknown raw values, and a performance guard at 2,000 saves.

## Out of scope (same as iOS today)
AI summaries (the `summary` field exists, but nothing fills it yet), editing tags.
