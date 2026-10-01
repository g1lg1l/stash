# Stash

"Save for later" inbox, one repo for every platform. Each app is native and self-contained; they share no code. `REQUIREMENTS.md` is the contract between them: when behavior changes on one side, update it there and flag the other side. `README.md` files are for people using the app; `CLAUDE.md` files are for working on it.

```
apps/ios/        iPhone app: Swift 6, SwiftUI, SwiftData, iOS 26+         (see apps/ios/CLAUDE.md)
apps/android/    Android app: Kotlin, Compose, Room, Android 12+          (see apps/android/CLAUDE.md)
apps/web/        Web client: Svelte 5 + Vite, on GitHub Pages                (see apps/web/CLAUDE.md)
supabase/        Backend (free tier): migrations/ is the schema, tests/ the RLS checks
.github/         web.yml tests, builds and deploys apps/web on every push to main
REQUIREMENTS.md  Features and behavior both apps implement, including how sync will work
```

Add workspace tooling (pnpm workspaces, `packages/`) only once two JS projects share code.

Email confirmation is on, locally too: confirmation emails land in Mailpit (http://127.0.0.1:54324) and their links go to the web client's dev server (`site_url` in `config.toml`). The hosted project's Site URL is set in the dashboard (*Authentication → URL Configuration*), not by `config.toml`: never `supabase config push`, it would send the local URLs.

Supabase runs locally in Docker, no account needed: `supabase db start` (just Postgres), `supabase test db`, `supabase stop`; `supabase start` brings up the whole stack with Studio. The CLI is from `brew install supabase/tap/supabase`. A new schema change is `supabase migration new <name>`, never an edit to an existing migration. The hosted project is connected to the repo through Supabase's GitHub integration, so pushing to `main` deploys new migrations.

Run each platform's commands from its own folder (`cd apps/android && ./gradlew …`, `xcodebuild -project apps/ios/Stash.xcodeproj …`, `cd apps/web && npm test`).

## Workflow

- The repo is public. `main` is the only branch: keep it linear and every commit buildable. No build output, local config or secrets in commits (`local.properties`, `xcuserdata/`, `.env`, the Supabase service role key or database password). Row level security on every table: the client keys are public.
- Work is tracked as GitHub issues on `g1lg1l/stash`. Scope commits by platform and reference the issue: `feat(android): add Settings (#14)`, `fix(ios): …`, `docs: …` for shared files.
- Once the build and tests pass, commit and push straight to `main` and close the issue with a short comment. No PRs. Never force push unless asked.
- Commit as `g1lg1l <gilbert.ndresaj@gmail.com>`. It's set in this repo's local git config; the global identity on this Mac belongs to someone else.
- Comments explain why, sparingly. `ponytail:` comments mark deliberate shortcuts and when to upgrade them.

## README image

`docs/hero.png` is `docs/hero.html` rendered by Chrome at 2400×1350, device scale 1 (headless Chrome through `puppeteer-core`, installed outside the repo). It frames `apps/web/docs/screenshots/wide.png`, `apps/android/docs/screenshots/explore.png` and `apps/ios/docs/screenshots/home.png`: retake those first, all at 9:41 with the sample saves, then render again.
