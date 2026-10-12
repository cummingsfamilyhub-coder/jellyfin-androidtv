# VESPER_STATUS.md

Last updated: 2026-10-12
Active branch: `vesper-mobile-vnext`
Base branch: `vesper`
Historical PR: #1 (closed) — Vesper mobile vNext shell


## CURRENT DIRECTION — 2026-10-12: VESPER 2 GREENFIELD DESIGN

This direction **supersedes Vesper 1 incremental refactor/gateway-first delivery**. The user has explicitly chosen a **new Vesper application codebase**, while preserving Vesper 1 untouched as a working reference and reusing the existing NAS media servers.

Read [docs/VESPER_V2_PRODUCT.md](docs/VESPER_V2_PRODUCT.md) FIRST. Product has six first-class domains: Movies, TV, Music, Audiobooks, Podcasts and eBooks; optional Live TV **undecided**. The personalised, unified **MyV IS the Home screen**, with top-level **MyV / Watch / Listen / Read**, global search/profile in the header and a collapsible sidebar (no permanent bottom tray). Minimalist visual density and user-centred MyV matter more than adding more navigation.

**Current stage: product/experience design, not code.** Next deliverable: screen map plus low-fidelity wireframes for MyV/sidebar, Watch, Listen, Read and Search, checked on phone, foldable and TV. Do not create a replacement repo, deploy gateway or rewrite source before design/architecture approvals. Existing V1 historical release notes below remain useful evidence but are NOT current implementation instructions.

## HISTORICAL VESPER 1 WORKING STATUS — 2026-10-11

**Active phase:** Phase 0 — baseline, security inventory, performance measurements and rollback plan. The next action is a **read-only audit**, not a new feature or NAS deployment.

**Project entry point:** [VESPER_PROJECT.md](VESPER_PROJECT.md). The ChatGPT Project instructions are [PROJECT_INSTRUCTIONS.md](PROJECT_INSTRUCTIONS.md). Phase plan: [docs/VESPER_ROADMAP.md](docs/VESPER_ROADMAP.md). Technical architecture, security, delivery, regression tests and decisions are linked from the project entry point.

**Release state (do not collapse these):**
- Last independently identified user-tested source baseline: `21d6866` for video controls and grouped music genres.
- Latest functionality changes committed: `302529e` (search drill-down, episode search, display/scroll and action-feedback fixes).
- Current screenshots show Vesper working, but **the installed APK commit is not verified** by the screenshots alone.
- CI, release target SHA and installed-source equivalence for `302529e` remain **unverified** in this handover. Verify each against GitHub Actions/release metadata before providing an update as tested.
- Later documentation-only commits do not imply a rebuilt APK.

**Product direction agreed:** NAS-first shared settings, API keys, MyV music favourites and gateway; native Android UI/playback, bounded local caches, device authentication and future offline media. Do not turn Vesper into a remote WebView or compromise performance. Do not buy an SSD without measurements.

**Phase 0 definition of done:** dated inventory of repo/NAS/device data ownership and credential exposure; actual Wi-Fi/5G baseline; recoverable backups and APK/signing rollback design; proposed gateway security/session boundaries; user review. No destructive changes.

**Keep working:** Jellyfin browse/playback, services and collections, Seerr, Music Assistant This Device and other players, profiles, MyV, genre picker and the familiar Vesper visual design.

---

## Source of truth

This file is the hand-off point for future Vesper sessions.

Do not modify or uninstall Vesper 1; use it as a reference. Vesper 2 will be a separately designed codebase after product approval.

## 2026-10-11 — Search & Stability update (awaiting APK verification)

The latest user-tested APK before these changes is commit `21d6866`:
video Resume/More Info sizes and compact MyV control, and the grouped
Music Artists genre selector were confirmed working. **Preserve them.**

Source commit `302529e8a6988520ae20ed564cc92f1b1dc046d4` on
`vesper-mobile-vnext` adds:
- Jellyfin unified search for individual episodes alongside films and series;
- separate "Already in your library" vs "Available to request" result headings
  for video, music and books/audiobooks;
- artist and album detail navigation directly from music search results,
  reusing existing artist/album detail screens;
- consistent scroll clearance beneath the persistent mini-player and nav;
- explicit user-facing errors where MyV/music playback/queue setup was
  previously missing and calls silently returned.

**CI not yet verified** at the time of this note. The dedicated
`vesper-mobile-build.yml` is push-triggered for app changes and publishes
`vesper-mobile-latest` only after build/signing verification passes.
Before supplying or installing an APK, check GitHub Actions results and
confirm the rolling release points at commit `302529e` (or a later
reviewed build). Do not uninstall the current working APK.

Post-build smoke tests:
1. Search for a movie, series episode, artist and album; verify direct
   detail navigation and working Back.
2. Verify owned vs requestable results are clearly separated.
3. Scroll to the very last artist, movie, episode and MyV item with
   persistent music playback active.
4. Confirm MyV and queue success/failure feedback, music controls,
   existing grouped genre picker, Collections and profile switching.

## Known-good Vesper state

Preserve these areas unless the current task explicitly requires touching them:

- Jellyfin library and search
- Movies / TV / Collections / MyV
- Toy Story-style searches returning individual films rather than only BoxSets
- full-library pagination
- Seerr integration
- Services and the known-good service logo assets/mapping
- playback, skip intro, next episode, restart and seek
- canonical Jellyfin remote access
- profile switching, PIN and character avatars
- Vesper-style Settings
- compact Home design
- existing Music Assistant library, rooms, grouping, mini-player and Now Playing UI

Do not re-add Chromecast casually. It was deliberately removed after repeated failures.

## Current milestone — Vesper Music Anywhere

Goal:

1. Music Assistant reachable through one canonical secure HTTPS endpoint.
2. Vesper appears in Music Assistant as a real player called **This Device**.
3. Android background audio, media notification, lock-screen/headset controls and audio focus.
4. Existing Vesper mini-player / Now Playing represents the same MA-controlled local session.
5. Wi-Fi ↔ 5G handoff without logout, reconnect mode or separate remote mode.

Do not expose raw port 8095 publicly.

## What is already implemented

### Canonical Music Assistant endpoint

`VesperMusicEndpoint.kt`

- accepts local private-network HTTP for LAN compatibility
- requires HTTPS for remote hosts
- derives Music Assistant HTTP API from the same base URL
- derives Sendspin WebSocket as:
  - local: `ws://<host>:8095/sendspin`
  - secure proxy: `wss://<canonical-host>/sendspin`
- current Music Assistant Sendspin endpoint has been verified as `/sendspin`

### Secure Sendspin engine

`:sendspin-shared`

- official Music Assistant Sendspin engine is vendored and isolated
- source attribution and licence files are present
- secure Noise/pairing/trust code is included
- persistent player identity/trust is stored locally
- no runtime MA token is committed to source

### This Device Android playback

Implemented:

- `VesperSendspinRuntime.kt`
- `VesperSendspinKeyStore.kt`
- `VesperAudioTrackSink.kt`
- `VesperMusicPlaybackService.kt`

Behaviour already wired:

- player advertises as **This Device**
- MA remains the queue/control plane
- Android AudioTrack output
- foreground media playback service
- platform MediaSession
- media notification
- previous / play-pause / next controls
- audio focus
- noisy-output handling
- Bluetooth/headphone routing follows Android
- background playback
- network-change monitoring/reconnect support
- service refreshes Vesper UI when local playback state/track changes

### Vesper UI integration

Latest changes:

- **This Device** is prioritised ahead of rooms in Music Assistant player sorting
- Play On picker is structured as:
  - This Device
  - Rooms
  - Groups
- persistent Vesper mini-player prefers an active **This Device** session when one exists
- background service emits refresh events when its MA player state changes so the in-app mini-player/Now Playing stays current

Latest functional commits before this status file:

- `0dfe673472` — Prioritise This Device in music outputs
- `52d1e5d05d` — Refresh Vesper UI from background music state
- `e321e43ae9` — Promote This Device in Vesper music UI
- `49284624ec` — Fix Vesper APK certificate verification

## Build / CI state

At commit `e321e43ae9cf5f2f5fd60734fdae0af95e25c5a0`:

- App / Build: **passed**
- App / Test: **passed**
- App / Lint: **passed**

The dedicated `vesper-mobile-build.yml` workflow:

- compiled the mobile APK successfully
- collected it successfully
- re-signed it successfully
- verified package identity successfully
- then failed only in the custom signing-certificate comparison step

That comparison script was fixed in commit `49284624ec` to compare the same normalised SHA-256 fingerprint format from `keytool` and `apksigner`.

Check the latest workflow result before claiming an APK is ready.

## Signing / update continuity

Do not rotate the signing key.

The installed Vesper package identity must stay stable:

`app.vesper.androidtv.debug`

The current stable Vesper debug signing key is still used so APKs can install over the existing app without wiping app data.

Longer-term security cleanup can move signing material to GitHub Actions secrets, but only after fingerprint/update continuity is proven.

## Remote Music Assistant — remaining infrastructure work

The app-side secure endpoint model is ready, but the NAS/Caddy side still needs to be configured and validated.

Existing infrastructure already works for Jellyfin and Seerr:

- DuckDNS
- ports 80/443 forwarded to Caddy
- TLS/ACME working
- remote 5G access working

Remaining Music Assistant work:

1. choose/configure a canonical HTTPS Music Assistant hostname
2. Caddy reverse-proxy that hostname to Music Assistant on the NAS
3. ensure WebSocket proxying works for:
   - `/sendspin`
   - MA WebSocket API where required
4. enter the HTTPS base URL in Vesper Settings
5. Test connection
6. validate on Wi-Fi
7. switch to 5G without logging out/reconfiguring
8. verify This Device playback and controls survive/reconnect correctly

Do not create a user-facing local/remote toggle.

## Next exact checkpoint

Before adding more features:

1. Check CI for the latest branch head.
2. If the dedicated mobile workflow is green, obtain the signed updateable APK.
3. Install it over the current Vesper build — no uninstall.
4. Test:
   - This Device appears first in Play On
   - music plays through the phone/tablet
   - app can be backgrounded
   - notification and lock-screen controls work
   - Bluetooth/headphone controls work
   - Vesper mini-player updates on track/state changes
5. Then configure/test the canonical remote Music Assistant Caddy endpoint and perform Wi-Fi → 5G handoff testing.

Only after that should we move on to polish/security cleanup.

## Guardrails

Do not:

- rebuild Vesper from scratch
- redesign unrelated screens
- regress Services icons
- reintroduce Wi-Fi/mobile endpoint switching
- expose port 8095 directly
- commit API keys, MA tokens, passwords or DuckDNS tokens
- rotate the Android signing key
- confuse Jellyfin `MobilePlayerActivity` with Music This Device playback
- claim a build is ready until the dedicated mobile workflow and APK checks are green
