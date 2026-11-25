-- Distributed rate limiting, append-only audit events, and least-privilege RPCs.
create schema if not exists private;

create table if not exists private.sadeeqi_rate_limits (
  key_hash text primary key check (length(key_hash) = 64),
  window_started_at timestamptz not null,
  hits integer not null check (hits >= 1),
  updated_at timestamptz not null default now()
);

create index if not exists sadeeqi_rate_limits_updated_idx
  on private.sadeeqi_rate_limits (updated_at);

create table if not exists private.sadeeqi_audit_events (
  id uuid primary key default gen_random_uuid(),
  actor_id uuid null references auth.users(id) on delete set null,
  event_type text not null check (length(event_type) between 1 and 80),
  resource_type text not null check (length(resource_type) between 1 and 80),
  resource_id text null check (resource_id is null or length(resource_id) <= 128),
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists sadeeqi_audit_events_created_idx
  on private.sadeeqi_audit_events (created_at desc);
create index if not exists sadeeqi_audit_events_actor_idx
  on private.sadeeqi_audit_events (actor_id, created_at desc);

alter table private.sadeeqi_rate_limits enable row level security;
alter table private.sadeeqi_audit_events enable row level security;
revoke all on schema private from public, anon, authenticated;
revoke all on all tables in schema private from public, anon, authenticated;
grant usage on schema private to service_role;
grant select, insert, update, delete on private.sadeeqi_rate_limits to service_role;
grant select, insert on private.sadeeqi_audit_events to service_role;

create or replace function public.consume_sadeeqi_rate_limit(
  p_key_hash text,
  p_limit integer,
  p_window_seconds integer
)
returns table (allowed boolean, retry_after_seconds integer, current_hits integer)
language plpgsql
security definer
set search_path = pg_catalog, private
as $$
declare
  v_window timestamptz;
  v_hits integer;
begin
  if coalesce(current_setting('request.jwt.claim.role', true), '') <> 'service_role' then
    raise insufficient_privilege using message = 'service_role required';
  end if;
  if p_key_hash !~ '^[0-9a-f]{64}$' or p_limit < 1 or p_limit > 10000
     or p_window_seconds < 1 or p_window_seconds > 86400 then
    raise invalid_parameter_value using message = 'invalid rate limit arguments';
  end if;

  v_window := to_timestamp(
    floor(extract(epoch from clock_timestamp()) / p_window_seconds) * p_window_seconds
  );

  insert into private.sadeeqi_rate_limits as current_row
    (key_hash, window_started_at, hits, updated_at)
  values (p_key_hash, v_window, 1, clock_timestamp())
  on conflict (key_hash) do update
    set window_started_at = excluded.window_started_at,
        hits = case
          when current_row.window_started_at = excluded.window_started_at
            then current_row.hits + 1
          else 1
        end,
        updated_at = clock_timestamp()
  returning private.sadeeqi_rate_limits.hits into v_hits;

  return query select
    v_hits <= p_limit,
    greatest(1, ceil(extract(epoch from (v_window + make_interval(secs => p_window_seconds) - clock_timestamp())))::integer),
    v_hits;
end;
$$;

create or replace function public.write_sadeeqi_audit_event(
  p_actor_id uuid,
  p_event_type text,
  p_resource_type text,
  p_resource_id text default null,
  p_metadata jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, private
as $$
declare
  v_id uuid;
begin
  if coalesce(current_setting('request.jwt.claim.role', true), '') <> 'service_role' then
    raise insufficient_privilege using message = 'service_role required';
  end if;
  if p_event_type is null or length(p_event_type) not between 1 and 80
     or p_resource_type is null or length(p_resource_type) not between 1 and 80
     or (p_resource_id is not null and length(p_resource_id) > 128)
     or p_metadata is null or jsonb_typeof(p_metadata) <> 'object'
     or pg_column_size(p_metadata) > 4096 then
    raise invalid_parameter_value using message = 'invalid audit event';
  end if;

  insert into private.sadeeqi_audit_events
    (actor_id, event_type, resource_type, resource_id, metadata)
  values (p_actor_id, p_event_type, p_resource_type, p_resource_id, p_metadata)
  returning id into v_id;
  return v_id;
end;
$$;

revoke all on function public.consume_sadeeqi_rate_limit(text, integer, integer) from public, anon, authenticated;
revoke all on function public.write_sadeeqi_audit_event(uuid, text, text, text, jsonb) from public, anon, authenticated;
grant execute on function public.consume_sadeeqi_rate_limit(text, integer, integer) to service_role;
grant execute on function public.write_sadeeqi_audit_event(uuid, text, text, text, jsonb) to service_role;
