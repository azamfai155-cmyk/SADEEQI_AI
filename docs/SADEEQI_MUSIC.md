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

## Global music streaming — Audius integration added

- Added an Audius read-only catalogue adapter using the documented REST API.
- Added remote track search, artist/title metadata, artwork metadata and stream URL resolution.
- Added remote Media3 queue playback alongside local file playback.
- Added Arabic text search in the SADEEQI MUSIC screen.
- Added voice search: say "ابحث عن [اسم الأغنية أو الفنان]" or "شغل [اسم الأغنية أو الفنان]"; the first available streamable result is queued.
- Read-only search uses the public API mode and an app_name identifier; an Audius API key can be added later if higher rate limits are needed.
- The catalogue is an open, creator-uploaded catalogue, not a complete catalogue of mainstream commercial releases. Each track's availability and rights are governed by its creator/platform terms. Do not describe it as every song in the world.
- Network playback requires internet access. Data-saver mode is currently a preference only; adaptive bitrate selection is not implemented.

The integration uses Audius documented track search and stream endpoints. Before public release, review Audius API terms, track-level rights/availability, attribution requirements, rate limits, regional availability and background playback conditions. Do not scrape music sites, bypass DRM or bundle commercial music.

## Quran recitation playback

- Added AlQuran Cloud verse-by-verse audio streaming for a selected surah.
- Voice command examples: "شغل سورة البقرة من القرآن", "شغل سورة يس", "أوقف التلاوة", "كمل التلاوة", and the usual next/previous/volume commands.
- The Quran picker lists all 114 surahs and offers four reciter editions in the music screen; the live voice session currently defaults to Mishary Alafasy.
- Quran audio uses the same Media3 playback session, background service, notification and lock-screen controls as music.
- Requires internet access. Reciter/edition availability and CDN reachability must be validated on-device; no claim of complete offline Quran audio is made.

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
