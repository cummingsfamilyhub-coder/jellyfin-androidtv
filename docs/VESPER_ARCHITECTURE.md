# Vesper target architecture — NAS-first, native-fast

Status: **agreed direction**, not deployed gateway architecture. Updated 2026-10-11.

## System boundary
```
Android mobile / Android TV
  Native Compose UI, playback and controls, session token,
  bounded thumbnail/data cache, offline download files + local manifest
                     | HTTPS (single Vesper API origin)
                     v
NAS (Docker + Caddy): Vesper Gateway
  Authentication/authorisation, profiles, per-user settings,
  integration adapters, credentials, cross-device MyV music,
  search aggregation/index and cache, audit/observability
      |            |           |           |           |
   Jellyfin    Music       Seerr       Aurral      LazyLibrarian /
                Assistant                         Audiobookshelf

Large media: Android <------ authenticated Jellyfin stream ------- NAS
  (Avoid relaying video through the gateway where possible.)
```

## Authority rules
| Data or function | Source of truth | Device storage allowance |
|---|---|---|
| Jellyfin video catalogue, watch progress and video favourites | Jellyfin | Bounded cache and unsynchronised offline progress |
| Music library, playback queues, controllable speakers | Music Assistant | Active playback state/cache as needed |
| MyV music favourites, Vesper-specific lists and preferences | Gateway database, partitioned by user | Small offline snapshot / pending synchronisation queue |
| Integration service credentials and configuration | Gateway secrets/config | **Not** stored on client after migration |
| Profiles, access policy and family permission checks | Gateway + Jellyfin identity | Short-lived, scoped device session using Android Keystore |
| Movies/TV/downloadable media | Jellyfin library and storage | Explicit encrypted or app-private offline copy where supported |
| Images, UI and local playback buffers | Client | Bounded expiring cache (critical for responsiveness) |
| Search and cross-service aggregation | Gateway progressively, with Jellyfin and MA as underlying authorities | Last results/cache, not authoritative |

## Non-negotiables
- Fast startup and smooth scrolling remain native; do not convert Vesper to a mere WebView wrapper.
- Support Wi-Fi and mobile data using a canonical HTTPS endpoint. No manual home/away selector or raw NAS ports publicly exposed.
- Authentication is user-scoped. An invited relative cannot administer the NAS or inherit the owner's integration credentials.
- Apply TLS, server-side authorisation, request validation, rate limiting and constrained network permissions. A gateway is **not** permission to expose unrestricted service APIs.
- Profile and integration settings must be server-side, but devices necessarily keep a protected session token, downloaded media, cache, accessibility/device-specific state and local playback state.
- Media playback goes directly to Jellyfin when possible (including appropriate transcoding), not through a heavyweight new relay.
- Existing Jellyfin integration, PIN continuity and installed APK identity must survive migration.

## Incremental migration (no big-bang rewrite)
1. Capture a baseline of load/search/player latency on Wi-Fi and 5G; record current endpoints and owner of each datum.
2. Introduce gateway with health check, auth, protected per-user read-only settings route and instrumentation — no changes to streaming path.
3. Migrate **one** per-user data domain (MyV music favourites) with dual-read/one-time import and verified rollback. Demonstrate cross-device sync.
4. Move service tokens/configuration to gateway, then remove persistent device copies and audit for leakage. Gracefully handle expired user sessions.
5. Move search aggregation only where measurements justify it; refactor Android screens in small units with regression checks.
6. Later add offline downloads using Android Media3/Jellyfin-compatible download jobs and local playback licence/auth policy appropriate to the user's own library.

## Gateway implementation constraints
A small service + relational data store is sufficient. Choose an implementation after the Phase 0 audit; prefer the simplest well-supported stack and backups. SQLite can serve a single-writer small gateway with consistent snapshots, but don't assume that NAS JBOD constitutes redundancy. Use volume mounts and a restore-tested backup outside the drive hosting the database. NAS SSD is **deferred** unless metrics show it is worthwhile.

## Offline playback concept (not implemented)
The phone downloads an explicitly selected film/episode or prepared compatible stream, validates completeness and plays entirely offline in airplane mode. Include Wi-Fi-only policies, user storage limits, visible progress, cancellation and deletion. Maintain pending progress locally and synchronise after reconnection. Confirm the approach handles expiring Jellyfin transcoding/session URLs and device storage constraints; never assume streaming URLs remain downloadable forever.

## Performance gates
Measure cold launch, returning launch, search responsiveness, browse/scroll jank, player start time, 5G/Wi-Fi transition, gateway latency and offline operation on the actual Android handset. Each migration stage must be no worse than baseline for core journeys, or be rolled back/optimised before further migrations. Do not invent target milliseconds before measuring the baseline.
