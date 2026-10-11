# Vesper roadmap and release gates

Updated 2026-10-11. **One active phase at a time.** New functionality is frozen until the foundations are sound. Phase completion requires verified evidence, not a reassuring commit message.

## Phase 0 — Baseline, security audit and rollback (**NEXT**)
**Deliverable:** a current-state inventory; data ownership map; secure migration plan; baseline timing; tested configuration backup and rollback.
- Verify branch, latest source, CI, published APK and installed/user-tested build *separately*.
- Inspect repo for tokens, keystore material, settings, Android backup/cleartext policies, PIN flows, dependencies and device-side databases.
- Inventory live NAS services/volume paths **read-only** if access is authorised, without logging credentials.
- Record startup/search/playback timing on Wi-Fi and 5G, plus crash/scan issues.
- Capture signed release/update identity and immutable rollback path; protect NAS config backups off-device.
**Gate:** inventory, risk register, migration/rollback design and baseline reviewed. No destructive action.

## Phase 1 — Minimal NAS Vesper Gateway
**Deliverable:** deployed authenticated service beside existing Docker stack.
- Health endpoint, HTTPS behind Caddy, secure user identity mapping, per-profile authorisation and logging with secrets redacted.
- A small per-user settings read endpoint and database with off-NAS backup.
- Keep existing Jellyfin streaming and music playback untouched.
**Gate:** authenticated request on Wi-Fi and 5G; no unsolicited data exposed; restore tested; performance no worse.

## Phase 2 — Centralise settings/secrets and shared data
**Deliverable:** gateway is source of truth for MyV music and service keys.
- Import device MyV music safely; maintain existing video MyV through Jellyfin.
- Prove cross-device MyV consistency and permission separation for distinct profiles.
- Migrate integration credentials from client to gateway, revoke/rotate as appropriate, remove persistent client copies after confirmed success.
- Update/verify PIN and sign-in continuity. Do not silently rotate Android signing key.
**Gate:** zero lost favourites/settings; two devices converge; normal user cannot access admin secrets; rollback works.

## Phase 3 — Controlled debt reduction and performance
**Deliverable:** modular code + measured improvements.
- Split oversized `MobileMainActivity.kt` by feature and centralise repeated network/state code in small PR-sized changes.
- Search only relevant selected sections where helpful, add sane paging/cache, handle network errors and preserve navigation.
- Replace brittle state/UI couplings with testable boundaries; expand CI regression coverage.
**Gate:** regression matrix passed, app no slower than baseline, published update signed and installable over existing app.

## Phase 4 — Offline video (films and episodes)
**Deliverable:** one downloaded title plays with **airplane mode enabled**.
- Device-local download manager, quality/network options, completion validation, delete/storage quota, playable file/manifest, queued progress sync.
- Handle transcode URL expiry, interrupted transfer and user logout without leaking other profiles' media.
**Gate:** download → disconnect → play → resume → reconnect → synchronise completed successfully.

## Deferred feature backlog (NOT current scope)
- Books/audiobooks improvements, better search discovery, AI-assisted recommendations/coming-soon, additional profile controls and TV feature parity.
- NAS SSD purchase only after real workload evidence.
- Cosmetic genre picker/menu changes only if blocking usability.

## Phase selection rule
At each handover, identify one next milestone and one stop condition. Resist adding parallel initiatives because they're interesting. An emergency security fix can supersede the phase, but document why.
