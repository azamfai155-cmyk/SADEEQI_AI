# SADEEQI AI — device acceptance and performance protocol

This protocol is required before claiming physical-device acceptance. CI/emulator success is not a substitute.

## Current automated diagnostics foundation
`DeviceDiagnosticsSnapshot` captures app UID receive/transmit byte counters, process PSS, Java heap, battery percentage/temperature when available, and device memory. Capture a baseline and a second snapshot around each repeatable test. Values are observations; they are not automatically attributed to one feature unless other network/background activity is controlled.

## Physical device test matrix
Run on the owner's actual Android device and record model, Android version, build SHA, APK SHA-256, date, network type, battery start/end, and test duration.

1. Install, first launch, sign-in, sign-out, token refresh, and restart.
2. Text chat: 20 prompts including Arabic, long context, server errors, offline recovery; verify real provider response and server request ID without logging tokens.
3. Voice: Arabic recognition, TTS, interrupt, mute/unmute, stop, permission denial, screen lock, notification stop, call/audio-focus interruption.
4. Memory: create/read/search/update/delete, app restart, encrypted export/import, wrong backup password, legacy migration.
5. Phone allow-list: each command, user confirmation, denial/cancel, unavailable target app, repeated invocation. Never test arbitrary tap/accessibility automation without explicit permission and a threat model.
6. Music: local and Audius playback, background service, lock-screen controls, headset, audio focus, queue, network loss.
7. Updates: newer version, same version, downgrade, invalid metadata, wrong signer, corrupted APK, canceled download, installer handoff, and recovery.

## Repeatable performance protocol
For each workload, capture a baseline, run for 10 minutes, capture again, and repeat three times:
- idle screen;
- 20-turn text chat;
- 5-minute voice session;
- 10-minute music playback (local and streaming separately);
- update check and download separately.

Record battery percentage-point delta, process PSS delta, Java heap delta, app UID RX/TX byte deltas, elapsed time, and network type. Use Android Studio Profiler/Perfetto for CPU and detailed memory; Battery Historian or equivalent for battery attribution. App counters can include background traffic and are not a per-feature network trace.

## Release acceptance gates
- Authenticated live chat returns a non-empty model response using the configured Groq provider; health HTTP 200 alone is insufficient.
- Android unit tests and connected instrumentation tests pass.
- All critical physical-device scenarios pass; attach redacted logs and diagnostics.
- Release metadata contains a monotonically increasing integer versionCode and the exact SHA-256 signer certificate fingerprint. APK package and signer verification must pass before installer handoff.
- No update may downgrade or install an untrusted signer.
- Never commit credentials, user data, exported memory, or raw authorization headers.

## Known blockers not solved by a diagnostics helper
- A true animated 3D character requires a selected/created rigged model asset, animation clips, renderer/runtime, performance budget, and accessibility fallback.
- Android does not permit unrestricted control of every app/function. Expand only through documented public intents/APIs and explicit user consent; arbitrary UI automation would require a separate accessibility-service design and a clear security model.
- Physical-device tests and battery/network benchmarks must be run on the owner's hardware. CI cannot truthfully produce those measurements.
