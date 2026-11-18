# SADEEQI AI — Readiness Execution Plan
**Date:** 2026-11-18  
**Working branch:** `work/readiness-reconciliation-2026-11-18`  
**Baseline:** `main` at `48da900dc3d9cda3abc439921aa5c5fd7d86640d`

## Safety boundaries
- This branch is an integration/reconciliation workspace only.
- Do not merge pull requests, deploy to Vercel/Cloudflare/production, change secrets/OAuth/account settings, or delete data/resources without explicit owner approval.
- A successful CI build or emulator run is not evidence of physical-device acceptance.
- Do not claim Yemeni/Ibbi speech quality without evaluation by native speakers on the target device.

## Initial repository findings
- `main` has successful backend tests, Android unit tests, debug APK build/upload, and API 34 emulator instrumentation in workflow run 44199003269.
- PR #42 is a draft. Backend tests, Android unit tests, debug APK build and artifact upload passed in run 44201686866; emulator instrumentation was still in progress at the last inspection.
- PR #37 is a draft and reports `dirty` mergeability against its base. It contains substantial voice-session, request-cancellation, audio-focus, realtime speech, dialect-profile and voice-studio changes. Its own description says real-device and native Ibbi-speaker acceptance remain outstanding.
- PR #35 is a large integration stack, 104 commits ahead and 17 behind the inspected `main` baseline; its mergeability was unknown. It must not be merged as-is.
- PR #33 (memory) and PR #36 (Termux bridge) are based on earlier integration branches rather than current `main`. Reconcile them as reviewed, isolated ports; do not merge the branch stack wholesale.
- Existing voice implementation on `main` uses Android SpeechRecognizer/TTS in an alternating listen/reply/speak loop. This is not always-on wake-word detection or full-duplex realtime conversation.

## Execution order and acceptance gates

### Phase 0 — Baseline and branch reconciliation
1. Keep this branch based on the inspected current `main` SHA.
2. Compare each candidate PR against this baseline and identify overlapping paths, removed files, incompatible APIs, and workflow changes.
3. Port only reviewed, minimal changes; never merge a stale branch wholesale.
4. Require a green backend test, Android unit test, debug build, and emulator instrumentation run for the exact resulting SHA.

### Phase 1 — Voice reliability
1. Reconcile PR #37 selectively: interruption must cancel the active network call, invalidate late callbacks, and avoid adding interrupted assistant turns to history.
2. Verify mute/unmute, recognizer restart, TTS callback ordering, audio focus, session stop, and foreground-service lifecycle.
3. Add deterministic tests for stale request completion, repeated interrupt, mute races, and service stop.
4. Treat `ar-YE` recognition support as provider/device-dependent; fall back to `ar` and record the actual selected language.
5. Keep Ibbi personas as presentation presets only until evaluated by native speakers; do not claim authentic accent based on TTS pitch/rate.

**Gate:** Android unit + instrumentation tests pass; emulator logs confirm session transitions; physical-device checklist completed before release claims.

### Phase 2 — Conversation history and encrypted memory
1. Reconcile PR #31/#33 memory and history changes with current `main`.
2. Preserve AES-GCM encryption and Android Keystore key isolation.
3. Ensure saves/imports are idempotent by logical ID and that duplicate records cannot produce stale reads.
4. Verify Room ordering, bounded recall, current-turn exclusion, untrusted-memory treatment, and backup import/export.
5. Run Room instrumentation tests and inspect SQLite main/WAL/SHM files for plaintext test markers.

**Gate:** unit tests and emulator Room instrumentation pass; migration/import failure never deletes the only remaining copy.

### Phase 3 — Backend and provider reliability
1. Reconcile PR #41 system-prompt newline and Worker-name corrections without changing provider secrets.
2. Verify request validation, payload limits, rate limiting, auth boundaries, and free-first provider policy.
3. Run backend regression, dependency audit, and Semgrep; verify health and authenticated chat using test credentials only.

**Gate:** CI checks pass for the exact commit; no production deployment is implied.

### Phase 4 — Safe phone control and Termux bridge
1. Reconcile PR #36 and related phone-control work against current Android permission and service contracts.
2. Keep commands allowlisted and require explicit confirmation for sensitive actions.
3. Validate authentication, replay protection, local-only binding, and stop/revoke behavior for the bridge.
4. Do not introduce unrestricted automation or background microphone activation.

**Gate:** unit/security tests pass; bridge remains disabled until explicit device-side opt-in and acceptance.

### Phase 5 — Device diagnostics, updates, and release readiness
1. Review PR #42 diagnostics and update-signature/downgrade protections.
2. Verify battery, process memory, UID network counters, and device memory are app-scoped and clearly labelled as estimates where Android APIs require it.
3. Validate update metadata, monotonic versionCode, package identity, signer SHA-256, and downgrade rejection.
4. Complete the physical-device acceptance protocol on the target phone, recording OS version, app build SHA, battery/network/memory observations, and voice test results.

**Gate:** physical-device acceptance evidence exists. Only then may the project be described as device-tested.

## Required final acceptance matrix
- Arabic speech recognition: pass/fail by device and recognition provider.
- Yemeni/Ibbi dialect recognition: native-speaker test evidence; no inference from language tags.
- Continuous listening: document whether turn-based or true streaming; verify restart and timeout behavior.
- Echo/overlap: test with speaker output and microphone active; verify AEC or document device limitations.
- Interruption: network call cancelled; stale callbacks ignored; no stale history append.
- Microphone mute: no audio capture while muted; correct foreground notification state.
- Arabic TTS: verify installed engine/voice and intelligibility.
- Conversation context: correct session history, bounded context, persistence behavior.
- Network/recognition recovery: retry/backoff and user-visible failure state.
- Background service: visible foreground notification, permission compliance, and clean stop.
- Memory: encrypted at rest, idempotent writes, safe import/export, bounded recall.
- Phone controls/Termux: allowlisted actions, explicit confirmation, revocation.
- Updates: signer verification and downgrade protection.
- Device performance: battery, memory, and network measured on the target physical device.

## Current execution status
- [x] Repository and open PR inventory inspected.
- [x] Main baseline workflow verified successful, including API 34 emulator instrumentation.
- [x] Working branch created from the inspected main SHA.
- [ ] Reconcile and port voice reliability changes.
- [ ] Reconcile memory/history changes.
- [ ] Reconcile backend/provider changes.
- [ ] Reconcile safe phone control/Termux bridge.
- [ ] Reconcile diagnostics/update hardening.
- [ ] Physical-device acceptance.

This plan is a tracking artifact, not proof that any pending phase has been implemented or accepted.
