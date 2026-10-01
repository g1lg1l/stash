<p align="center">
  <img src="docs/icon.png" width="128" alt="Stash app icon">
</p>

<h1 align="center">Stash</h1>

<p align="center">Save anything for later. Stash sorts it out.</p>

<p align="center">
  <img src="docs/screenshots/home.png" width="200" alt="Home, newest saves first">
  <img src="docs/screenshots/explore.png" width="200" alt="Explore: Worth another look and categories">
  <img src="docs/screenshots/detail.png" width="200" alt="A saved recipe">
  <img src="docs/screenshots/settings.png" width="200" alt="Settings in dark mode">
</p>

Found a recipe on Instagram, a trail on YouTube, a café on Maps? Share it to Stash and get on with your day. Stash fetches the title and picture, files it into a category, and brings it back when you've forgotten about it.

No account, works offline, and everything stays on your iPhone.

## Features

- **Save from anywhere.** Safari, YouTube, Instagram, TikTok, Reddit, X, Spotify, Maps, or any app that shares a link. Stash saves instantly and gets out of the way.
- **Filled in for you.** Titles, pictures and authors appear on their own. The same link shared twice is saved once.
- **Sorted automatically.** Food, Travel, Fitness, Tech and more. Pick a different category and it sticks.
- **Home.** Newest first, grouped by day, as cards or a compact list. Show only what you haven't opened, or one type: videos, articles, places…
- **Explore.** "Worth another look" brings back saves you forgot about, next to your categories.
- **Search.** Instant, and it ignores case and accents.
- **Settings.** Automatic, light or dark theme, and export every link as plain text.

Built with Swift, SwiftUI and SwiftData. No third-party code.

## Requirements

- A Mac with Xcode 27 or later
- An iPhone on iOS 26 or later, or the iOS Simulator
- An Apple ID (a free one works)

## Run it in the Simulator

1. Clone the repo and open `Stash.xcodeproj`.
2. Choose the **Stash** scheme and an iPhone simulator, then press **⌘R**.

It starts with sample saves so there's something to look at. For an empty stash, untick `-sampleData` under *Product → Scheme → Edit Scheme → Run → Arguments*.

## Install it on your iPhone

This is how Stash was first installed on a real phone.

1. **Connect the iPhone** with a cable and tap *Trust* on the phone. If iOS asks, turn on *Settings → Privacy & Security → Developer Mode* and restart.
2. **Sign in to Xcode** with your Apple ID: *Xcode → Settings → Accounts*.
3. **Set your team.** Select the project, then for each of the three targets (*Stash*, *StashShareExtension*, *StashActionExtension*) open *Signing & Capabilities* and choose your team. On someone else's account, also change the bundle identifiers and the App Group (`group.com.g1lg1l.stash`) to your own.
4. **Build and run from Xcode** with your iPhone as the destination (**⌘R**). Do this from Xcode at least once: it registers the App Group that lets the share sheet hand links to the app. A command-line build doesn't.
5. **Trust the developer** on the phone: *Settings → General → VPN & Device Management*, then your Apple ID.
6. **Add Stash to the share sheet.** iOS hides new share extensions. In any share sheet, scroll the row of apps to *More*, tap *Edit* and turn on Stash. *Save to Stash* also appears in the list of actions below; if it doesn't, tap *Edit Actions…*.

With a free Apple ID the app stops opening after 7 days. Run it from Xcode again to renew it. Your saves are kept.

To run the tests, press **⌘U** in Xcode.

## License

[GPL-3.0](LICENSE)
