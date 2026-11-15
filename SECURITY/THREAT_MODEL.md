# SADEEQI AI — Initial Threat Model

## Assets
User access/refresh tokens, chat history, encrypted memory, selected images/files, provider API keys, social integration credentials, APK signing key, and backend availability.

## Trust boundaries
Android app ↔ public API edge ↔ backend ↔ AI provider; Android app ↔ local Keystore/Room; user-selected file ↔ upload/parser/scanner; backend ↔ Supabase JWKS and optional external reputation services.

## Primary threats
- Shared-token replay and credential extraction from legacy clients.
- Credential leakage through logs, crash reports, APK resources, or CI artifacts.
- Cross-user access caused by missing ownership checks in future data endpoints.
- Request floods against per-instance rate limits.
- Malicious files, polyglot files, oversized payloads and parser exploits.
- SSRF through arbitrary HTTPS URL fetching.
- Prompt injection embedded in user-provided documents and links.
- Supply-chain compromise through vulnerable or compromised dependencies.
- APK tampering / downgrade / unsigned or incorrectly signed updates.
- Accidental exposure of user data to third-party scanning or AI services.

## Required mitigations
Use per-user short-lived authentication, object-level authorization, shared edge rate limiting, strict content and size validation, isolated scanning, explicit consent for external processing, no arbitrary server-side URL fetch, prompt/tool isolation, dependency and secret scanning, pinned release signing identity, encrypted backups and restore drills.

## Residual risk
This threat model is preliminary and must be updated after endpoint inventory, database schema review, cloud configuration review and a real penetration test.
