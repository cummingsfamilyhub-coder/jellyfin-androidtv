# Vesper

Vesper is a custom Android TV client built from Jellyfin Android TV.

## Baseline
- Upstream: jellyfin/jellyfin-androidtv
- Stable baseline: release-0.19.z
- Development branch: vesper
- App name: Vesper
- Debug application ID: app.vesper.androidtv.debug

The first milestone deliberately leaves Jellyfin authentication, API access, playback, transcoding, subtitles and media handling intact while Vesper replaces the browsing experience.

## Product structure

### Left navigation
Home · Movies · TV Shows · Kids · Search · Settings

### Home
Continue Watching · MyV · Movies · TV Shows · Services

MyV maps to Jellyfin favourites. Services will use curated Jellyfin collections such as Netflix, Disney+, Max and Prime Video.

### Browse
Movies and TV Shows use poster grids with lightweight genre and sort filters. Genres are filters rather than top-level destinations.

## Milestone 1
Vesper installs alongside Jellyfin, connects to a Jellyfin server, signs in, displays real library data, opens an item and starts playback using Jellyfin's playback stack.

## Licence
Derived from Jellyfin Android TV and subject to GPL-2.0.
