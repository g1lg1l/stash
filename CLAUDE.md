# Stash

Native iPhone "save for later" inbox. Swift 6, SwiftUI, SwiftData, iOS 26+. No third-party dependencies, no account, no backend yet (planned in the GitHub issues). `README.md` is for people using the app; this file is for working on it.

## Build, test, install

On this Mac `xcode-select` points at the Command Line Tools, so prefix commands with `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`. Simulator names are ambiguous (two "iPhone 17 Pro" runtimes), so pass an id from `xcrun simctl list devices`.

```sh
xcodebuild test -project Stash.xcodeproj -scheme Stash -destination 'id=<simulator id>'

# On the connected iPhone (UDID from `xcrun devicectl list devices`)
xcodebuild build -project Stash.xcodeproj -scheme Stash -destination 'id=<UDID>' -allowProvisioningUpdates -derivedDataPath <dir>
xcrun devicectl device install app --device <UDID> <dir>/Build/Products/Debug-iphoneos/Stash.app
```

Bump `CURRENT_PROJECT_VERSION` (project level, shared by all targets) for each build installed on the phone, so *Settings → Version* tells builds apart.

## Layout

```
Shared/                  Compiled into the app and both extensions
  Models/                Save (SwiftData) and presentation (names, symbols, tints)
  Persistence/           ModelContainer in the App Group, SaveStore (dedupe, delete), ShareInbox (hand-off)
  Services/              URLSourceDetector, ClassificationService
Stash/                   App: features (Home, Explore, Search, SaveDetail, Settings), design system, metadata, rediscovery
ShareUI/                 Share sheet UI and link extraction, compiled into both extensions
StashShareExtension/     Share extension (app row): Info.plist and entitlements
StashActionExtension/    "Save to Stash" action (actions list): Info.plist, entitlements, template icon
StashTests/              Swift Testing
docs/                    README icon and screenshots
```

Each top-level folder is an Xcode synchronized folder tied to its targets: new files are picked up without editing the project.

## How it works

- **Share hand-off.** The extensions never open the database. They write one small JSON file per share into the App Group (`share-inbox/`). The app drains it on launch, on returning to the foreground, and on pull to refresh, deduping as it goes (`ShareInbox` in `SaveStore.swift`).
- **Enrichment.** `MetadataEnricher` fetches oEmbed (YouTube, TikTok, X) or Open Graph in the background. It fills gaps only, never overwrites, and a failure never loses a save.
- **Classification.** `ClassificationService` is local keyword scoring, with structural rules first (Maps → Places, Spotify → Music). A category picked by hand (`categoryIsManual`) always wins.

## Conventions

- Enums are stored as raw strings (`categoryRaw`, `sourceRaw`…) so they work in `#Predicate`.
- No `@Attribute(.unique)`: the schema stays CloudKit-compatible, and duplicates are resolved by `canonicalURL` in `SaveStore.add`.
- Preferences are `@AppStorage` keys: `theme`, `feedLayout`, `showRediscovery`. Launch arguments override them (`-theme dark`), which helps for screenshots.
- `-sampleData` (DEBUG) uses an in-memory store with sample saves and never drains the inbox.
- Comments explain why, sparingly. `ponytail:` comments mark deliberate shortcuts and when to upgrade them.
- Tests use Swift Testing, with one focused check per behavior.

## Gotchas

- Only Xcode's editor registers the App Group `group.com.g1lg1l.stash`, not `xcodebuild`. Without it the app falls back to a local store and shares can't reach it.
- `URLCache` removes disk entries in the background, so tests must wait for a removal to land.
- Free provisioning expires after 7 days.
- The test iPhone is an iPhone 14: no Apple Intelligence.

## Workflow

- Work is tracked as GitHub issues on `g1lg1l/stash`. Reference them in commit messages: `feat: add Settings (#14)`.
- Once the build and tests pass, commit and push straight to `main` and close the issue with a short comment. No PRs. Never force push unless asked.
- Commit as `g1lg1l <gilbert.ndresaj@gmail.com>`. It's set in this repo's local git config; the global identity on this Mac belongs to someone else.
- README screenshots: run the simulator with `-sampleData` and `xcrun simctl status_bar <id> override --time 9:41`, then resize to 600 px wide into `docs/screenshots/`. Icon: `ictool Stash/AppIcon.icon --export-image --output-file docs/icon.png --platform iOS --rendition Default --width 256 --height 256 --scale 1` (`ictool` ships inside Icon Composer in Xcode).
