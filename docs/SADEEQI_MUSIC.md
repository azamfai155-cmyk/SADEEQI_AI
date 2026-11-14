# SADEEQI MUSIC — implementation status

Updated: 2026-11-14

## Implemented in source

- AndroidX Media3 ExoPlayer + MediaSession dependencies.
- A dedicated `MediaSessionService` with a Media3 player, intended to keep user-selected playback alive outside the activity and expose media-session controls to Android system UI.
- A MediaController bridge for play/pause, next/previous, seek, repeat off/all/one, shuffle and volume.
- A Compose music screen using the existing RTL/dark/gold visual language.
- User-selected audio files through Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT` contract), with persistable read permission where the provider supports it. The app does not copy the audio into its own storage.
- A local queue, favorites and listening history stored in Preferences DataStore. These are references and metadata, not copied audio files.
- Arabic voice-command parsing for pause/resume, next/previous, volume up/down and music-search requests. Search phrases are detected but are not falsely reported as successful catalogue searches.
- Unit tests for the Arabic command parser, included in the Android CI test task.

## Not implemented / requires provider integration

- No global catalogue or remote music search is connected yet.
- No licensed streaming provider, provider credentials, OAuth flow, playback authorization or country-availability checks are configured.
- No online playlists or mood-based recommendation engine is connected.
- Data-saver quality selection, adaptive streaming, cover-art caching and remote catalogue pagination are not active because there is no streaming source yet.
- The current voice recognizer is Android's existing speech service; offline recognition depends on the device's installed language pack and provider.

Do not scrape music sites, bypass DRM, download protected streams or bundle commercial music. Before adding a provider, review its current API terms, playback rules, regional availability, attribution requirements, and whether background playback is allowed. Implement one provider adapter at a time and test it against the provider's sandbox/test catalogue.

## Data and storage estimates

The local-file player itself adds **0 streaming data** when playing a file already on the device. Selecting files uses document-provider URIs and does not intentionally duplicate audio bytes inside the app. Metadata preferences are small and bounded (history retains up to 50 entries).

For a future streaming service, payload estimates from nominal encoded bitrate (excluding protocol overhead, retransmissions and adaptive-bitrate variation):

| Nominal audio bitrate | Approx. MB/min | Approx. MB/hour |
|---:|---:|---:|
| 64 kbit/s | 0.48 | 28.8 |
| 96 kbit/s | 0.72 | 43.2 |
| 128 kbit/s | 0.96 | 57.6 |
| 256 kbit/s | 1.92 | 115.2 |

Formula: bitrate in kbit/s × seconds ÷ 8 ÷ 1000. These are estimates, not measurements from a connected provider.

## Build and device validation

Source-level checks and CI compilation/unit tests are required before treating this module as buildable. Real-device validation is still required for:
- playback on supported Android versions and codecs;
- foreground-service notification and lock-screen controls;
- playback while app is backgrounded and after activity recreation;
- audio focus, Bluetooth/headset controls, phone-call interruptions and noisy-audio handling;
- URI permission persistence across reboot and document-provider edge cases;
- behavior under network loss (local playback should remain available);
- battery, memory and CPU profiling on a mid-range device.

## APK size / memory report

No verified before/after APK artifact measurement or physical-device memory profile is available yet. Do not invent these values. Record the actual APK byte size from CI artifacts and profile memory with Android Studio/Perfetto on the target device after a successful build. The Media3 libraries add binary size; the exact delta must be measured from comparable builds with the same build type, minification and ABI configuration.
