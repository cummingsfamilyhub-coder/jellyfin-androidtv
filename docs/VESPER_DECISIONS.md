# Vesper architectural decision register

Updated 2026-10-12. **Vesper 2 decisions below supersede conflicting V1-era locks.** Decisions are not automatically deployed capabilities.

| ID | Decision | Status | Rationale / consequence |
|---|---|---|---|
| D01 | Keep the existing `jellyfin-androidtv` Vesper fork and one mobile/TV codebase | **SUPERSEDED 2026-10-12 by D13** | Historical V1-only choice; keep V1 intact |
| D02 | Active development on `vesper-mobile-vnext`; `VESPER_STATUS.md` is the handover anchor | Locked for now | Reliable continuity across chat limits; verify branch head each time |
| D03 | Native Android UI, playback and offline storage; NAS-first shared data/logic | Approved target | Do not trade responsiveness for a purely hosted/WebView app |
| D04 | Store shared profile/settings, service API credentials and MyV music on NAS | Approved target, migration pending | User-owned central state, consistent across devices |
| D05 | No NAS SSD purchase until real performance measurements justify it | Locked | Don't solve unmeasured latency with hardware |
| D06 | Preserve signed APK update continuity; no uninstall to update | Locked | Avoid lost accounts/configuration |
| D07 | Keep Jellyfin streams direct where practical, not re-proxied through gateway | Approved target | Avoid unnecessary bandwidth and transcoding overhead |
| D08 | Treat critical gateway/identity settings as irreplaceable; off-NAS recovery required | Locked | JBOD disks are not redundancy/backup |
| D09 | Do not reintroduce Chromecast casually | Locked | Previously removed after unreliable behaviour |
| D10 | No ad-supported services or recommendations | Locked | User preference |
| D11 | Freeze unrelated UI polish and feature expansion until NAS foundations are reliable | **SUPERSEDED 2026-10-12 by D17** | Was the V1 migration strategy |
| D12 | First offline feature is downloadable Jellyfin films and episodes | Planned, not implemented | Clear measurable airplane-mode acceptance test |

## Questions Phase 0 must decide, not guess
- Gateway implementation/runtime and database after inspecting NAS workload and recovery requirements.
- Authentication architecture and token lifetimes, owner-vs-guest access, compromised device revocation.
- Credential rotation/remediation strategy for committed debug signing material while preserving installed-app continuity.
- Exact migration rules for music favourites and local Vesper preferences (import, reconcile, rollback).
- Actual disk targets/backup destinations for the gateway on current NAS volume(s).

## Change control
A change to any locked decision needs the user's explicit agreement and a dated rationale. The status document should point to the updated decision; don't silently reinterpret old choices.

## Vesper 2 decisions (12 October 2026)
| ID | Decision | Status | Rationale / consequence |
|---|---|---|---|
| D13 | Build a greenfield Vesper 2 client, informed by V1 learnings; preserve V1 untouched | **Approved product direction** | Own architecture/visual design without inherited Android TV UI; no V2 repository yet |
| D14 | Six first-class media types: Movies, TV, Music, Audiobooks, Podcasts, eBooks | **Locked** | One unified family media product |
| D15 | MyV is the personalised unified Home; Watch/Listen/Read are other top-level areas | **Locked direction** | Avoid duplicate Home and favourites destinations; personal activity first |
| D16 | Collapsible sidebar rather than permanent bottom navigation | **Approved direction** | Reclaim space while adapting mobile, foldable and remote-driven TV |
| D17 | Product navigation/screen map/wireframes approved before technical implementation | **Current stage** | Work backwards from the desired experience |
| D18 | Live TV under Watch only if later approved; no placeholder tab | **Open** | Avoid premature EPG, sources and DVR scope |
| D19 | Minimalism through progressive disclosure and content density; don't hide capabilities behind clutter | **Locked principle** | Many features should not require many permanent controls |
| D20 | NAS-first shared state/secrets but native-device-fast playback/UI/offline | **Approved architectural intent; implementation TBD** | Consistent cross-device experience without WebView slowness |

Do not make V1-derived requirements (package identity, refactor gates, V1 CI) constraints on a wholly separate V2 package. Keep proven V1 use cases for future regression specifications.
