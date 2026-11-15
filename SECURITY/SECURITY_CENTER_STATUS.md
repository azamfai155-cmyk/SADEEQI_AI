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
| Upload scanning | Shared validation now enforces bounded JPEG/PNG/WebP/GIF data URLs, checks the decoded file signature against the declared MIME type, and rejects obvious local/private IP literals, localhost, non-HTTPS URLs, credentials and nonstandard ports. This does not detect malware, isolate files, or quarantine them. Hostname DNS resolution is not checked, so this is not complete SSRF protection. | HIGH — format/signature guard implemented; malware scanning and quarantine not implemented |
| URL safety | Basic image-reference guard blocks obvious unsafe URL forms, but does not resolve DNS, follow redirects safely, assess reputation or guarantee destination IP safety. | HIGH — Safe Browsing / hardened fetch proxy not integrated |
| API hardening | Node API baseline headers added in this branch; deployed behavior pending CI/deployment | IN PROGRESS |
| Container privilege | Docker image now switches to the built-in unprivileged `node` user | IMPLEMENTED IN BRANCH; build pending |
| Android integrity | Play Integrity / Firebase App Check integration not found in audited source | NOT IMPLEMENTED |
| Secret scanning | CI secret scanning / push protection not yet evidenced | NOT VERIFIED |
| Dependency scanning | CI dependency vulnerability scanning not yet evidenced | NOT VERIFIED |
| SAST / DAST | Semgrep ran 113 rules across 72 tracked files and reported 20 findings; report artifact uploaded for triage. Findings have not yet been classified or remediated. OWASP ZAP / MobSF not run. | REVIEW REQUIRED — Semgrep findings pending triage |
| Backups | encrypted export exists for local memory; restore drill and server backup coverage not evidenced | NOT VERIFIED |
| Signing | update manager has signer fingerprint validation support, but fixed production fingerprint/official release pipeline not verified | NOT VERIFIED |

## Safety and privacy constraints

- Do not upload private files to external scanning providers without explicit user consent.
- Do not automatically delete or modify suspicious files. Quarantine must be reversible and deletion requires user approval.
- Do not claim Google Play Protect, Play Integrity, Firebase App Check, Safe Browsing, ZAP, MobSF, or malware scanning is active until configured and tested.
- Do not expose secrets in health endpoints, logs, CI output, APKs, or reports.
- Keep security status values limited to: `safe_for_checks_run`, `review_required`, `threat_detected`, `scan_incomplete`.

## Latest CI evidence (2026-11-15)

- Security workflow run `43713538579`: backend dependency audit and backend tests passed; Semgrep completed and uploaded artifact `14161203673` with 20 findings; Android unit/build job was still running at the last check.
- This workflow currently treats Semgrep findings as an uploaded report rather than a blocking gate. A green job means the scan ran, not that the findings are clean.
- Newer security workflow run `43720955346`: dependency audit, backend tests, Android unit/build, and API 34 emulator instrumentation all passed. Its Semgrep artifact `14164566872` contains 2 warnings, both from exported launcher activity declarations: one stale root-level manifest (removed in the follow-up commit) and the active `MainActivity` launcher declaration. The remaining export is required for the Android launcher and has no external deep-link intent filter; this is a reviewed expected warning, not a claim that all security risks are resolved.

## Required release gates

1. Set production `AUTH_MODE=supabase`; verify sign-up, sign-in, refresh, expired token rejection, and old shared-token rejection.
2. Add edge/shared rate limiting and verify cross-instance behavior.
3. Add file upload service with byte-level MIME sniffing, isolated storage, malware engine integration, quarantine and explicit deletion approval. Current validation includes basic byte-signature checks, but is not antivirus and does not quarantine files.
4. Add URL reputation integration with privacy disclosure and SSRF-safe fetching (no arbitrary server-side URL fetch).
5. Add Play Integrity / App Check only after project registration, signing configuration and server-side token verification are available.
6. Run dependency audit, secret scan, SAST, API security tests, Android static analysis, emulator and physical-device tests.
7. Perform a backup restore drill and record results.
8. Pin release signing certificate fingerprint and verify update chain.
9. Publish a signed release only after all release gates pass.

## Scope note

This foundation does not claim full protection, antivirus capability, Google security certification, or continuous monitoring. External services may have quotas or paid tiers; none are enabled by this source change.
