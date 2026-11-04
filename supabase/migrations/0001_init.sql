-- CyberTrace schema
-- Apply with: supabase db push   (or the Management API SQL endpoint)
-- Safe to re-run: every statement is idempotent.

create extension if not exists pgcrypto;

-----------------------------------------
-- links: one row per generated tracking link
-----------------------------------------
create table if not exists public.links (
    id          uuid primary key default gen_random_uuid(),
    link_token  text unique not null,
    label       text not null,
    config      jsonb not null default '{
        "precise_location": false,
        "camera_front": false,
        "camera_back": false,
        "video": false,
        "audio": false
    }'::jsonb,
    created_at  timestamptz not null default now(),
    created_by  uuid references auth.users(id) on delete set null default auth.uid()
);

-----------------------------------------
-- visits: one row per time a link is opened
-----------------------------------------
create table if not exists public.visits (
    id               uuid primary key default gen_random_uuid(),
    link_id          uuid not null references public.links(id) on delete cascade,
    ip_address       text not null default '',
    ip_geo           jsonb not null default '{}'::jsonb,
    fingerprint      jsonb not null default '{}'::jsonb,
    user_agent       text not null default '',
    consent_shown_at timestamptz not null default now(),
    first_seen_at    timestamptz not null default now(),
    last_seen_at     timestamptz not null default now()
);

-----------------------------------------
-- captures: one row per item the target was asked for
-----------------------------------------
create table if not exists public.captures (
    id           uuid primary key default gen_random_uuid(),
    visit_id     uuid not null references public.visits(id) on delete cascade,
    link_id      uuid not null references public.links(id) on delete cascade,
    capture_type text not null check (capture_type in (
        'precise_location', 'camera_front', 'camera_back', 'video', 'audio'
    )),
    storage_path text,
    payload      jsonb not null default '{}'::jsonb,
    status       text not null check (status in ('granted', 'denied')),
    captured_at  timestamptz not null default now()
);

create index if not exists idx_visits_link_id       on public.visits(link_id);
create index if not exists idx_visits_first_seen    on public.visits(link_id, first_seen_at desc);
create index if not exists idx_captures_visit_id    on public.captures(visit_id);
create index if not exists idx_captures_link_id     on public.captures(link_id);
create index if not exists idx_captures_status      on public.captures(link_id, status);
create index if not exists idx_captures_captured_at on public.captures(captured_at);
create index if not exists idx_links_created_by     on public.links(created_by, created_at desc);

-----------------------------------------
-- Row Level Security
--   anon           : no direct table access at all (Edge Functions only)
--   authenticated  : own links + the visits/captures that belong to them
-----------------------------------------
alter table public.links    enable row level security;
alter table public.visits   enable row level security;
alter table public.captures enable row level security;

-- links ------------------------------------------------------------------
drop policy if exists "links_select_own" on public.links;
create policy "links_select_own" on public.links
    for select to authenticated
    using (created_by = auth.uid());

drop policy if exists "links_insert_own" on public.links;
create policy "links_insert_own" on public.links
    for insert to authenticated
    with check (created_by = auth.uid());

drop policy if exists "links_update_own" on public.links;
create policy "links_update_own" on public.links
    for update to authenticated
    using (created_by = auth.uid())
    with check (created_by = auth.uid());

drop policy if exists "links_delete_own" on public.links;
create policy "links_delete_own" on public.links
    for delete to authenticated
    using (created_by = auth.uid());

-- visits -----------------------------------------------------------------
drop policy if exists "visits_select_own" on public.visits;
create policy "visits_select_own" on public.visits
    for select to authenticated
    using (exists (
        select 1 from public.links l
        where l.id = visits.link_id and l.created_by = auth.uid()
    ));

-- captures ---------------------------------------------------------------
drop policy if exists "captures_select_own" on public.captures;
create policy "captures_select_own" on public.captures
    for select to authenticated
    using (exists (
        select 1 from public.links l
        where l.id = captures.link_id and l.created_by = auth.uid()
    ));

-- Table privileges. RLS still gates every row.
grant usage on schema public to anon, authenticated;
grant select, insert, update, delete on public.links    to authenticated;
grant select on public.visits   to authenticated;
grant select on public.captures to authenticated;
revoke all on public.links, public.visits, public.captures from anon;

-----------------------------------------
-- Storage: private bucket for captured media
-- Path convention: {link_id}/{visit_id}/{capture_type}/{uuid}.{ext}
-- Uploads are written by upload-capture with the service role; the dashboard
-- reads through short-lived signed URLs.
-----------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('captures', 'captures', false, 104857600, null)
on conflict (id) do update
    set public = false,
        file_size_limit = 104857600;

drop policy if exists "captures_objects_read_own" on storage.objects;
create policy "captures_objects_read_own" on storage.objects
    for select to authenticated
    using (
        bucket_id = 'captures'
        and exists (
            select 1 from public.links l
            where l.created_by = auth.uid()
              and l.id::text = (storage.foldername(storage.objects.name))[1]
        )
    );

-----------------------------------------
-- Realtime: stream new visits and captures to the dashboard
-----------------------------------------
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public' and tablename = 'visits'
    ) then
        alter publication supabase_realtime add table public.visits;
    end if;

    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public' and tablename = 'captures'
    ) then
        alter publication supabase_realtime add table public.captures;
    end if;
end $$;
