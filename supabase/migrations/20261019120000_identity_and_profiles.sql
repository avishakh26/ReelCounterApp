-- Phase 3: identity, profiles and avatars.
--
-- Implements docs/phase-1/03-database-schema.md §1–2 for the tables Phase 3 needs. Activity,
-- devices, platform settings and the social graph arrive in Phases 5 and 6.
--
-- Security model:
--   * RLS on every table; no policy = no access.
--   * Users read and edit only their own profile and settings. Phase 6 widens profile reads to
--     friends once friendships exist (private.can_view_profile).
--   * Profile rows are created by a trigger, never by clients; accounts are deleted through an
--     Edge Function (Phase 8), never by clients.
--   * Helper functions live in the "private" schema, which is not exposed through the API.

-- ── Schemas and types ────────────────────────────────────────────────
create schema if not exists private;
grant usage on schema private to authenticated;

create type public.visibility_level as enum ('public', 'friends', 'private');

-- ── Helpers ──────────────────────────────────────────────────────────
create function private.set_updated_at() returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

-- ── Profiles ─────────────────────────────────────────────────────────
create table public.profiles (
  id           uuid primary key references auth.users (id) on delete cascade,
  -- 3–20 chars of a–z, 0–9, "_" and "."; starts and ends with a letter or digit; no "..".
  -- Stored lowercase, so the unique constraint is case-insensitive in practice.
  -- NULL until the user finishes onboarding.
  username     text unique
               constraint username_format check (
                 username ~ '^[a-z0-9][a-z0-9_.]{1,18}[a-z0-9]$' and username !~ '\.\.'
               ),
  display_name text
               constraint display_name_format check (
                 char_length(display_name) between 1 and 50
                 and display_name = btrim(display_name)
                 and display_name !~ '[[:cntrl:]]'
               ),
  -- Path inside the "avatars" bucket. Must live in the owner's folder, so a profile can never
  -- point at someone else's file.
  avatar_path  text
               constraint avatar_path_owned check (avatar_path like (id::text || '/%')),
  bio          text
               constraint bio_format check (char_length(bio) <= 160),
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create index profiles_username_prefix on public.profiles (username text_pattern_ops);

create trigger profiles_set_updated_at
  before update on public.profiles
  for each row execute function private.set_updated_at();

-- Reserved names are kept server-side so the list can change without an app release.
create table private.reserved_usernames (
  name text primary key check (name = lower(name))
);

insert into private.reserved_usernames (name) values
  ('admin'), ('administrator'), ('api'), ('app'), ('help'), ('me'), ('mod'), ('moderator'),
  ('null'), ('official'), ('privacy'), ('reelcounter'), ('root'), ('security'), ('settings'),
  ('staff'), ('support'), ('system'), ('team'), ('undefined'), ('verified');

-- Rejects reserved usernames with a dedicated SQLSTATE the app maps to "That username is reserved".
create function private.reject_reserved_username() returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.username is not null
     and exists (select 1 from private.reserved_usernames r where r.name = new.username) then
    raise exception using errcode = 'RC001', message = 'username_reserved';
  end if;
  return new;
end;
$$;

create trigger profiles_reject_reserved_username
  before insert or update of username on public.profiles
  for each row execute function private.reject_reserved_username();

-- ── Privacy settings (read by Phases 5–8; created now so every user has a row) ──
create table public.user_privacy_settings (
  user_id                uuid primary key references public.profiles (id) on delete cascade,
  profile_visibility     public.visibility_level not null default 'friends',
  activity_visibility    public.visibility_level not null default 'friends',
  discoverable           boolean not null default true,
  accept_friend_requests boolean not null default true,
  share_presence         boolean not null default false,
  stealth_mode           boolean not null default false,
  updated_at             timestamptz not null default now()
);

create trigger user_privacy_settings_set_updated_at
  before update on public.user_privacy_settings
  for each row execute function private.set_updated_at();

-- ── New user → profile + default privacy settings ────────────────────
create function private.handle_new_user() returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id) values (new.id);
  insert into public.user_privacy_settings (user_id) values (new.id);
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function private.handle_new_user();

-- ── Row level security ───────────────────────────────────────────────
alter table public.profiles enable row level security;
alter table public.user_privacy_settings enable row level security;
alter table private.reserved_usernames enable row level security;

-- Profiles: own row only for now (Phase 6 adds friends and public profiles).
create policy profiles_select_own on public.profiles
  for select to authenticated
  using (id = (select auth.uid()));

create policy profiles_update_own on public.profiles
  for update to authenticated
  using (id = (select auth.uid()))
  with check (id = (select auth.uid()));

-- Clients may change only these columns; id and timestamps are off limits.
revoke insert, update, delete, truncate, references, trigger on public.profiles from anon, authenticated;
grant update (username, display_name, avatar_path, bio) on public.profiles to authenticated;

-- Privacy settings: owner reads and updates; rows are created by the trigger only.
create policy privacy_select_own on public.user_privacy_settings
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy privacy_update_own on public.user_privacy_settings
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

revoke insert, update, delete, truncate, references, trigger on public.user_privacy_settings from anon, authenticated;
grant update (profile_visibility, activity_visibility, discoverable, accept_friend_requests, share_presence, stealth_mode)
  on public.user_privacy_settings to authenticated;

revoke all on public.profiles, public.user_privacy_settings from anon;

-- ── Username availability ────────────────────────────────────────────
-- Returns 'available', 'taken', 'reserved' or 'invalid'. SECURITY DEFINER so it can see every
-- username despite RLS; it returns only a status, never another user's data. Your own current
-- username counts as available, so re-saving a profile doesn't report a conflict.
create function public.check_username(candidate text) returns text
language sql
stable
security definer
set search_path = ''
as $$
  select case
    when candidate is null
      or candidate !~ '^[a-z0-9][a-z0-9_.]{1,18}[a-z0-9]$'
      or candidate ~ '\.\.' then 'invalid'
    when exists (select 1 from private.reserved_usernames r where r.name = candidate) then 'reserved'
    when exists (
      select 1 from public.profiles p
      where p.username = candidate and p.id <> (select auth.uid())
    ) then 'taken'
    else 'available'
  end;
$$;

revoke execute on function public.check_username(text) from public, anon;
grant execute on function public.check_username(text) to authenticated;

-- Trigger and helper functions are never called directly by clients.
revoke execute on all functions in schema private from public, anon, authenticated;

-- ── Avatars (Storage) ────────────────────────────────────────────────
-- Public-read bucket (decision D6) with unguessable object names. Users write only inside a
-- folder named after their own user id. 1 MB limit; the app uploads ~512 px WebP/JPEG.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 1048576, array['image/webp', 'image/jpeg'])
on conflict (id) do nothing;

create policy avatars_select_own on storage.objects
  for select to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy avatars_insert_own on storage.objects
  for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy avatars_update_own on storage.objects
  for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy avatars_delete_own on storage.objects
  for delete to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
