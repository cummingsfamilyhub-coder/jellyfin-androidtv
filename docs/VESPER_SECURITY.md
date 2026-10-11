# Vesper security, data ownership and backups

Status: **risks identified in current code; mitigations not yet completed**. 2026-10-11.

## Verified source-level exposures requiring assessment
1. `MobileSettingsActivity.kt` currently reads/writes Music Assistant, Seerr, TVDb, TMDb and discovery-service credentials in ordinary `SharedPreferences("vesper", MODE_PRIVATE)`. `MODE_PRIVATE` is app-sandboxed, but not equivalent to server-held credentials or hardware-backed encrypted storage. Don't put third-party service admin tokens in a family member's client.
2. `.github/vesper-debug.keystore.b64` exists in Git history/current branch, and the build workflow reconstructs it. Whether repository exposure was public or private is not established here; treat it as a signing-key exposure risk until reviewed. **Do not simply rotate the key** — that can break Android update continuity. Plan safe relocation to protected CI secrets, audit exposure/history and only rotate with an explicit continuity strategy.
3. `vesper_mobile_network_security_config.xml` currently permits cleartext at base-config level. There is explicit HTTPS enforcement for remote Music Assistant URLs, but check all remaining client/server traffic. Restrict broader cleartext behaviour with testing rather than risk local services abruptly failing.
4. Music MyV favourites are currently stored in `vesper_myv_music_v1` on each device. These are family data needing central sync and recoverable backup.
5. Profile PINs are salted and derived in `VesperProfilePinStore.kt`; that is good but not a replacement for server-side authorisation. Audit retry throttling/session handling and cross-user permission boundaries.

## Security design
- Service secrets remain inside NAS volumes/secret store with least-privilege file permissions, never committed or returned to clients. Scope APIs to functions a specific user may use.
- Device gets a scoped, revocable session credential protected by Android Keystore; use HTTPS and short lifetime/refresh flows. Offline media/cache naturally remains on-device and needs appropriate isolation.
- Child/guest profile access must be enforced on the NAS, not hidden by UI. Be careful about inherited owner-level Seerr, Music Assistant and NAS credentials.
- Secure gateway behind Caddy, with authentication, rate limiting, request caps, TLS, redacted logs, and no exposed Docker socket/admin endpoints.
- Distinguish local-LAN service HTTP from public remote traffic. No raw media admin ports forwarded.
- Maintain pinned installation package identity and signature during migration; CI verifies the fingerprint and package. Keep original known-good APK for rollback.
- Never print secrets into logs, documentation or chat. Back up configs without exposing them through Project attachments.

## Critical-data backup policy
Media files intentionally stored on replaceable individual JBOD drives do not need the same backup policy as **Vesper DB, music favourites, family settings, gateway secrets, Jellyfin user/config data, and irreplaceable photos/docs**. Back up the latter separately, encrypt secret-bearing backups, schedule them automatically, keep at least one copy off the NAS/affected drive and test restores before migrating. A filesystem snapshot on the same disk is not disaster recovery.

## Phase 0 risk checklist
- [ ] Determine GitHub repository exposure/permissions and the debug key's history; preserve app continuity.
- [ ] Inventory actual device-held secrets, network permissions and Android backups.
- [ ] List user roles and NAS/API privileges; design family guest access.
- [ ] Decide gateway auth/session/refresh/revocation protocol.
- [ ] Verify backups and restore of Vesper/Jellyfin critical configuration.
- [ ] Produce rollback steps for every migration.
- [ ] Obtain and record actual Wi-Fi/5G baseline; reject slower migration without investigation.

No claims of resolved security risk without test evidence and committed/deployed changes.
