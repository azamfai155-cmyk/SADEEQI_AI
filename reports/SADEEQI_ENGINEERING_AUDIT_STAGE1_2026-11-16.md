# SADEEQI AI — Engineering Audit (Stage 1)
**Date:** 2026-11-16  
**Repository:** `azamfai155-cmyk/SADEEQI_AI`  
**Audited integration ref:** `integration/unified-mainline-2026-11-16` (base commit `487665393df4292431b9d3bc9ad798b84f256326`)  
**Default branch:** `main` (`f507d246ef4985f4807de5452f165bd09588919e`)  
**Status:** Initial inventory and evidence-based review; not final acceptance.

## 1. Executive summary

- GitHub tree inventory before this audit: 79 file blobs + 43 directory entries on `main`; 84 file blobs + 44 directory entries on the integration ref (five additional files and one additional directory). After adding this audit report and `.gitignore`, the integration ref contains 86 file blobs.
- Integration ref is 56 commits ahead of `main`, 0 behind; the compare endpoint reports 19 changed files.
- 13 pull requests were open at audit time. PR #35 is the unification candidate; PRs #31, #33 and #34 form a nested dependency chain and should not be merged independently without reviewing overlap.
- Three Vercel projects are linked to the same GitHub repository: canonical `sadeeqi-ai-pdrk`, plus legacy `sadeeqi-ai` and `sadeeqi-ai-kh3l`.
- Production `https://sadeeqi-ai-pdrk.vercel.app/health` returned HTTP 200, version 1.5.1, provider Groq, model `openai/gpt-oss-20b`, auth mode `supabase`, and `configured: true`. This proves health/configuration only, not an authenticated live model response.
- Latest PR #35 preview deployment for commit `4876653` reached Vercel READY and its `/health` returned HTTP 200, but `configured: false` and `auth_mode: legacy`. The Vercel environment inventory shows the relevant Supabase and Groq variables scoped to production; preview Supabase variables are scoped to a different branch (`feature/supabase-auth`). No secrets were copied or modified.
- GitHub Actions runs 434/435/436/437 (Android/backend) and 62/63/64/65 (security) concluded FAILURE across commits `4876653`, `5a11ed7`, `c3f47e0`, and `4745931`. Each job endpoint returned an empty step list; the failure happened before any step-level evidence was exposed. Root cause is therefore **not established**; do not describe these as code-test failures until runner logs are available.
- CircleCI Semgrep SAST was SUCCESS on the original integration commit, then remained pending on the latest checked commit; CircleCI backend dependency audit was FAILURE on both original and latest checked commits. The job page/logs were inaccessible in this audit, so the cause remains unknown.
- The latest Vercel preview deployment for commit `4745931` reached READY; its `/health` returned HTTP 200 but still reports `configured:false` and `auth_mode:legacy`. Production `/health` remains HTTP 200 and reports `configured:true` with `auth_mode:supabase`.
- Vercel runtime error aggregation returned no runtime errors in the last seven days; the last-30-minute production error/fatal log query returned no matching logs. The Hobby plan rejected a one-hour log query because its retention window is shorter.
- Stage 1 confirmed that the canonical Vercel project had no custom firewall configuration and that the original Vercel `Map` limiter was per-instance while the active Cloudflare Worker lacked a shared limiter. This turn stages a Cloudflare SQLite Durable Object implementation and Vercel client; production activation and live verification remain pending.
- No APK artifact or successful emulator/device acceptance was verified in this audit.

## 2. Module status

Percentages below are provisional source-implementation estimates from code review, not test pass rates or a release-readiness score. Verification is reported separately; no module is declared complete.

| Module | Source implementation estimate | Verification evidence | Status |
|---|---:|---|---|
| Android / Kotlin | 75% | Gradle workflow exists; current Actions run failed; no APK artifact verified | Partial |
| Compose UI | 65% | MainActivity and feature screens exist; no UI/device acceptance run | Partial |
| Backend (Vercel) | 80% | Production health HTTP 200; authenticated chat not live-tested | Partial |
| Cloudflare Worker | 70% | Worker source and tests exist; deployment/runtime not independently verified here | Partial |
| AI Core | 65% | Production Groq configuration reports configured; no model round-trip test | Partial |
| Memory | 80% | AES-GCM/Keystore, Room, bounded recent query, recall tests in source; instrumentation not run | Partial |
| Voice | 60% | Android SpeechRecognizer + TextToSpeech + foreground service; no device test; not full-duplex Realtime | Partial |
| Security | 70% | Supabase auth path, request validation, headers, SAST success; dependency audit failed; no pentest | Partial |
| Database | 75% | Room encrypted store and SQL migration file exist; Room instrumentation and external migration execution unverified | Partial |
| APIs | 75% | Health and routes exist; health tested; authenticated chat and provider integrations not tested | Partial |
| CI/CD | 45% | Workflows and Vercel build exist; GitHub Actions runs failed; CircleCI dependency audit failed | Blocked/partial |
| APK | 25% | Workflow defines assembleDebug and artifact upload; no verified artifact | Unverified |
| Deployment | 75% | Production health 200; PR preview READY; preview provider config missing | Partial |

These figures must be revised after executable unit, integration, emulator, and real API tests. They must not be averaged as a mathematically measured overall completion percentage.

## 3. Confirmed findings

### AUD-001 — CI execution failure without diagnostic steps
- **Priority:** P1 (release gate)
- **Affected:** `.github/workflows/android.yml`, `.github/workflows/security.yml`
- **Evidence:** Actions run 43799411353 (run 434) and 43799411345 (run 62) concluded FAILURE; job-step endpoints returned empty step lists.
- **Root cause:** Unknown. Available evidence does not establish whether this is runner/billing provisioning, workflow syntax, dependency installation, or a test failure.
- **Impact:** Backend tests, Android unit tests, APK build, and security checks cannot currently be accepted as passed.
- **Next action:** Obtain raw runner logs or rerun after confirming Actions minutes/billing and inspect the first failing step.

### AUD-002 — Dependency audit is failing
- **Priority:** P1 until diagnosed
- **Affected:** CircleCI backend dependency audit; `package.json`
- **Evidence:** CircleCI status failure for backend-dependency-audit; log page unavailable. `package.json` has no committed lockfile and the security workflow generates one dynamically before `npm ci`.
- **Root cause:** Not established; missing committed lockfile is a reproducibility weakness, not proven to be the audit failure cause.
- **Next action:** Retrieve CircleCI logs; generate and commit a reviewed lockfile; rerun `npm ci`, `npm audit --omit=dev --audit-level=high`, and `npm test`.

### AUD-003 — Preview environment is not configured for the integration branch
- **Priority:** P1 for preview-based integration testing; production unaffected by this evidence
- **Affected:** Vercel project `sadeeqi-ai-pdrk`
- **Evidence:** PR preview `/health` returned HTTP 200 but `configured:false`, `auth_mode:legacy`; environment inventory shows Groq variables only in production and Supabase preview variables branch-scoped to `feature/supabase-auth`.
- **Root cause:** Preview variables do not cover the integration branch.
- **Impact:** Preview can build and start but cannot validate real AI requests or Supabase auth.
- **Next action:** Add branch-scoped preview variables through Vercel after explicit approval of secret propagation. No secret was read, copied, or changed in this audit.

### AUD-004 — Multiple Vercel projects point to the same repository
- **Priority:** P2
- **Affected:** Vercel project topology and `vercel.json`
- **Evidence:** Deployment context lists `sadeeqi-ai-pdrk`, `sadeeqi-ai`, and `sadeeqi-ai-kh3l` linked to this repository. `vercel.json` has an ignore command intended to skip the two legacy duplicates.
- **Impact:** Duplicate deployment attempts and confusing project selection; canonical project is identifiable as `sadeeqi-ai-pdrk`.
- **Next action:** Keep canonical project; verify ignore behavior on both legacy projects; do not delete projects without owner approval.

### AUD-005 — APK and Android device acceptance are not proven
- **Priority:** P1 for release readiness
- **Affected:** Android build, Room, voice, media, phone controls
- **Evidence:** Workflow defines unit tests, `assembleDebug`, conditional artifact upload, and manual emulator instrumentation. Current Actions run failed; no artifact was verified.
- **Impact:** Cannot claim installability, runtime behavior, memory migration correctness, microphone operation, or media controls on device.
- **Next action:** Restore Actions execution; run unit tests and build; run API 34 emulator instrumentation; then physical-device acceptance.

### AUD-006 — Social Hub is a readiness surface, not an integrated social platform
- **Priority:** P2
- **Affected:** Social Hub UI/API and `migrations/0001_social_hub.sql`
- **Evidence:** Existing audit and source describe provider readiness with `connected:false`; OAuth callback/token lifecycle and per-provider publishing/reading are not complete.
- **Impact:** UI must not imply social accounts are connected or actions are available.
- **Next action:** Keep readiness labels explicit; implement OAuth provider-by-provider only after app registration, scopes, callback URLs, and secure token storage are available.

### AUD-007 — Voice implementation is not full-duplex Realtime
- **Priority:** P2 (scope/expectation)
- **Affected:** `VoiceController.kt`, `VoiceForegroundService.kt`
- **Evidence:** Android SpeechRecognizer and TextToSpeech are used; no Realtime/WebSocket audio implementation was verified.
- **Impact:** Current voice is turn-based speech recognition and synthesis, dependent on Android speech services.
- **Next action:** Device-test Arabic STT/TTS, permissions, interruption and background behavior; treat full-duplex Realtime/wake word as unimplemented scope.

### AUD-008 — Legacy shared app token remains configured in production environment inventory
- **Priority:** P2 (residual credential hygiene)
- **Affected:** Vercel production environment
- **Evidence:** `SADEEQI_APP_TOKEN` exists as a sensitive production variable while production health reports `AUTH_MODE=supabase`.
- **Impact:** The variable appears unused while Supabase-only mode is active, but it remains a credential requiring lifecycle review.
- **Next action:** Confirm no deployed/older client depends on it before removal. Do not reveal or change its value as part of this audit.

### AUD-009 — Repository had no root .gitignore
- **Priority:** P2
- **Affected:** Repository hygiene and accidental secret/build-output commits
- **Evidence:** Root tree inventory contained no `.gitignore`.
- **Change made:** Added root `.gitignore` on the integration branch to exclude local environment files, signing keys, Gradle/Android outputs, Node dependencies, Vercel/Wrangler state, logs, and temporary files.
- **Verification:** Commit created; follow-up tree/commit verification and CI status still required.


### AUD-010 — Distributed rate limiting across Vercel and Cloudflare
- **Priority:** P1 (abuse/cost control)
- **Root cause:** The original Vercel limiter used a process-local `Map`; each Serverless instance maintained an independent counter. The active Worker had no rate-limit binding. Requests could therefore evade the intended budget by reaching another instance or by using the Worker entry point directly.
- **Chosen architecture:** Cloudflare Durable Objects with SQLite storage, partitioned deterministically by SHA-256 of the authenticated user ID (or trusted edge IP for legacy-token sessions). Both Vercel and the active Cloudflare Worker call the same Durable Object namespace. One SQLite `INSERT … ON CONFLICT … RETURNING` statement performs the fixed-window increment atomically. A given identity always resolves to the same globally coordinated Durable Object.
- **Why this option:** It supplies strong per-identity coordination and atomic storage rather than Cloudflare's approximate per-location Rate Limiting binding. The Workers Free plan includes 100,000 Worker requests/day; SQLite-backed Durable Objects are available on Free and include 100,000 DO requests/day, 13,000 GB-s/day, and 100,000 SQL rows written/day. Exceeding a Free limit causes further operations to fail rather than generating overage charges. The free limits are finite and can therefore cause fail-closed service unavailability if exhausted.
- **Alternatives rejected:** Vercel Hobby WAF rate limiting includes 1,000,000 allowed requests/month, but the published rate-limit price is $0.50 per additional million allowed requests and Hobby is limited to one rule using IP/JA4 counting; it does not provide the required shared authenticated-user counter. Cloudflare's simple Rate Limiting binding is approximate and independent per Cloudflare location. Supabase Free offers a 500 MB database but the current Vercel environment inventory did not include a Supabase service-role credential or a deployed atomic RPC function, so using it would require additional privileged database setup.
- **Code changes staged on `integration/unified-mainline-2026-11-16`:**
  - `backend/src/rate-limit.js`: Durable Object counter, SQLite atomic upsert, deterministic identity hashing, policy validation, and internal-secret validation.
  - `backend/src/vercel-rate-limit.js`: Vercel middleware calling the shared HTTPS Worker endpoint; 1.2-second timeout and fail-closed 503 behavior.
  - `server.js`: removes the process-local rate-limiter implementation; applies 20 requests/minute to chat and 60/minute to social-provider reads; applies authentication and shared rate limiting before parsing chat JSON; exposes a non-secret readiness boolean.
  - `backend/src/worker.js`: uses the same Durable Object for Worker chat and social-provider routes; adds a secret-protected internal endpoint for Vercel; fails closed if the binding is unavailable; exports the DO class.
  - `wrangler.jsonc`: adds the SQLite Durable Object binding and `new_sqlite_classes` migration.
  - `server.test.js`, `backend/src/worker.test.js`, `backend/src/rate-limit.test.js`: add policy, rejection, fail-closed, simulated multi-instance, concurrent-burst, and object-restart regression tests.
- **Identity controls:** Supabase `sub` is the primary key when authenticated. Vercel's documented `x-forwarded-for` value is used only for legacy mode because Vercel states that it overwrites the header with the public client IP; Cloudflare uses `cf-connecting-ip`. Arbitrary malformed IP headers fail closed. Raw identities are hashed before becoming Durable Object names. No request content or email is stored in the limiter.
- **Route coverage:** In the active Express server, `/v1/chat` and `/v1/social/providers` are the only user API routes and both now use the distributed limiter after authentication. The active Wrangler entry point is `backend/src/worker.js`; it applies the same policy to both routes. `/health` and the root status route remain unmetered health/status endpoints. Login/signup are handled by Supabase Auth rather than routes in these two backends; their auth-provider controls are outside this limiter.
- **Failure behavior:** Missing binding, missing internal secret, invalid service response, timeout, or storage failure returns 503; excess requests return 429 with Retry-After. The implementation does not fall back to a local `Map`.
- **Deployment/configuration status:** Repository code and Wrangler configuration are staged, but the Cloudflare deployment/DO migration has **not** been run. No Cloudflare secret, Vercel environment variable, Vercel Firewall rule, production deployment, or existing secret was changed. Vercel still needs `RATE_LIMIT_SERVICE_URL` (the deployed Worker's `/_internal/rate-limit` URL) and `RATE_LIMIT_SERVICE_SECRET`; the same strong random secret must be configured as a Cloudflare Worker secret. The deployment tool connection available in this session does not expose Cloudflare deployment/secret-management actions.
- **Test status:** Automated regression tests were added, including a 100-request concurrent simulated Durable Object test (20 allowed / 80 denied) and a simulated new-instance persistence test. These are test code, **not executed test results**. GitHub Actions attempts on the earlier integration commits failed before usable step-level output; the latest code has not yet passed a complete `npm test`, Wrangler local integration test, or live multi-instance test. No real request counts or latency measurements are claimed.
- **Status:** **PARTIALLY RESOLVED** — the local-counter design has been replaced in the staged code with a shared atomic Durable Object design, but deployment, secret provisioning, live Vercel↔Cloudflare path testing, and full test execution remain release blockers. Do not mark resolved until all are evidenced.

## 4. Memory and prompt-injection review

- PR #35 adds a Vercel identity/system instruction that explicitly treats the user-supplied «سياق ذاكرة سابق ذو صلة» block as untrusted historical data, not as instructions or permissions.
- `buildProviderRequest` now joins instruction sections with actual newline characters.
- `EncryptedMemory.kt` has a Room query `recent(limit)` ordered by newest rowid, validates limits to 1..500, and decrypts only the bounded result set after migration.
- `MainActivity.kt` calls `memory.recent(200)` before `MemoryRecall.select`; selected memory is attached as a quoted/contextual user-turn block.
- `MemoryRecall.kt` labels the memory block as potentially old/incomplete and says not to treat embedded text as instructions.
- Unit tests for memory ranking and conversation history exist in the integration branch; Android instrumentation tests exist but were not run.
- Remaining scale concern: migration of legacy rows and non-recall operations such as broad search/delete still scan legacy/all rows; assess with realistic large-database benchmarks before claiming large-scale performance.

## 5. Dependency map and execution order

1. Actions runner/CI access → enables reproducible backend tests, Gradle build, security audit, APK artifact.
2. Lockfile and dependency audit → supply-chain confidence; independent of Android code fixes.
3. Preview environment scope → enables Supabase and Groq integration tests on PR previews; production currently reports configured.
4. Android build → instrumentation → physical device acceptance.
5. Provider OAuth credentials/reviews → Social Hub real account connection; separate from the readiness API.
6. Vercel canonical project hygiene → deployment reliability; do not remove projects before verifying aliases/domains and ignore behavior.

Parallel-safe work: repository hygiene, documentation, CircleCI log investigation, Vercel project/variable inventory, and Android source review. Do not run concurrent writes to the same file or merge nested PRs independently.

## 6. Test ledger

| Test | Total | Passed | Failed | Not run / unavailable |
|---|---:|---:|---:|---:|
| GitHub Actions Android/backend run 434 | 2 jobs | 0 confirmed | 2 jobs failed | Step-level diagnostics unavailable |
| GitHub Actions security run 62 | 2 jobs | 0 confirmed | 2 jobs failed | Step-level diagnostics unavailable |
| CircleCI Semgrep SAST | 1 check | 1 | 0 | — |
| CircleCI backend dependency audit | 1 check | 0 | 1 | Failure logs unavailable |
| Vercel production health | 1 request | 1 | 0 | Does not test chat/model |
| PR preview health | 1 request | 1 HTTP response | 0 HTTP errors | Provider configured=false |
| Authenticated AI chat round-trip | — | — | — | Not run |
| Android unit tests / assembleDebug | — | — | — | Not verified due failed workflow |
| Room instrumentation / emulator | — | — | — | Not run |
| Physical-device acceptance | — | — | — | Not run |
| Performance / battery / network benchmark | — | — | — | Not run |

## 7. Scope limitations

This is a source/repository and cloud-status audit, not a full local execution or penetration test. The GitHub tree was enumerated, and core Android, backend, Worker, workflow, security, database, voice, memory, and deployment files were reviewed. The connected GitHub API did not provide usable failing job logs; CircleCI job logs were inaccessible; no Android emulator/device was attached. Therefore code coverage percentages, runtime defect rates, binary APK size, performance, and full end-to-end success rates are not measurable from this evidence.

## 8. Changes made during this stage

- Added `.gitignore` on `integration/unified-mainline-2026-11-16`.
- No production environment variables, secrets, signing keys, or deployments were modified.
- No pull request was merged and no branch was force-pushed.
