# AUD-010 — Distributed Rate Limiting

## Status

**PARTIALLY RESOLVED in source; not production-active.** The Vercel and Cloudflare code paths now target the same SQLite-backed Cloudflare Durable Object namespace. Do not close AUD-010 until the Worker migration is deployed, secrets are configured on both platforms, automated tests pass, and live cross-instance tests pass.

## Architecture

- Identity key: authenticated Supabase `sub`; otherwise the platform-provided client IP (`x-forwarded-for` overwritten by Vercel, or Cloudflare `cf-connecting-ip`).
- The identity is SHA-256 hashed before use as the Durable Object name.
- A single SQLite UPSERT performs the fixed-window counter increment atomically.
- Scope counters are independent: `chat` = 20 requests/60 seconds; `social-read` = 60 requests/60 seconds.
- Both Vercel and Cloudflare Worker routes use the same Durable Object namespace and identity scheme.
- Missing binding, missing internal secret, timeout, invalid response, or storage error fails closed with HTTP 503. Over-limit requests return HTTP 429 and `Retry-After`.
- The internal Vercel-to-Worker endpoint is `POST /_internal/rate-limit` and requires a bearer secret of at least 32 characters.

## Deploy the Worker

Run from the repository root with a Cloudflare account on the Workers Free plan:

```bash
npm install --global wrangler@4.36.0
npx wrangler whoami
npx wrangler deploy --config wrangler.jsonc
```

The first deployment applies migration tag `v1` and creates the SQLite-backed `RateLimitDurableObject` namespace. Record the Worker URL Wrangler returns; do not guess the account's `workers.dev` subdomain.

Generate one random 32-byte secret locally (do not commit it or paste it into source control):

```bash
node -e "console.log(require('node:crypto').randomBytes(32).toString('base64url'))"
```

Set that same secret in Cloudflare:

```bash
npx wrangler secret put RATE_LIMIT_SERVICE_SECRET --config wrangler.jsonc
```

Then add these encrypted Vercel environment variables to the canonical project `sadeeqi-ai-pdrk`:

- `RATE_LIMIT_SERVICE_URL` = the deployed Worker URL followed by `/_internal/rate-limit`
- `RATE_LIMIT_SERVICE_SECRET` = the exact same generated secret

Apply to Production and the integration Preview branch only after the Worker deployment and secret are ready. Never add the secret to a `NEXT_PUBLIC_*` variable, mobile app, repository file, build argument, or logs. Redeploy Vercel after adding the variables.

## Acceptance checks

1. Worker `GET /health` reports `rate_limiter_configured: true` and `rate_limit_service_auth_configured: true`.
2. Vercel `GET /health` reports `rate_limiter_configured: true`.
3. Authenticated chat requests through two different Vercel deployment instances share the same user counter.
4. A direct Worker chat request with the same Supabase user ID consumes the same counter as the Vercel route.
5. For a fresh test identity, the 21st chat request within the fixed 60-second window receives 429 after 20 permitted requests; social-provider reads use their separate 60/minute budget.
6. Restarting or creating a new Worker instance does not reset the count.
7. A burst of 100 concurrent requests for a fresh identity produces exactly 20 allowed and 80 rejected decisions.
8. Missing/invalid internal secret, unavailable Durable Object binding, timeout, and simulated storage failure return 503; no local-memory fallback is permitted.
9. Normal requests below the limit continue to the handler.
10. Measure p50/p95 overhead from real Vercel and Worker deployments and record the actual response counts and timestamps.

## Free-tier ceiling

Cloudflare Workers Free currently includes 100,000 Worker requests/day and 10 ms CPU time per request. SQLite Durable Objects are available on Free; their Free limits include 100,000 DO requests/day, 13,000 GB-s/day, and 100,000 SQL rows written/day. Free-limit exhaustion causes requests/operations to fail; it must not be treated as unlimited capacity. Monitor usage and preserve fail-closed behavior.

Vercel WAF rate limiting was not enabled: although Hobby includes 1,000,000 allowed rate-limit requests/month, published pricing lists $0.50 per additional million allowed requests, and Hobby provides only one IP/JA4-keyed fixed-window rule. The Cloudflare simple Rate Limiting binding was not used as the global counter because its counts are approximate and per Cloudflare location.

## Current blockers

- Cloudflare deployment and the Durable Object migration have not been executed from this session.
- Cloudflare secret `RATE_LIMIT_SERVICE_SECRET` has not been set.
- Vercel `RATE_LIMIT_SERVICE_URL` and `RATE_LIMIT_SERVICE_SECRET` have not been set.
- Vercel deployments are currently rate-limited by the Hobby deployment cap; no production redeploy is possible until that platform restriction clears.
- GitHub Actions/CircleCI have not produced a successful execution of the new test suite. Added tests are not evidence of passing tests.
- No production cross-instance, restart, outage, or latency test has been run.

Until all blockers are cleared and evidence is attached, record **AUD-010: PARTIALLY RESOLVED**.
