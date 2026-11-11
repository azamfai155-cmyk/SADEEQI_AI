# SADEEQI AI Changelog

## 1.2.0 — 2026-11-11
- Added Android gateway client for the SADEEQI backend using HTTPS and OkHttp.
- Replaced the local canned response engine in the chat send path with remote Responses API calls.
- Added Node.js backend proxy; OpenAI key remains server-side and is read from environment.
- Added configurable model/system persona and optional Responses API web_search tool.
- Added backend health endpoint, bearer-token gate, input validation, and payload limits.
- Kept the 1.1.0 project as a separate pre-change backup in the workspace; no prior release artifact was overwritten.

## Known gaps
- API image content is accepted by the backend/client protocol, but image picker and image upload UI are not yet wired into Android.
- Voice uses Android SpeechRecognizer/TTS; this is not OpenAI Realtime voice and is not wake-word listening.
- App bearer token embedded at build time is only a bootstrap gate, not robust user authentication; production should issue short-lived per-user tokens.
- No server deployment, API key provisioning, GitHub remote creation, APK build, or device test has been performed.
