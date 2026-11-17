# Stash

"Save for later" inbox, one repo for every platform. Each app is native and self-contained; they share no code. `REQUIREMENTS.md` is the contract between them: when behavior changes on one side, update it there and flag the other side. `README.md` files are for people using the app; `CLAUDE.md` files are for working on it.

```
apps/ios/        iPhone app: Swift 6, SwiftUI, SwiftData, iOS 26+         (see apps/ios/CLAUDE.md)
apps/android/    Android app: Kotlin, Compose, Room, Android 12+          (see apps/android/CLAUDE.md)
apps/web/        Web client: Svelte 5 + Vite, on GitHub Pages                (see apps/web/CLAUDE.md)
supabase/        Backend (free tier): migrations/ is the schema, tests/ the pgTAP checks, functions/ the Edge Functions
.github/         web.yml tests, builds and deploys apps/web on every push to main
REQUIREMENTS.md  Features and behavior both apps implement, including how sync will work
```

Add workspace tooling (pnpm workspaces, `packages/`) only once two JS projects share code.

Stash shares one hosted Supabase project (ref `yuapnmbvkmdjsgccqlgx`) with Midori and Matcha, so none of them is paused for inactivity. A project has one sign-in setting and the hosted one belongs to Midori's repo (`supabase/config.toml` there): email confirmation is off, because Midori's username accounts cannot confirm an email. Do not run `supabase config push` from this repo: it would turn confirmation on and anonymous sign-ins off for all three apps. `config.toml` here only drives the local stack. Every Stash object in the shared database comes from `supabase/migrations`; apply a new migration with `supabase db query --remote=yuapnmbvkmdjsgccqlgx -f <file>`, not `db push` (the migration history there is Midori's).

Supabase runs locally in Docker, no account needed: `supabase db start` (just Postgres), `supabase test db`, `supabase stop`; `supabase start` brings up the whole stack with Studio. The CLI is from `brew install supabase/tap/supabase`. A new schema change is `supabase migration new <name>`, never an edit to an existing migration. Pushing to `main` does not reach the hosted project (the GitHub integration doesn't deploy): apply each new migration by hand after the push, the way the paragraph above says. There are no Edge Functions.

Run each platform's commands from its own folder (`cd apps/android && ./gradlew …`, `xcodebuild -project apps/ios/Stash.xcodeproj …`, `cd apps/web && npm test`).

## Workflow

- The repo is public. `main` is the only branch: keep it linear and every commit buildable. No build output, local config or secrets in commits (`local.properties`, `xcuserdata/`, `.env`, the Supabase service role key or database password). Row level security on every table: the client keys are public.
- Work is tracked as GitHub issues on `g1lg1l/stash`. Scope commits by platform and reference the issue: `feat(android): add Settings (#14)`, `fix(ios): …`, `docs: …` for shared files.
- Once the build and tests pass, commit and push straight to `main` and close the issue with a short comment. No PRs. Never force push unless asked.
- Commit as `g1lg1l <gilbert.ndresaj@gmail.com>`. It's set in this repo's local git config; the global identity on this Mac belongs to someone else.
- Comments explain why, sparingly. `ponytail:` comments mark deliberate shortcuts and when to upgrade them.

## README image

`docs/hero.png` is `docs/hero.html` rendered by Chrome at 2400×1350, device scale 1 (headless Chrome through `puppeteer-core`, installed outside the repo). It frames `apps/web/docs/screenshots/wide.png`, `apps/android/docs/screenshots/explore.png` and `apps/ios/docs/screenshots/home.png`: retake those first, all at 9:41 with the sample saves, then render again.
