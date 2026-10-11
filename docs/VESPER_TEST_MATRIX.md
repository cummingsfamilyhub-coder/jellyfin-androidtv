# Vesper smoke tests and acceptance matrix

Updated 2026-10-11. Device testing is required; source review and green GitHub Actions are not substitutes.

## Every mobile release
| Area | Test | Pass evidence |
|---|---|---|
| Install | Install APK over current app without uninstall | Same package, previous profile/settings intact |
| Authentication | Startup/login, profile switching and PIN recovery | Correct user and no data crossover |
| Video Home | Continue Watching hero, Resume, More Info, MyV | Correct title, playback and favourite state |
| Library | Movies, TV, individual collection titles, pagination, sort and genre | Browse and navigate without missing/duplicated entries |
| Video playback | Play/resume, seek, skip intro, next episode, controls | Playback and progress update correctly |
| Music | Artists, genre families, albums/tracks, This Device and room playback | Working local and remote controls, artwork and navigation |
| Music queue | Queue add/remove/reorder, mini-player/Now Playing/background audio | Action feedback and consistent queue state |
| Unified search | Film, TV episode, artist, album and track | Owned/requestable distinction, direct navigation and correct item |
| MyV | Save/remove video and music favourite | Immediate feedback and consistent state |
| Navigation | Last row of every relevant grid with player visible | No items obscured by dock/mini-player |
| Network | Home Wi-Fi → 5G with active playback/search | No re-login or broken sessions when supported |
| Failure state | Disconnect one service or NAS and retry | Useful message, no silent failure or crash |
| Services | Artwork/logos, Seerr and collections | No regressions |

## NAS gateway milestones
- Authenticate as two different users: no credential or history crossover.
- Save MyV song on Device A; see it on Device B under same profile; preserve existing favourites.
- Normal user cannot retrieve Music Assistant/API keys, modify gateway secrets or access unrestricted NAS admin APIs.
- Gateway backup restores user favourites/settings and authentication configuration without manual recreation.
- Wi-Fi/5G baseline repeated. Do not assert speed improvement without comparable measurements.
- Test gateway outage: explain recoverable failure; cached browsing may be available where designed, but don't claim full offline access until implemented.

## Offline milestone
- Download one permitted film and one TV episode; interrupt and resume a transfer.
- Play the fully downloaded title in airplane mode with subtitles/audio as available.
- Resume offline, reconnect, sync watch progress once without corrupting server progress.
- Delete and verify storage returns; confirm profiles and logout cannot casually expose another person's downloads.

Record pass/fail and tested device/build in `VESPER_STATUS.md`, with outstanding issues to fix before introducing another release.
