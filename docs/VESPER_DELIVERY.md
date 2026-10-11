# Vesper development, CI, release and handover protocol

**Goal:** A new conversation should resume the correct code and state in minutes, not depend on hundreds of old chat messages.

## Session start (always)
1. Read `VESPER_PROJECT.md`, current `VESPER_STATUS.md` and the relevant docs in this repo, on `vesper-mobile-vnext` (unless the status file explicitly changes the active branch).
2. Inspect current branch/source/commit, not just prior messages or a generated APK. Confirm the current active phase and *one* next task.
3. Distinguish these independent facts: (a) source commit, (b) CI workflow outcome, (c) release asset and release tag target, (d) device-installed/tested build. Do not infer (d) from (a)-(c).
4. If missing repository access or live-state evidence, say what's unverified. Never invent links or GitHub checks.

## Cycle
**Agree the exact task → inspect source → implement limited change → run/check CI → verify signing/package and release commit → provide direct APK → user tests → record result.**
- No unrelated refactor or new dependency during a feature change; no parallel Vesper forks or new app.
- Package identity: `app.vesper.androidtv.debug`. Preserve signing certificate, data and existing sign-in. No uninstall to update.
- Build workflow: `.github/workflows/vesper-mobile-build.yml`, branch `vesper-mobile-vnext`. Documentation-only paths should not trigger APK CI.
- Rolling release: `https://github.com/cummingsfamilyhub-coder/jellyfin-androidtv/releases/tag/vesper-mobile-latest`.
- Direct download (ONLY after verifying that rolling release points to the intended successful build): `https://github.com/cummingsfamilyhub-coder/jellyfin-androidtv/releases/download/vesper-mobile-latest/Vesper-mobile-debug.apk`.
- The direct link is *rolling*. Without checking its target SHA it may still contain an older build. Never describe an unverified download as the newest code.
- A build that passes doesn't prove playback, cross-device sync or other real-world behaviour. Record user's test outcome separately.

## What to update after a milestone
`VESPER_STATUS.md` should include:
- **Time and source:** date, active branch and head SHA.
- **Release:** latest build result/run ID (if known), last verified published APK/target commit, last user-tested APK/commit (if known).
- **What's confirmed working**, what's unverified and known regressions.
- **Current phase, one milestone, one blocker, one action**; definition of done.
- **Changes made:** commits, files, tests and rollback points.
- **Decisions**: update `docs/VESPER_DECISIONS.md` if the architectural direction changes.

## Next chat handover
In the ChatGPT Project, a complete new-chat prompt is:

> Continue Vesper from `VESPER_STATUS.md` and the current GitHub `vesper-mobile-vnext` branch. Verify source and release state, then take the next agreed step. Do not rebuild it.

The assistant does the retrieval. Don't demand a giant copied log from the user.
