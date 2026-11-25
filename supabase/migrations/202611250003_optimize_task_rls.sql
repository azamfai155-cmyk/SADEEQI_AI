-- Optimize per-row RLS evaluation for the owner-scoped task table.
-- Wrapping auth.uid() in a scalar subquery lets Postgres evaluate it once per statement.
drop policy if exists "Users can read their own Sadeeqi tasks" on public.sadeeqi_tasks;
create policy "Users can read their own Sadeeqi tasks"
  on public.sadeeqi_tasks for select
  to authenticated using ((select auth.uid()) = owner_id);

drop policy if exists "Users can insert their own Sadeeqi tasks" on public.sadeeqi_tasks;
create policy "Users can insert their own Sadeeqi tasks"
  on public.sadeeqi_tasks for insert
  to authenticated with check ((select auth.uid()) = owner_id);

drop policy if exists "Users can update their own Sadeeqi tasks" on public.sadeeqi_tasks;
create policy "Users can update their own Sadeeqi tasks"
  on public.sadeeqi_tasks for update
  to authenticated using ((select auth.uid()) = owner_id)
  with check ((select auth.uid()) = owner_id);
