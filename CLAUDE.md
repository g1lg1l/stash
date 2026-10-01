# Stash

"Save for later" inbox, one repo for every platform. Each app is native and self-contained; they share no code. `REQUIREMENTS.md` is the contract between them: when behavior changes on one side, update it there and flag the other side. `README.md` files are for people using the app; `CLAUDE.md` files are for working on it.

```
apps/ios/        iPhone app: Swift 6, SwiftUI, SwiftData, iOS 26+         (see apps/ios/CLAUDE.md)
apps/android/    Android app: Kotlin, Compose, Room, Android 12+          (see apps/android/CLAUDE.md)
supabase/        Backend (free tier): migrations/ is the schema, tests/ the RLS checks
REQUIREMENTS.md  Features and behavior both apps implement, including how sync will work
```

Planned: `apps/web/` for the web client. Sync isn't built into the apps yet. Add workspace tooling (pnpm workspaces, `packages/`) only once two JS projects share code.

Supabase runs locally in Docker, no account needed: `supabase db start` (just Postgres), `supabase test db`, `supabase stop`; `supabase start` brings up the whole stack with Studio. The CLI is from `brew install supabase/tap/supabase`. A new schema change is `supabase migration new <name>`, never an edit to an existing migration. The hosted project is connected to the repo through Supabase's GitHub integration, so pushing to `main` deploys new migrations.

Run each platform's commands from its own folder (`cd apps/android && ./gradlew …`, `xcodebuild -project apps/ios/Stash.xcodeproj …`).

## Workflow

- The repo is public. `main` is the only branch: keep it linear and every commit buildable. No build output, local config or secrets in commits (`local.properties`, `xcuserdata/`, `.env`, the Supabase service role key or database password). Row level security on every table: the client keys are public.
- Work is tracked as GitHub issues on `g1lg1l/stash`. Scope commits by platform and reference the issue: `feat(android): add Settings (#14)`, `fix(ios): …`, `docs: …` for shared files.
- Once the build and tests pass, commit and push straight to `main` and close the issue with a short comment. No PRs. Never force push unless asked.
- Commit as `g1lg1l <gilbert.ndresaj@gmail.com>`. It's set in this repo's local git config; the global identity on this Mac belongs to someone else.
- Comments explain why, sparingly. `ponytail:` comments mark deliberate shortcuts and when to upgrade them.
