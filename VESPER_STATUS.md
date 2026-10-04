# VESPER_STATUS.md

Last updated: 2026-10-04
Active branch: `vesper-mobile-vnext`
Base branch: `vesper`
Draft PR: #1 — Vesper mobile vNext shell

## Source of truth

This file is the hand-off point for future Vesper sessions.

Do not rebuild or redesign Vesper. Inspect the current branch first, then continue from the state below.

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
