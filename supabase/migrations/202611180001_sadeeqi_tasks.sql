-- SADEEQI AI durable task storage (Supabase SQL Editor)
-- Run only after reviewing this migration. Tasks are private to the authenticated owner.

create table if not exists public.sadeeqi_tasks (
  id uuid primary key,
  owner_id uuid not null references auth.users(id) on delete cascade,
  state text not null check (state in (
    'queued', 'running', 'waiting_approval', 'paused',
    'completed', 'failed', 'cancelled'
  )),
  title text not null check (char_length(title) between 1 and 160),
  goal text not null check (char_length(goal) between 1 and 8000),
  task_data jsonb not null,
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create index if not exists sadeeqi_tasks_owner_updated_idx
  on public.sadeeqi_tasks (owner_id, updated_at desc);

alter table public.sadeeqi_tasks enable row level security;

drop policy if exists "Users can read their own Sadeeqi tasks" on public.sadeeqi_tasks;
create policy "Users can read their own Sadeeqi tasks"
  on public.sadeeqi_tasks for select
  to authenticated using (auth.uid() = owner_id);

drop policy if exists "Users can insert their own Sadeeqi tasks" on public.sadeeqi_tasks;
create policy "Users can insert their own Sadeeqi tasks"
  on public.sadeeqi_tasks for insert
  to authenticated with check (auth.uid() = owner_id);

drop policy if exists "Users can update their own Sadeeqi tasks" on public.sadeeqi_tasks;
create policy "Users can update their own Sadeeqi tasks"
  on public.sadeeqi_tasks for update
  to authenticated using (auth.uid() = owner_id)
  with check (auth.uid() = owner_id);

grant select, insert, update on public.sadeeqi_tasks to authenticated;
