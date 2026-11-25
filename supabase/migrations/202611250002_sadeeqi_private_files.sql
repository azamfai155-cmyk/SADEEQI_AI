-- Private per-user file bucket. Object names must begin with the authenticated user's UUID.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'sadeeqi-private-files',
  'sadeeqi-private-files',
  false,
  10485760,
  array['image/jpeg','image/png','image/webp','application/pdf','text/plain']
)
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

alter table storage.objects enable row level security;

drop policy if exists "Sadeeqi users read own private files" on storage.objects;
create policy "Sadeeqi users read own private files"
on storage.objects for select to authenticated
using (
  bucket_id = 'sadeeqi-private-files'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

drop policy if exists "Sadeeqi users upload own private files" on storage.objects;
create policy "Sadeeqi users upload own private files"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'sadeeqi-private-files'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

drop policy if exists "Sadeeqi users update own private files" on storage.objects;
create policy "Sadeeqi users update own private files"
on storage.objects for update to authenticated
using (
  bucket_id = 'sadeeqi-private-files'
  and (storage.foldername(name))[1] = (select auth.uid())::text
)
with check (
  bucket_id = 'sadeeqi-private-files'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

drop policy if exists "Sadeeqi users delete own private files" on storage.objects;
create policy "Sadeeqi users delete own private files"
on storage.objects for delete to authenticated
using (
  bucket_id = 'sadeeqi-private-files'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);
