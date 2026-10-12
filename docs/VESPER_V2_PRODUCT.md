# Vesper 2 — product definition and experience principles

Updated: 2026-10-12. **Product direction approved, individual screen designs not yet approved.** This file supersedes V1-centric navigation and feature prioritisation in historical documents.

## Vision
Vesper is a self-hosted, ad-free, family entertainment platform with one unified, personalised experience across phone, tablet/foldable and Android TV.

Six first-class media domains:
- **Movies** and **TV Shows** (Watch)
- **Music**, **Audiobooks**, **Podcasts** (Listen)
- **eBooks** (Read)

**Live TV** is an undecided optional future domain. Design an extensible Watch section, but do not build tuner/IPTV/EPG/DVR functionality or show an empty Live TV entry until its inclusion and legal/technical source are agreed.

## Primary navigation — approved direction
- **MyV is the default unified Home.** Do not create a separate Home destination that duplicates MyV.
- Primary destinations: **MyV / Watch / Listen / Read**.
- Global Search and profile switcher accessible in the header; Downloads and Settings accessible from the menu rather than a permanent navigation tray.
- **Collapsible sidebar**, no always-visible bottom dock:
  - Phones: hidden overlay drawer, opened with a menu icon; optional left-edge gesture must not interfere with media gestures.
  - Tablet/foldable: optional compact icon rail expanding into labels when screen space permits.
  - Android TV: compact rail expanding on focus, fully remote navigable.
- Navigation and page layout must remain usable with media mini-player visible; the mini-player is contextual and must not obscure content.

## MyV is the product's centre of gravity
MyV is NOT only a favourites folder. It brings together **continue watching / listening / reading**, next episodes, favourites and saved media, user playlists/collections, selected new arrivals, and downloads. All relevant media types can appear, but **do not display six enormous mandatory shelves** on every visit.

Initial layout guidance (to validate with wireframes):
1. Small header with Vesper, profile and search.
2. One compact cross-media Continue section, ordered by useful recency/intent, with clear type labels.
3. Personal content (saved lists and favourites) prioritised ahead of catalogue promotion.
4. A modest What's New / Up Next area, with user-specific relevance.
5. Contextual download status/quick access only when applicable.

The first-use/empty MyV state must be purposeful: suggested actions to save/continue something rather than acres of blank shelves. Screen density is deliberately compact, especially on folded phones and TV.

## Watch / Listen / Read
- **Watch:** film and TV browsing, series details, episodes, collections, playback, future optional Live TV.
- **Listen:** music albums/artists/playlists/radio, audiobook progress, podcast subscriptions and episodes; all within one coherent audio destination while retaining content-specific detail/player affordances.
- **Read:** ebook library, book details, reader, progress and bookmarks; audiobook editions can cross-link where metadata allows.
- Don't force movie posters, square album artwork and book covers into one universal card shape.
- One consistent Vesper design system: spacing, typography, colour, artwork handling, overlay controls, dialogs, feedback, focus and loading states.

## Minimalism principles
- **Progressive disclosure:** show the next likely action, not every possible action.
- Fewer top-level choices; deeper controls live contextually.
- Prioritise personal content over provider/service branding and clutter.
- No placeholder tabs or feature badges merely because something is on the roadmap.
- Avoid gratuitous carousels, oversized heroes, extra floating controls or design polish that hides unfinished functionality.
- Accessibility, large TV targets, foldable layouts and remote focus are acceptance criteria, not optional polish.

## Product/technical boundaries
- **Greenfield Vesper 2 client**, not another iteration of the V1 Jellyfin Android TV fork. Use existing V1 as read-only reference and preserve installed V1 untouched. New app/repository/package identity when implementation is approved.
- Reuse reliable building blocks: Jellyfin as video server, Music Assistant as music backend, Seerr for discovery/requests, existing audiobook/book stack where validated; choose libraries based on actual requirements. Do not reimplement media codecs.
- **NAS-first shared authority:** per-user settings, profiles, app business state, service credentials, shared MyV and integration policy live on the NAS.
- **Native-device-fast:** Kotlin/Compose mobile and Compose TV are the current proposed client direction, not a final approved implementation choice; preserve local device session, short-lived cached data, playback controls, offline media, pending sync.
- Never require every movie stream to traverse the application gateway; preserve direct authenticated Jellyfin streaming where appropriate. Benchmark startup, browsing, search and playback on LAN/5G.
- Secrets belong server-side. Scoped session credentials, secure local playback caches and downloaded files necessarily live on devices.
- NAS hard drives are not a replacement for recoverable off-device backups. SSD is an evidence-based future decision.

## Explicitly undecided
- Exact MyV visual layout and degree of personalisation.
- Whether Listen defaults to a combined feed or remembers a chosen music/audiobook/podcast subsection.
- Live TV inclusion/source, EPG, recording and timeshift.
- Final client technology choices, NAS gateway/database, offline media formats and API architecture.

## Next finishable step (DESIGN — not coding)
Make a **screen map and low-fidelity wireframe set** for MyV, the closed/open sidebar, Watch, Listen, Read and a cross-media Search screen. Include phone, unfolded foldable and TV navigation behaviours. Review MyV first before continuing.

**Definition of done:** user approves what the four main destinations do, what the MyV first screen prioritises, and which features are deliberately hidden until needed. Then derive architecture, gateway boundaries and implementation slices from the approved flows.
