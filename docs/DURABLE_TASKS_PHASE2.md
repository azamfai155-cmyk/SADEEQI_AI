# Durable task manager — Phase 2

This branch adds a Supabase-backed task persistence API, scoped to the authenticated Supabase user ID. The service-role key is server-only and is never returned to the Android client. Legacy shared-token authentication is intentionally insufficient for task access because it has no verified per-user identity.

## Setup required
1. Review and run `supabase/migrations/202611180001_sadeeqi_tasks.sql` in the Supabase SQL editor.
2. Configure `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` as backend secrets. Never place the service-role key in the Android app or commit it.
3. Set `AUTH_MODE=supabase` (or `dual` while migrating; task endpoints still require a verified Supabase JWT).

## Endpoints
- `GET /v1/tasks` — list the signed-in user's most recently updated 100 tasks.
- `POST /v1/tasks` — create a queued task with optional named steps.
- `PATCH /v1/tasks/{uuid}` — transition state or request/resolve an approval.

Sensitive actions remain approval-gated. This API stores task state but does not itself execute long-running work or invoke external tools. Background execution, durable scheduling, optimistic concurrency and Android UI remain subsequent phases. No paid provider is enabled.
