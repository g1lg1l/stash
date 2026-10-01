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

## Sync (planned, Supabase)
Not built yet; this is the design both apps follow when they add it.
- The device's own database stays the source of truth. The app works fully offline and without an account; signing in only turns on sync.
- `supabase/migrations` defines the `saves` table: the same fields in snake_case, plus `user_id`, `updated_at` (set by the server on every write) and `deleted_at`. Row level security limits each user to their own rows.
- Push: rows changed locally since the last sync are upserted by `id`. Pull: rows with `updated_at` after the last one seen. The last write to reach the server wins.
- Delete becomes a tombstone (`deleted_at`), kept locally until it has been pushed, so other devices remove the save too.
- Dedupe still runs on each device: two devices saving the same link offline end up with two rows, and the next `add` or pull merges them by `canonicalUrl`.
- The web client has no local database: it reads and writes Supabase directly.
- Only the publishable key ships in the apps. The service role key never goes in the repo.

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
