# Stash

A universal "save for later" inbox for iPhone. Find something, share it to Stash, done. Stash organizes it afterwards.

Native iPhone app: Swift 6, SwiftUI, SwiftData. iOS 26+. No third-party dependencies, no account, no backend. Everything is stored locally, and saving works offline.

## What it does

- **Share sheet**: Stash is both a share extension (the app row) and a **Save to Stash** action (the list below it), so it works from Safari, YouTube, Instagram, TikTok, Reddit, X, Spotify, Maps, or any app that shares a link or text containing one. It saves instantly and dismisses itself.
- **URL intelligence**: detects the source and content type, and dedupes the same resource however it was linked (`youtu.be`, `/shorts`, `watch?v=`, tracking parameters).
- **Metadata**: fetched in the background from official keyless oEmbed endpoints (YouTube, TikTok, X) or from Open Graph tags. A failure never loses a save.
- **Classification**: local and deterministic categories (Food, Travel, Tech…), designed to be swapped for an on-device model later.
- **Home**: newest first, grouped by day, as cards or a compact list. Filters for unseen saves and content type. Swipe actions and context menus. Change a save's category (it sticks) or delete it entirely, cached image included.
- **Explore**: "Worth another look" for older unseen saves, plus categories covered with your own thumbnails.
- **Search**: local and instant. Every word must match; case and accents are ignored.
- **Settings** (gear on Home): theme (Automatic, Light, Dark), turn "Worth another look" on or off, export every link as plain text.

## Architecture

```
Shared/                  Compiled into the app and the extension
  Models/                Save (SwiftData) and presentation (names, symbols, tints)
  Persistence/           ModelContainer in the App Group, SaveStore (dedupe), ShareInbox (hand-off)
  Services/              URLSourceDetector, ClassificationService
Stash/                   App: features (Home, Explore, Search, SaveDetail, Settings), design system, metadata, rediscovery
ShareUI/                 Share sheet UI and link extraction, compiled into both extensions
StashShareExtension/     Share extension (app row): Info.plist and entitlements
StashActionExtension/    "Save to Stash" action (actions list): Info.plist, entitlements, template icon
StashTests/              Swift Testing
```

The Share Extension never opens the database. It writes one small JSON file per share into the App Group (`share-inbox/`). The app imports those files on launch, on returning to the foreground, and on pull to refresh, deduping as it goes. A share is safe the moment it's written, and it shows up even while the app is running.

## Development

- Xcode 27+. Open `Stash.xcodeproj` and run the `Stash` scheme.
- The scheme launches with `-sampleData`, an in-memory store with sample saves; the share inbox is left untouched in that mode. Untick it under *Edit Scheme → Run → Arguments* to use the real store.
- Tests: `xcodebuild test -project Stash.xcodeproj -scheme Stash -destination 'platform=iOS Simulator,name=iPhone 17 Pro'`

### First run on a real iPhone

1. Sign in to Xcode (*Settings → Accounts*) and set your team under *Signing & Capabilities* for both targets.
2. **Build once from Xcode itself (⌘B).** Only Xcode's editor registers the App Group `group.com.g1lg1l.stash`; `xcodebuild` doesn't. Without it the app still runs (with a local store), but shares can't reach it.
3. With a free Apple ID, trust the developer on the phone: *Settings → General → VPN & Device Management*. Free provisioning expires after 7 days.
4. iOS hides new share extensions: in any share sheet, scroll the app row to **More → Edit** and add Stash. **Save to Stash** also appears in the actions list below (if not, use **Edit Actions…** at the bottom).
