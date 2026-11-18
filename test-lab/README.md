# SADEEQI AI Web Test Lab

An isolated Arabic RTL browser console for developing and testing the SADEEQI AI experience before porting stable modules to Android.

## Unified Android modules dashboard\n- Open `modules.html` from the main lab using the **وحدات التطبيق كاملة** link.\n- Consolidates the Android app areas: conversation/command classification, browser voice input/output and voice settings, phone command parser preview, encrypted-memory architecture notes, music command parser preview, SOCIAL HUB integration status, trading risk/R:R/backtest calculators, and code architecture map.\n- Includes local previews of the existing `DeviceCommandParser` and `MusicCommandParser` command patterns, plus trading calculations aligned with `TradingMath`.\n- Phone operations are deliberately preview-only in the browser. Android Keystore keys and encrypted device memory are not copied into browser storage.\n\n## Unified Android modules dashboard\n- Open `modules.html` from the main lab using the **وحدات التطبيق كاملة** link.\n- Includes local command previews, browser speech controls, phone-command parsing, memory architecture notes, music-command parsing, social integration status, and trading calculators.\n- The durable-task panel can authenticate with Supabase and call the existing Worker `/v1/tasks` API to list, create, and transition user-owned tasks.\n- The browser keeps the Supabase access token in page memory only; never enter a service-role key or provider secret. Persistent task storage requires the server-side `SUPABASE_SERVICE_ROLE_KEY` and the existing database migration.\n- Task state transitions do not execute task steps or external actions. Approval controls only record the approval decision; they do not send messages or perform trades.\n\n## Development studio\n- Open `dev-studio.html` from either dashboard to prototype new HTML/CSS/JavaScript features.\n- Preview code runs in a sandboxed iframe; JavaScript syntax checks, browser-local drafts, JSON import/export, and standalone HTML export are available.\n- Drafts remain in the current browser only. Exported code must be reviewed and committed to a development branch; this page does not write to GitHub or deploy.\n- The release path is feature branch → GitHub Actions checks → pull request → Vercel preview → reviewed merge to `main`.\n\n## Current browser modules
- Home dashboard and quick-command launcher.
- Local intent analysis and conversation UI (heuristics only; not an LLM).
- Browser speech recognition where supported by the browser, microphone permission, and installed language packs.
- Browser speech synthesis with available device voices.
- Animated placeholder avatar and local display settings (not a 3D human avatar).
- Simulated task lifecycle and cancellation.
- Server health URL checker using GET only; subject to network and CORS restrictions.
- Optional Supabase Auth sign-in/sign-up and direct authenticated chat with the existing Cloudflare Worker (`POST /v1/chat`); disabled until explicitly configured and enabled.
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
- The standalone server checker is a GET health probe. The separate optional Worker connector authenticates through Supabase and can call the configured model through the existing Worker when server credentials, CORS, and provider settings are valid.
- No provider key or privileged database key is exposed to browser code. The optional chat connector does not add durable task storage, Android access, or external tool adapters.
- The avatar is a CSS animated placeholder, not a human 3D model, facial rig, or lip-synced character.
- No real messages, calls, purchases, trades, publishing, deletion, or phone operations are performed.

## Integration roadmap
1. Add durable task API integration and contract tests.
5. Add allow-listed tool adapters in dry-run mode, then approval-gated execution.
6. Add secure user/session management, persistence, audit logs, and failure recovery.
7. Build the 3D human avatar and voice pipeline.
8. Verify Android-only capabilities on a physical device before packaging the stable modules as the Android app.

Never put provider secrets, database service-role keys, or privileged credentials in browser JavaScript or a public repository. Do not enable real-world side effects without explicit approval and server-side enforcement.

## Live backend connection
The lab now includes an optional direct connector to the existing Cloudflare Worker:
- Configure the Supabase project URL and public anon/publishable key in the browser.
- Sign in or sign up through Supabase Auth REST endpoints. The password is cleared after successful sign-in; the access token is kept in page memory only and is not written to localStorage.
- Configure the Worker base URL, run its `GET /health` check, then explicitly enable remote chat.
- Remote chat sends the recent conversation to `POST /v1/chat` with the Supabase access token as a Bearer token. It does not send provider credentials to the browser.

### Required server-side configuration
For the existing `backend/src/worker.js` implementation, configure Worker secrets/variables:
- `AUTH_MODE=supabase`
- `SUPABASE_URL` = the same Supabase project URL
- `AI_PROVIDER=groq` and `GROQ_API_KEY` as a Worker secret, with `GROQ_MODEL` set to a model supported by the account; alternatively use another provider only when deliberately configured server-side.
- `ALLOWED_ORIGIN=https://sadeeqi-web-test-lab.vercel.app` so browser preflight and API responses permit this exact site origin.

Do not switch authentication modes or alter production credentials as part of the browser test. Configure a separate test Worker and Supabase project where possible. The Supabase anon key is designed to be public; never enter a service-role key, provider key, or shared app token in the browser. Supabase sign-up may require email confirmation. CORS, Worker secrets, provider quota, and model availability can prevent a successful chat even when the UI is ready.

The browser connector is optional and inactive until configured and explicitly enabled. It uses the existing Worker contract; it does not create a new backend or grant Android/device permissions.
