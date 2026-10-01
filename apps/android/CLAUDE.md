# Stash for Android

Native Android "save for later" inbox. Kotlin, Jetpack Compose, Material 3 with dynamic color, Room, Android 12+ (minSdk 31, target 37). No account, no backend yet (planned in the GitHub issues). Repo-wide rules and the workflow are in the root `CLAUDE.md`; commands below run from `apps/android`.

## Build, test, install

There's no global Gradle or JDK on this Mac: use the wrapper with Android Studio's JDK.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew testDebugUnitTest            # JVM tests: detection, parsing, classification, search, feed
./gradlew connectedDebugAndroidTest    # Room tests, on a running emulator or phone
./gradlew installDebug                 # or installRelease: minified and much smoother, signed with the debug key
```

Emulator: the `Stash_Pixel` AVD (Android 16, Play Store image) runs with `~/Library/Android/sdk/emulator/emulator -avd Stash_Pixel`. `adb` is in `~/Library/Android/sdk/platform-tools`.

Bump `versionCode` in `app/build.gradle.kts` for each build installed on a phone, so *Settings → Version* tells builds apart. Debug and release are signed with the same key, so they install over each other and keep the saves.

## Layout

```
app/src/main/java/com/g1lg1l/stash/
  data/         Save (Room entity), Store (DAO, database, the Stash singleton), UrlSourceDetector,
                Classification, Metadata (oEmbed / Open Graph + the enricher), Feed (search, day sections,
                rediscovery, widget pick), SampleData
  ui/           Theme (tokens, Material You, icons, tints, Prefs), Components, one file per screen
  widget/       Home screen widget (Glance)
  MainActivity  Tabs, navigation (Navigation 3), widget deep links
  ShareActivity Share sheet and "Save to Stash" in the text selection menu
  StashApp      Opens the store, configures Coil
app/src/main/res/  Material Symbols drawables (ic_*), adaptive icon, widget info and preview
app/src/test/      JUnit on the JVM
app/src/androidTest/  Room on a device
app/schemas/       Room schema history (exported by KSP), for future migrations
docs/              README icon and screenshots
```

## How it works

- **Share hand-off.** `ShareActivity` runs in the app's own process, so it writes to Room directly (`SaveDao.add` dedupes by `canonicalUrl`); there's no inbox like the iOS extensions need. It then starts enrichment in `Stash.scope`, which outlives the sheet.
- **Enrichment.** `Enricher` fetches oEmbed (YouTube, TikTok, X, Spotify) or Open Graph, at most four at a time. It fills gaps only, never overwrites, and a failure never loses a save. Offline stays pending; "the page had nothing" is failed and retried once per launch and on pull to refresh. It runs when `MainActivity` starts, after a share, and on pull to refresh. Each result goes through `SaveDao.update`, which reads the row inside a transaction, so a background write never undoes an edit.
- **State.** One `Stash.saves` StateFlow (newest first) feeds every screen, like `@Query` on iOS; screens filter in memory. Preferences are `Prefs` (SharedPreferences mirrored into Compose state): `theme`, `feedLayout`, `showRediscovery`, the same keys as iOS. The theme goes through `UiModeManager.setApplicationNightMode`, so the system applies it to the splash screen too (the activity is recreated; the back stack survives).
- **Widget.** Glance, in the app process, collecting `Stash.saves` while it's showing. It shows `Rediscovery.widgetPick`. It refreshes when `MainActivity` stops, after enrichment, and every 4 hours. Tapping opens `stash://save/<id>` through an explicit intent (no intent filter needed); `MainActivity` is `singleTop` and pushes the detail screen.
- **Classification.** `Classification` is local keyword scoring, structural rules first (Maps → Places, Spotify → Music). A category picked by hand (`categoryIsManual`) always wins.

## Conventions

- Enums are stored as the same lowercase raw strings as iOS (`food`, `youtube`) through `Converters`; unknown values fall back. No unique index on `canonicalUrl`: the schema stays sync-friendly and `SaveDao.add` resolves duplicates.
- Debug builds: `adb shell am start -n com.g1lg1l.stash/.MainActivity --ez sampleData true` opens an in-memory store with the sample saves; it's never enriched and never touches the real one. `adb shell cmd uimode night yes` tries dark mode without touching the setting.
- Icons are Material Symbols Rounded vector drawables, fetched from `google/material-design-icons` (`symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml`, or `_fill1_` for filled) with the `android:tint` line removed. Drawables rather than Compose vectors, because the widget needs them too.
- Plain strings in Compose, as on iOS. `strings.xml` only holds what XML needs (labels, widget description).
- Tests: one focused check per behavior. Pure logic gets JVM tests; only Room needs a device.

## Gotchas

- `android.util.Log`, `android.net.Uri`, `org.json` and ICU are stubs in JVM tests. Keep the logic in `data/` on OkHttp's `HttpUrl`, `java.time` and `java.text`; `org.json` comes in as a test dependency.
- Wikimedia (and some image CDNs) answer 403 to OkHttp's default User-Agent. Coil and the metadata fetcher share `MetadataService.client`, which sends a browser identity.
- Spotify pages bounce every browser to an open-in-the-app link with no metadata: only its oEmbed works. The iOS app doesn't use it yet.
- The first launch of a debug build on the emulator takes a few seconds to draw; take screenshots after that. Debug builds also scroll less smoothly than release.
- The Undo snackbar lasts 4 seconds. Scripted taps that wait on `uiautomator dump` in between will miss it.
- The Pixel launcher on the emulator doesn't show Glance's generated widget previews, so `stash_widget_info.xml` uses `drawable-nodpi/widget_preview.png`, a crop of the real widget. Retake it when the widget's look changes.

## Screenshots

Sample data on the emulator, status bar in demo mode (`adb shell settings put global sysui_demo_allowed 1`, then `am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941`, `… -e command notifications -e visible false`, `… -e command network -e mobile hide`), `adb exec-out screencap -p`, resized to 600 px wide into `docs/screenshots/`. `uiautomator dump` gives the bounds to tap.
