# SADEEQI SECURITY & PROTECTION CENTER

Status: **FOUNDATION IN PROGRESS — NOT A SECURITY CERTIFICATION**

This document is an evidence-based implementation register. A control is not marked active until it has a deployed integration and a test proving it works.

## Confirmed in source at audit start

- Android local memory uses AES-GCM with Android Keystore-backed key material (physical-device acceptance still required).
- Android auth session storage uses AES-GCM with Android Keystore-backed key material.
- Android manifest disables Android backup (`allowBackup=false`).
- Backend provider credentials are read from server environment variables, not hardcoded in the Android Gradle configuration.
- Supabase JWT verification exists in backend source.
- Input shape and size validation exists for chat requests.
- Node backend has per-instance rate limiting (not globally coordinated across instances).
- API responses from the Worker already set `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`.

## Risks requiring remediation or owner configuration

| Area | Evidence / gap | Status |
|---|---|---|
| Authentication | `AUTH_MODE` defaults to `legacy` in server and Worker; legacy shared token remains accepted in legacy/dual mode | HIGH — migrate production to Supabase-only after old clients are retired |
| Rate limiting | Node limiter is in-memory per instance; Worker has no shared durable limiter | HIGH — use platform/WAF or shared durable store |
| Upload scanning | Chat accepts image data URLs / HTTPS image URLs; no malware scanning or quarantine pipeline was found in audited backend paths | HIGH — scanner not implemented |
| URL safety | HTTPS scheme validation is not equivalent to URL reputation checks or SSRF protection | HIGH — Safe Browsing / allow-list proxy not integrated |
| API hardening | Node API baseline headers added in this branch; deployed behavior pending CI/deployment | IN PROGRESS |
| Container privilege | Docker image now switches to the built-in unprivileged `node` user | IMPLEMENTED IN BRANCH; build pending |
| Android integrity | Play Integrity / Firebase App Check integration not found in audited source | NOT IMPLEMENTED |
| Secret scanning | CI secret scanning / push protection not yet evidenced | NOT VERIFIED |
| Dependency scanning | CI dependency vulnerability scanning not yet evidenced | NOT VERIFIED |
| SAST / DAST | Semgrep / OWASP ZAP / MobSF results not yet produced | NOT RUN |
| Backups | encrypted export exists for local memory; restore drill and server backup coverage not evidenced | NOT VERIFIED |
| Signing | update manager has signer fingerprint validation support, but fixed production fingerprint/official release pipeline not verified | NOT VERIFIED |

## Safety and privacy constraints

- Do not upload private files to external scanning providers without explicit user consent.
- Do not automatically delete or modify suspicious files. Quarantine must be reversible and deletion requires user approval.
- Do not claim Google Play Protect, Play Integrity, Firebase App Check, Safe Browsing, ZAP, MobSF, or malware scanning is active until configured and tested.
- Do not expose secrets in health endpoints, logs, CI output, APKs, or reports.
- Keep security status values limited to: `safe_for_checks_run`, `review_required`, `threat_detected`, `scan_incomplete`.

## Required release gates

1. Set production `AUTH_MODE=supabase`; verify sign-up, sign-in, refresh, expired token rejection, and old shared-token rejection.
2. Add edge/shared rate limiting and verify cross-instance behavior.
3. Add file upload service with MIME sniffing, size limits, isolated storage, malware engine integration, quarantine and explicit deletion approval.
4. Add URL reputation integration with privacy disclosure and SSRF-safe fetching (no arbitrary server-side URL fetch).
5. Add Play Integrity / App Check only after project registration, signing configuration and server-side token verification are available.
6. Run dependency audit, secret scan, SAST, API security tests, Android static analysis, emulator and physical-device tests.
7. Perform a backup restore drill and record results.
8. Pin release signing certificate fingerprint and verify update chain.
9. Publish a signed release only after all release gates pass.

## Scope note

This foundation does not claim full protection, antivirus capability, Google security certification, or continuous monitoring. External services may have quotas or paid tiers; none are enabled by this source change.
