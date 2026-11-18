# SADEEQI AI Web Test Lab

An isolated Arabic RTL browser console for developing and testing the SADEEQI AI experience before porting stable modules to Android.

## Current browser modules
- Home dashboard and quick-command launcher.
- Local intent analysis and conversation UI (heuristics only; not an LLM).
- Browser speech recognition where supported by the browser, microphone permission, and installed language packs.
- Browser speech synthesis with available device voices.
- Animated placeholder avatar and local display settings (not a 3D human avatar).
- Simulated task lifecycle and cancellation.
- Server health URL checker using GET only; subject to network and CORS restrictions.
- Tool-category launchers for research, files, vision, reminders, maps, coding, trading, media, Android, and social integrations. These are test placeholders, not connected adapters.
- Approval gates for messages, deletion, purchases, trading, publishing, and calls.
- 15 local regression checks and downloadable JSON report.
- Downloadable event log.

## Run locally
Open `index.html` in a modern browser, or serve this directory with a static web server. For microphone access, use HTTPS or localhost and grant permission.

## Test workflow
1. Open each module from the left navigation.
2. Test typed commands and quick-command buttons.
3. Test speech recognition and speech synthesis on a supported browser/device.
4. Add a public health endpoint URL and run the GET-only connectivity check.
5. Create, run, and cancel sample tasks.
6. Exercise all approval gates.
7. Run the regression suite and export its JSON report.

## Important limitations
- Conversation classification is local heuristic logic, not a real model response.
- Voice uses browser-native Web Speech APIs; availability and Arabic voice quality vary by browser/device.
- Server check is a basic GET health probe only. It does not authenticate, call the model, or connect a database.
- No backend API, persistent database, provider key, Android bridge, or external tool adapter is currently wired into this static lab.
- The avatar is a CSS animated placeholder, not a human 3D model, facial rig, or lip-synced character.
- No real messages, calls, purchases, trades, publishing, deletion, or phone operations are performed.

## Integration roadmap
1. Inspect the repository's actual backend routes and document the API contract.
2. Add a same-origin server-side proxy with server-only secrets and explicit authentication.
3. Connect real conversation streaming and backend health diagnostics.
4. Add durable task API integration and contract tests.
5. Add allow-listed tool adapters in dry-run mode, then approval-gated execution.
6. Add secure user/session management, persistence, audit logs, and failure recovery.
7. Build the 3D human avatar and voice pipeline.
8. Verify Android-only capabilities on a physical device before packaging the stable modules as the Android app.

Never put provider secrets, database service-role keys, or privileged credentials in browser JavaScript or a public repository. Do not enable real-world side effects without explicit approval and server-side enforcement.
