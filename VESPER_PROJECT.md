# Vesper — project home

**Purpose:** A responsive, privately hosted media experience for the household and eventually invited relatives: video, music, requests, books/audiobooks and (later) offline downloads. Maintain one existing Jellyfin-derived Android codebase with touch-first mobile and TV surfaces.

## Where everything lives
- **Code and living documentation:** `cummingsfamilyhub-coder/jellyfin-androidtv`, active branch `vesper-mobile-vnext`.
- **What we're doing right now:** [`VESPER_STATUS.md`](VESPER_STATUS.md). Always read this first and confirm the live branch head; dates and commits here are historical if the repository has advanced.
- **Architecture and NAS boundary:** [`docs/VESPER_ARCHITECTURE.md`](docs/VESPER_ARCHITECTURE.md).
- **Phase gates and sequencing:** [`docs/VESPER_ROADMAP.md`](docs/VESPER_ROADMAP.md).
- **Security, credentials and backup controls:** [`docs/VESPER_SECURITY.md`](docs/VESPER_SECURITY.md).
- **Session, change and APK hand-off:** [`docs/VESPER_DELIVERY.md`](docs/VESPER_DELIVERY.md).
- **Regression and acceptance checks:** [`docs/VESPER_TEST_MATRIX.md`](docs/VESPER_TEST_MATRIX.md).
- **Decisions and open questions:** [`docs/VESPER_DECISIONS.md`](docs/VESPER_DECISIONS.md).
- **ChatGPT Project instructions:** [`PROJECT_INSTRUCTIONS.md`](PROJECT_INSTRUCTIONS.md).

## Latest agreed direction (11 October 2026)
1. **Preserve Vesper**; do not start over or polish the UI for its own sake.
2. **NAS-first shared state and secrets**, native Android presentation and playback for speed and reliable controls. A device will still require a scoped authentication token and local files for offline media.
3. **Baseline and risk audit first**, then an incremental Vesper Gateway, controlled migration and a measured refactor, then offline video.
4. Never assume a source commit has been built or installed. The current status file must keep distinct: **source head / CI / published APK / installed and user-tested version**.

## Known working product baseline
User screenshots on 11 October show a working video Home/Continue Watching hero, Movies and TV grids, Services and collections, music hero/mini-player, artists and grouped genre filter, MyV music, and a unified Search landing screen. These visuals are observations, not proof of every API or offline feature. The exact installed APK commit shown in screenshots was **not independently verified**. Existing code includes Jellyfin, Music Assistant, Seerr, Aurral, LazyLibrarian and Audiobookshelf integration points; configuration and end-to-end availability vary.

## Working style
A new chat in the **Vesper app** ChatGPT Project should simply ask: **“Continue Vesper from the current repo and VESPER_STATUS.md. What's the next agreed milestone?”** The assistant should verify the current repo itself; no pasted encyclopaedic handover prompt.