# SADEEQI AI — Web Test Lab

Arabic RTL browser workspace for prototyping, testing, and reviewing SADEEQI AI modules before porting stable behavior to Android.

## Entry points
- `index.html`: main test console — chat UI, optional authenticated Worker chat, browser voice, simulated tasks, server health, approvals, event log, and local regression tests.
- `modules.html`: consolidated Android feature dashboard — command parsing, voice settings, phone command previews, encrypted-memory architecture, music parser, SOCIAL HUB status, trading calculators, durable task API, and code architecture.
- `dev-studio.html`: isolated feature workbench — HTML/CSS/JavaScript editor, sandboxed live preview, syntax checks, local drafts, JSON import/export, and standalone HTML export.

## Development and release workflow
1. Prototype a browser feature in `dev-studio.html`.
2. Save the draft in this browser or export the project JSON/HTML.
3. Commit reviewed source files to a feature branch in GitHub.
4. Run web validation, backend tests, security scans, Android build, and emulator tests.
5. Review the pull request and Vercel Preview.
6. Merge only approved, tested changes into `main`, then release the production deployment.

The Dev Studio does not write to GitHub or deploy automatically. This separation prevents an unreviewed browser experiment from reaching the main branch.

## Security and platform boundaries
- Never put provider API keys, Supabase service-role keys, Android Keystore material, passwords, or privileged credentials in browser code.
- The optional chat connector uses Supabase Auth and the existing Worker API; provider credentials remain server-side.
- Durable task API actions update task state and approval decisions only; they do not execute arbitrary task steps or external side effects.
- Message sending, deletion, purchases, trading, publishing, and calls remain approval-gated and are not executed by these static pages.
- Phone controls and encrypted device memory remain Android capabilities. The browser only previews command parsing and architecture.
- The avatar shown in the main test lab is a placeholder, not the requested production 3D human character or lip-synced avatar.
- Browser speech recognition/synthesis depends on browser, operating system, microphone permission, and installed Arabic voices.

## Current test workflow
1. Open each dashboard and test the command examples.
2. Test microphone and speech synthesis on HTTPS in a supported browser.
3. Configure only a public Supabase anon/publishable key and test account if the optional Worker connector is used.
4. Check the Worker health endpoint and test remote chat only after authentication and explicit enablement.
5. Create and transition test tasks; confirm that approvals do not trigger real-world actions.
6. Run local browser regression checks and export their JSON reports.
7. Run the repository CI and verify the Android emulator/device tests separately.

## Local development
Serve this directory over localhost or HTTPS. Microphone permissions and some browser APIs may not work when opening files directly using `file://`.

## Important limitations
- Local intent parsing is heuristic logic, not an LLM response.
- A green browser test does not prove backend, provider, Android, or physical-device readiness.
- The sandboxed preview is for web UI prototypes. Android/Kotlin code must be built and tested in the Android project and emulator.
- Drafts are saved in the current browser only. Exported code must be reviewed and committed through the normal Git workflow.
