# Stash for iPhone

Native iPhone "save for later" inbox. Swift 6, SwiftUI, SwiftData, iOS 26+. No third-party dependencies. An optional account syncs through Supabase (`supabase/`), over plain URLSession. Repo-wide rules and the workflow are in the root `CLAUDE.md`; commands below run from `apps/ios`.

## Build, test, install

On this Mac `xcode-select` points at the Command Line Tools, so prefix commands with `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`. Simulator names are ambiguous (two "iPhone 17 Pro" runtimes), so pass an id from `xcrun simctl list devices`.

```sh
xcodebuild test -project Stash.xcodeproj -scheme Stash -destination 'id=<simulator id>'

# On the connected iPhone (UDID from `xcrun devicectl list devices`)
xcodebuild build -project Stash.xcodeproj -scheme Stash -destination 'id=<UDID>' -allowProvisioningUpdates -derivedDataPath <dir>
xcrun devicectl device install app --device <UDID> <dir>/Build/Products/Debug-iphoneos/Stash.app
```

Bump `CURRENT_PROJECT_VERSION` (project level, shared by all targets) for each build installed on the phone, so *Settings → Version* tells builds apart.

## Release

Each GitHub release carries two .ipa files next to the APK, ad-hoc signed with the entitlements so AltStore and Sideloadly know which App Group to register when they re-sign (`appGroupID` then reads the renamed group from the profile). A free Apple ID allows 3 apps *and* extensions at once, so `stash-free.ipa` keeps only the share extension.

```sh
OUT=<dir>; APP=$OUT/Stash.xcarchive/Products/Applications/Stash.app
xcodebuild archive -project Stash.xcodeproj -scheme Stash -destination 'generic/platform=iOS' -archivePath $OUT/Stash.xcarchive CODE_SIGNING_ALLOWED=NO
for x in StashActionExtension StashShareExtension StashWidget; do codesign -f -s - --entitlements $x/$x.entitlements $APP/PlugIns/$x.appex; done
codesign -f -s - --entitlements Stash/Stash.entitlements $APP
mkdir -p $OUT/Payload && cp -R $APP $OUT/Payload/ && (cd $OUT && zip -qry stash.ipa Payload)
rm -r $OUT/Payload/Stash.app/PlugIns/{StashActionExtension,StashWidget}.appex
codesign -f -s - --entitlements Stash/Stash.entitlements $OUT/Payload/Stash.app && (cd $OUT && zip -qry stash-free.ipa Payload)
gh release upload <tag> $OUT/stash.ipa $OUT/stash-free.ipa
```

## Layout

```
Shared/                  Compiled into the app and both extensions
  Models/                Save (SwiftData) and presentation (names, symbols, tints)
  Persistence/           ModelContainer in the App Group, SaveStore (dedupe, delete), ShareInbox (hand-off)
  Services/              URLSourceDetector, ClassificationService, Rediscovery
Stash/                   App: features (Home, Explore, Search, SaveDetail, Settings, Account), design system, metadata, sync
ShareUI/                 Share sheet UI and link extraction, compiled into both extensions
StashShareExtension/     Share extension (app row): Info.plist and entitlements
StashActionExtension/    "Save to Stash" action (actions list): Info.plist, entitlements, template icon
StashWidget/             Home Screen widget (WidgetKit): one save worth another look
StashTests/              Swift Testing
docs/                    README icon and screenshots
```

Each top-level folder is an Xcode synchronized folder tied to its targets: new files are picked up without editing the project.

## How it works

- **Share hand-off.** The share extensions never open the database. They write one small JSON file per share into the App Group (`share-inbox/`). The app drains it on launch, on returning to the foreground, and on pull to refresh, deduping as it goes (`ShareInbox` in `SaveStore.swift`).
- **Enrichment.** `MetadataEnricher` fetches oEmbed (YouTube, TikTok, X) or Open Graph in the background. It fills gaps only, never overwrites, and a failure never loses a save.
- **Widget.** `StashWidget` reads the store (the app is the only writer) and shows `Rediscovery`'s pick, else the newest unseen save. The app reloads its timeline when it goes to the background. Tapping opens `stash://save/<id>`, which `RootView` presents as a sheet; widget URLs reach the app without a registered URL scheme.
- **Sync.** `Account` in `Sync.swift`: email and password against Supabase's REST API, session in the Keychain, cursors (`lastPushedAt`, `pullCursor`) and the last error in UserDefaults. `sync()` pushes saves with `modifiedAt > lastPushedAt` and the `Tombstone`s, then pulls by `updated_at`; it runs with `refreshFromInbox` (launch, foreground, pull to refresh, so after every share), on going to the background, on Sync Now and after signing in. Every local change calls `Save.touch()`; applying pulled rows doesn't. The pull rules are `Sync.action` (pure) and `Sync.apply`, as in `REQUIREMENTS.md`.
- **Classification.** `ClassificationService` is local keyword scoring, with structural rules first (Maps → Places, Spotify → Music). A category picked by hand (`categoryIsManual`) always wins.

## Conventions

- Enums are stored as raw strings (`categoryRaw`, `sourceRaw`…) so they work in `#Predicate`.
- No `@Attribute(.unique)`: the schema stays CloudKit-compatible, and duplicates are resolved by `canonicalURL` in `SaveStore.add`.
- Preferences are `@AppStorage` keys: `theme`, `feedLayout`, `showRediscovery`. Launch arguments override them (`-theme dark`), which helps for screenshots.
- `-sampleData` (DEBUG) uses an in-memory store with sample saves: it never drains the inbox, never syncs and skips the welcome screen.
- Tests use Swift Testing, with one focused check per behavior.

## Gotchas

- Only Xcode's editor registers the App Group `group.com.g1lg1l.stash`, not `xcodebuild`. Without it the app falls back to a local store and shares can't reach it.
- `URLCache` removes disk entries in the background, so tests must wait for a removal to land.
- Free provisioning expires after 7 days.
- Extensions don't pick up the app's accent color: use `Color(.accent)` explicitly (asset symbols are generated).
- `xcodebuild test` can skip reinstalling the app when only an extension changed, so the simulator keeps running the old widget or share extension. `xcrun simctl install` the built `Stash.app` before checking extension changes.
- The test iPhone is an iPhone 14: no Apple Intelligence.
- New stored properties need a default (`modifiedAt = 1970`) and new models go in `ModelContainer.stash()`, which the app and the widget share, or SwiftData's automatic migration fails on existing stores.
- Upserts send `columns=…`: PostgREST then writes a missing key as null. Without it, a cleared field (unseen again, `deleted_at`) never reaches the server.
- Keychain items survive deleting the app, UserDefaults don't: a reinstall comes back signed in with empty cursors, so it pulls everything.
- To try sync against the local stack (`supabase start`), point `Account.baseURL` and the key at `http://127.0.0.1:54321`; the simulator needs no ATS exception for it.

## Screenshots

Run the simulator with `-sampleData` and `xcrun simctl status_bar <id> override --time 9:41` (the simulator must be booted), then resize to 600 px wide into `docs/screenshots/`. Taps can't be scripted from the shell, so the share sheet and widget shots came from a temporary UI test target (not committed) driving Safari (`XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")`) and SpringBoard; it can write PNGs straight to a Mac path. Icon: `ictool Stash/AppIcon.icon --export-image --output-file docs/icon.png --platform iOS --rendition Default --width 256 --height 256 --scale 1` (`ictool` ships inside Icon Composer in Xcode).
