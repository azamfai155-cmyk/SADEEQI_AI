# SADEEQI AI security controls

## Database rollout

Migration `supabase/migrations/202611250001_sadeeqi_security_controls.sql` creates private, RLS-protected distributed rate-limit and audit tables. The RPC functions are executable only by `service_role`. Rate-limit identities are HMAC-SHA-256 digests; raw IP addresses and user IDs are not written to the rate-limit table. Audit records contain actor/resource metadata only, never message text, tokens, or request bodies.

## Required server-side secrets

Configure these as server-only Environment Variables in the Vercel project `sadeeqi-ai-pdrk` (Settings → Environment Variables; select the intended Production and Preview environments). The deployed Vercel API entrypoint is `server.js`. Never put these values in Android, browser bundles, or public client variables. The separate Cloudflare Worker build check currently reports that the Worker service `sadeeqi-ai` does not exist on the account; its deployment is not active.

- `SUPABASE_URL`: the project URL.
- `SUPABASE_SERVICE_ROLE_KEY`: the Supabase service-role secret. Server-side only; never expose it to clients.
- `RATE_LIMIT_HASH_KEY`: a randomly generated secret with at least 32 characters. Keep stable across Worker instances and deployments so limits are globally consistent.
- `SOCIAL_TOKEN_ENCRYPTION_KEY`: base64 encoding of exactly 32 cryptographically random bytes for OAuth/access/refresh tokens.
- `TASK_DATA_ENCRYPTION_KEY`: a separate base64 encoding of exactly 32 cryptographically random bytes for task titles, goals, steps and results. Task payloads are AES-256-GCM encrypted before storage; plaintext title/goal columns contain generic placeholders. Losing this key makes encrypted tasks unrecoverable.

Generate independent keys with a cryptographically secure random generator. Do not commit them, paste them into issues, or add them to Android build configuration.

## Enable enforcement

After the secrets are present in Vercel and the database migrations are applied and verified, set:

- `ENFORCE_DISTRIBUTED_RATE_LIMIT=true`
- `ENFORCE_AUDIT_LOGGING=true`

The Express/Vercel API fails closed (HTTP 503) for protected requests if an enabled control cannot reach Supabase. The Worker entrypoint has the same server-side checks for a future Cloudflare deployment. Rate limits use an atomic Postgres upsert shared across Worker instances: chat 30/minute, social provider API 30/minute, task API 60/minute, with both trusted Cloudflare client-IP and authenticated-user buckets where available. Exceeded requests receive HTTP 429 and `Retry-After`.

## Token encryption

Use `security/token-vault.js` for OAuth/access/refresh tokens and task payloads before any sensitive data is persisted. It uses AES-256-GCM with a fresh 96-bit nonce per encryption and binds ciphertext to both provider and user ID using authenticated additional data. The ciphertext envelope includes a version and algorithm identifier. Never log plaintext tokens. Key rotation must decrypt with the old key and re-encrypt with the new key in a controlled migration.

At present, the social endpoints only report provider readiness; no OAuth tokens are stored or connected. The vault utility is implemented and tested, but it is not evidence that a social account is connected. Task persistence requires `TASK_DATA_ENCRYPTION_KEY` and refuses plaintext task payloads.

## SSRF boundary

Remote image URLs are rejected. The Worker forwards only bounded, signature-checked base64 JPEG/PNG/WebP/GIF data URLs to the model provider. This deliberately avoids having a third-party model provider dereference attacker-controlled URLs, including redirect and DNS-rebinding paths.

## Audit review

Audit events are stored in `private.sadeeqi_audit_events`; no client role has schema/table access. Review them through an authenticated administrative SQL session, for example:

```sql
select created_at, actor_id, event_type, resource_type, resource_id, metadata
from private.sadeeqi_audit_events
order by created_at desc
limit 200;
```

The event log is metadata-only. Do not add prompt text, file contents, credentials, access tokens, or other secrets to `metadata`.

## Remaining deployment and dashboard hardening

- The connected Supabase/Vercel tools do not expose the Supabase `service_role` secret, so it must be copied from the Supabase project dashboard into Vercel as `SUPABASE_SERVICE_ROLE_KEY`. Do not paste it into chat or GitHub. Generate independent 32-byte keys for the encryption and HMAC variables, save secure offline backups, then add them to Vercel before enabling the enforcement flags.
- The Vercel previews are building, but Cloudflare's GitHub integration reports `This Worker does not exist on your account` for the configured `sadeeqi-ai` Worker. Either create/configure that Worker in Cloudflare or remove the stale Cloudflare preview integration if Vercel is the only intended runtime.

## Remaining dashboard hardening

- Supabase Auth's leaked-password protection is currently disabled according to the Security Advisor. Enable it in Supabase Dashboard → Authentication → Security and rerun the Security Advisor. The project-level API does not expose this setting through the available connection.
- The private file bucket and per-user object policies are prepared in `supabase/migrations/202611250002_sadeeqi_private_files.sql`, but the connected database role is not the owner of `storage.objects`, so the migration could not be applied through this connection. Apply it with a database owner/admin role before enabling uploads. The bucket does not exist yet and no files are stored.
