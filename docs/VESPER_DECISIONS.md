# Vesper architectural decision register

Updated 2026-10-11. Decisions are not automatically deployed capabilities.

| ID | Decision | Status | Rationale / consequence |
|---|---|---|---|
| D01 | Keep the existing `jellyfin-androidtv` Vesper fork and one mobile/TV codebase | Locked | Preserve work, avoid a fresh app or parallel fork |
| D02 | Active development on `vesper-mobile-vnext`; `VESPER_STATUS.md` is the handover anchor | Locked for now | Reliable continuity across chat limits; verify branch head each time |
| D03 | Native Android UI, playback and offline storage; NAS-first shared data/logic | Approved target | Do not trade responsiveness for a purely hosted/WebView app |
| D04 | Store shared profile/settings, service API credentials and MyV music on NAS | Approved target, migration pending | User-owned central state, consistent across devices |
| D05 | No NAS SSD purchase until real performance measurements justify it | Locked | Don't solve unmeasured latency with hardware |
| D06 | Preserve signed APK update continuity; no uninstall to update | Locked | Avoid lost accounts/configuration |
| D07 | Keep Jellyfin streams direct where practical, not re-proxied through gateway | Approved target | Avoid unnecessary bandwidth and transcoding overhead |
| D08 | Treat critical gateway/identity settings as irreplaceable; off-NAS recovery required | Locked | JBOD disks are not redundancy/backup |
| D09 | Do not reintroduce Chromecast casually | Locked | Previously removed after unreliable behaviour |
| D10 | No ad-supported services or recommendations | Locked | User preference |
| D11 | Freeze unrelated UI polish and feature expansion until NAS foundations are reliable | Current priority | Resist scope creep and finish the migration |
| D12 | First offline feature is downloadable Jellyfin films and episodes | Planned, not implemented | Clear measurable airplane-mode acceptance test |

## Questions Phase 0 must decide, not guess
- Gateway implementation/runtime and database after inspecting NAS workload and recovery requirements.
- Authentication architecture and token lifetimes, owner-vs-guest access, compromised device revocation.
- Credential rotation/remediation strategy for committed debug signing material while preserving installed-app continuity.
- Exact migration rules for music favourites and local Vesper preferences (import, reconcile, rollback).
- Actual disk targets/backup destinations for the gateway on current NAS volume(s).

## Change control
A change to any locked decision needs the user's explicit agreement and a dated rationale. The status document should point to the updated decision; don't silently reinterpret old choices.
