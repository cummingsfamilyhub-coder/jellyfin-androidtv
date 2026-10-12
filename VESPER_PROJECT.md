# Vesper — project home

**Current product direction (12 October 2026): Vesper 2 is a new, purpose-built application.** Preserve Vesper 1 untouched as the working reference. The existing NAS services and media remain; client architecture and UX are being designed from desired outcomes backwards.

## Read in this order
1. [Vesper 2 product definition](docs/VESPER_V2_PRODUCT.md) — the **current** agreed direction and next design deliverable.
2. [Decision register](docs/VESPER_DECISIONS.md) — dated decisions, superseded V1 choices and remaining open choices.
3. [VESPER_STATUS.md](VESPER_STATUS.md) — V1 historical implementation/release evidence; current V2 stage is marked at the top.
4. [Vesper roadmap](docs/VESPER_ROADMAP.md) — V2 gates at the top, V1 historical plan retained for audit.
5. [Vesper architecture](docs/VESPER_ARCHITECTURE.md) — currently records the older V1 migration target; **not final V2 technical design**.
6. [Security](docs/VESPER_SECURITY.md), [delivery](docs/VESPER_DELIVERY.md), [test matrix](docs/VESPER_TEST_MATRIX.md) — V1 learnings to carry forward selectively.
7. [ChatGPT Project instructions](PROJECT_INSTRUCTIONS.md).

## Product identity
Six core media domains: Movies, TV, Music, Audiobooks, Podcasts and eBooks. Live TV remains undecided and optional.

**MyV is the unified, personalised home** and the expected main destination. Watch / Listen / Read handle deeper exploration. Global Search and profile controls in the header. Minimal design, progressive disclosure and a collapsible sidebar; no permanent bottom dock.

Vesper runs fast native clients backed by the NAS as shared source of truth for settings, per-user data and privileged service credentials. Some client-local cache, short-lived authentication and downloads are necessary. Don't assume the gateway, exact libraries or client framework are locked until the architecture design is signed off.

## Working reference (Vesper 1)
Source: `cummingsfamilyhub-coder/jellyfin-androidtv`, branch `vesper-mobile-vnext`.
V1 includes Jellyfin playback, Music Assistant, discovery/requests, MyV, profiles and genre filtering. Do not silently identify source commits with user-tested APKs. No V2 repository or APK exists yet.

## Immediate milestone
**Design the MyV-first navigation and screen map** for mobile, unfolded foldable and TV before implementation.
Definition of done: user approves entry screen, global navigation, how MyV combines media, and the main Watch/Listen/Read flows. Then derive architecture and a small implementation proof from the approved designs.
