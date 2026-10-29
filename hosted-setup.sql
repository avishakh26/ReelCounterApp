-- ReelCounter: all migrations in order, for pasting into a hosted Supabase project's SQL Editor once.
-- Generated from supabase/migrations; those files stay the source of truth.

-- ===== 20261019120000_identity_and_profiles.sql =====
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

-- ===== 20261020120000_activity_and_social_graph.sql =====
-- Phase 5: activity data model and the read side of the social graph.
--
-- Implements docs/phase-1/03-database-schema.md §1, §3–6. Friend-request writes (send, accept,
-- block, ...) are SECURITY DEFINER RPCs added in Phase 6; until then friendships and blocks can
-- only be read by their participants.
--
-- Trust model for activity:
--   * Each device uploads absolute daily totals for its own rows. Retries are idempotent (unique
--     key) and a monotonic `revision` decides conflicts: stale writes are dropped by a trigger.
--   * Clients can never write VERIFIED figures or data-portability rows; only the server can.
--   * Friends see a user's activity only if the owner's account-level AND per-platform
--     visibility allow it, the owner isn't in stealth mode, and neither has blocked the other.

-- ── Types ────────────────────────────────────────────────────────────
create type public.metric_quality as enum ('VERIFIED', 'ESTIMATED', 'DURATION_ONLY', 'UNAVAILABLE'); -- order = precedence
create type public.friendship_status as enum ('pending', 'accepted');
create type public.activity_source as enum ('usage_stats', 'data_portability', 'manual');

-- ── Reference data (read-only to clients) ────────────────────────────
create table public.platforms (
  id               text primary key,
  display_name     text not null,
  android_packages text[] not null,
  default_quality  public.metric_quality not null,
  verified_capable boolean not null default false,
  is_active        boolean not null default true,
  sort_order       smallint not null default 0
);

-- Mirrors PlatformCatalog in :core:domain.
insert into public.platforms (id, display_name, android_packages, default_quality, verified_capable, sort_order) values
  ('tiktok',    'TikTok',    '{com.zhiliaoapp.musically,com.ss.android.ugc.trill,com.zhiliaoapp.musically.go}', 'ESTIMATED',     true,  1),
  ('instagram', 'Instagram', '{com.instagram.android,com.instagram.lite}',                                        'ESTIMATED',     false, 2),
  ('youtube',   'YouTube',   '{com.google.android.youtube}',                                                      'DURATION_ONLY', true,  3),
  ('facebook',  'Facebook',  '{com.facebook.katana,com.facebook.lite}',                                           'DURATION_ONLY', false, 4);

create table public.estimation_profiles (
  platform_id      text not null references public.platforms (id),
  version          int  not null,
  short_form_share numeric(4,3) not null,
  share_low        numeric(4,3) not null,
  share_high       numeric(4,3) not null,
  seconds_per_reel numeric(5,1) not null,
  seconds_low      numeric(5,1) not null,
  seconds_high     numeric(5,1) not null,
  min_session_sec  int not null default 3,
  published_at     timestamptz not null default now(),
  primary key (platform_id, version),
  check (0 <= share_low and share_low <= short_form_share and short_form_share <= share_high and share_high <= 1),
  check (0 < seconds_low and seconds_low <= seconds_per_reel and seconds_per_reel <= seconds_high)
);

-- Version 1: UNVALIDATED placeholders, identical to the app's built-in profiles. Calibration
-- spike S2 publishes version 2.
insert into public.estimation_profiles
  (platform_id, version, short_form_share, share_low, share_high, seconds_per_reel, seconds_low, seconds_high) values
  ('tiktok',    1, 0.95, 0.85, 1.00, 21, 12, 35),
  ('instagram', 1, 0.55, 0.30, 0.80, 18, 10, 30),
  ('youtube',   1, 0.30, 0.10, 0.60, 30, 15, 50),
  ('facebook',  1, 0.20, 0.05, 0.50, 20, 10, 35);

-- ── Devices ──────────────────────────────────────────────────────────
-- A random per-install id generated by the app; no hardware identifiers.
create table public.devices (
  id           uuid primary key,
  user_id      uuid not null references public.profiles (id) on delete cascade,
  label        text check (char_length(label) <= 40),
  app_version  text check (char_length(app_version) <= 32),
  created_at   timestamptz not null default now(),
  last_sync_at timestamptz
);
create index devices_user on public.devices (user_id);

-- ── Per-platform settings ────────────────────────────────────────────
create table public.user_platform_settings (
  user_id                   uuid not null references public.profiles (id) on delete cascade,
  platform_id               text not null references public.platforms (id),
  is_tracked                boolean not null default true,
  -- Effective visibility is the stricter of this and user_privacy_settings.activity_visibility.
  visibility                public.visibility_level not null default 'friends',
  estimation_enabled        boolean,
  share_override            numeric(4,3) check (share_override between 0 and 1),
  seconds_per_reel_override numeric(5,1) check (seconds_per_reel_override > 0),
  updated_at                timestamptz not null default now(),
  primary key (user_id, platform_id)
);

create trigger user_platform_settings_set_updated_at
  before update on public.user_platform_settings
  for each row execute function private.set_updated_at();

-- ── Daily activity ───────────────────────────────────────────────────
create table public.daily_activity_summaries (
  id                   bigint generated always as identity primary key,
  user_id              uuid not null references public.profiles (id) on delete cascade,
  device_id            uuid references public.devices (id) on delete cascade, -- NULL = server-sourced (verified) row
  platform_id          text not null references public.platforms (id),
  activity_date        date not null,         -- the user's local calendar date
  tz_id                text not null check (char_length(tz_id) between 1 and 64),
  foreground_ms        bigint not null default 0 check (foreground_ms between 0 and 90000000), -- ≤ 25 h (DST day)
  session_count        int not null default 0 check (session_count between 0 and 100000),
  estimated_reels      int check (estimated_reels >= 0),
  estimated_reels_low  int check (estimated_reels_low >= 0),
  estimated_reels_high int check (estimated_reels_high >= 0),
  verified_reels       int check (verified_reels >= 0),
  quality              public.metric_quality not null,
  source               public.activity_source not null,
  estimation_version   int,
  user_adjusted        boolean not null default false,
  is_final             boolean not null default false,
  revision             bigint not null,       -- client-monotonic per device
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now(),
  constraint daily_uq unique nulls not distinct (user_id, device_id, platform_id, activity_date),
  constraint verified_has_count check (quality <> 'VERIFIED' or verified_reels is not null),
  constraint estimated_has_count check (
    quality <> 'ESTIMATED'
    or (estimated_reels is not null and estimated_reels_low is not null and estimated_reels_high is not null)),
  constraint no_count_when_not_counted check (
    quality not in ('DURATION_ONLY', 'UNAVAILABLE') or (estimated_reels is null and verified_reels is null)),
  constraint band_ordered check (
    estimated_reels is null or (estimated_reels_low <= estimated_reels and estimated_reels <= estimated_reels_high))
);
create index daily_user_date on public.daily_activity_summaries (user_id, activity_date desc);

-- Inserts: dates must be plausible. Updates: key columns are immutable and stale revisions are
-- silently dropped, so retried or out-of-order uploads can never overwrite newer figures.
create function private.daily_activity_guard() returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.activity_date < date '2020-01-01' or new.activity_date > (now() at time zone 'utc')::date + 1 then
    raise exception using errcode = '23514', message = 'activity_date out of range';
  end if;
  if tg_op = 'UPDATE' then
    if (new.user_id, new.device_id, new.platform_id, new.activity_date)
       is distinct from (old.user_id, old.device_id, old.platform_id, old.activity_date) then
      raise exception using errcode = '23514', message = 'key columns are immutable';
    end if;
    if new.revision <= old.revision then
      return null;
    end if;
    new.created_at := old.created_at;
    new.updated_at := now();
  end if;
  return new;
end;
$$;

create trigger daily_activity_guard
  before insert or update on public.daily_activity_summaries
  for each row execute function private.daily_activity_guard();

-- Per-user totals across devices. security_invoker: the caller's RLS applies, as on the table.
create view public.daily_activity_totals with (security_invoker = true) as
select user_id,
       platform_id,
       activity_date,
       sum(foreground_ms) filter (where source <> 'data_portability')       as foreground_ms,
       sum(session_count)                                                  as session_count,
       sum(estimated_reels) filter (where quality = 'ESTIMATED')           as estimated_reels,
       sum(estimated_reels_low) filter (where quality = 'ESTIMATED')       as estimated_reels_low,
       sum(estimated_reels_high) filter (where quality = 'ESTIMATED')      as estimated_reels_high,
       max(verified_reels) filter (where quality = 'VERIFIED')             as verified_reels,
       min(quality)                                                        as best_quality,
       bool_or(user_adjusted)                                              as user_adjusted
from public.daily_activity_summaries
group by user_id, platform_id, activity_date;

-- ── Social graph ─────────────────────────────────────────────────────
create table public.friendships (                -- one row per unordered pair
  id           bigint generated always as identity primary key,
  user_low     uuid not null references public.profiles (id) on delete cascade,
  user_high    uuid not null references public.profiles (id) on delete cascade,
  requested_by uuid not null,
  status       public.friendship_status not null default 'pending',
  created_at   timestamptz not null default now(),
  responded_at timestamptz,
  constraint pair_ordered      check (user_low < user_high),
  constraint requester_in_pair check (requested_by in (user_low, user_high)),
  constraint pair_unique       unique (user_low, user_high)
);
create index friendships_high on public.friendships (user_high);

create table public.user_blocks (
  blocker_id uuid not null references public.profiles (id) on delete cascade,
  blocked_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);
create index user_blocks_blocked on public.user_blocks (blocked_id);

-- ── Authorization helpers (private schema: usable in policies, not callable over the API) ──
create function private.are_friends(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.friendships f
    where f.user_low = least(a, b) and f.user_high = greatest(a, b) and f.status = 'accepted'
  );
$$;

create function private.is_blocked_between(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.user_blocks ub
    where (ub.blocker_id = a and ub.blocked_id = b) or (ub.blocker_id = b and ub.blocked_id = a)
  );
$$;

-- Profile card: public profiles, friends and pending-request counterparts; never across a block.
create function private.can_view_profile(owner uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select (select auth.uid()) is not null
     and not private.is_blocked_between(owner, (select auth.uid()))
     and (
       exists (select 1 from public.user_privacy_settings s
               where s.user_id = owner and s.profile_visibility = 'public')
       or exists (select 1 from public.friendships f
                  where f.user_low = least(owner, (select auth.uid()))
                    and f.user_high = greatest(owner, (select auth.uid())))
     );
$$;

-- Activity row: the stricter of account-level and platform-level visibility, no stealth, no block.
create function private.can_view_activity(owner uuid, platform text) returns boolean
language sql stable security definer set search_path = '' as $$
  select (select auth.uid()) is not null
     and not private.is_blocked_between(owner, (select auth.uid()))
     and exists (
       select 1
       from public.user_privacy_settings s
       left join public.user_platform_settings p on p.user_id = s.user_id and p.platform_id = platform
       where s.user_id = owner
         and not s.stealth_mode
         and (s.activity_visibility = 'public'
              or (s.activity_visibility = 'friends' and private.are_friends(owner, (select auth.uid()))))
         and (coalesce(p.visibility, 'friends') = 'public'
              or (coalesce(p.visibility, 'friends') = 'friends' and private.are_friends(owner, (select auth.uid()))))
     );
$$;

revoke execute on function
  private.are_friends(uuid, uuid), private.is_blocked_between(uuid, uuid),
  private.can_view_profile(uuid), private.can_view_activity(uuid, text),
  private.daily_activity_guard()
  from public, anon;
grant execute on function
  private.are_friends(uuid, uuid), private.is_blocked_between(uuid, uuid),
  private.can_view_profile(uuid), private.can_view_activity(uuid, text)
  to authenticated;

-- ── Row level security ───────────────────────────────────────────────
alter table public.platforms enable row level security;
alter table public.estimation_profiles enable row level security;
alter table public.devices enable row level security;
alter table public.user_platform_settings enable row level security;
alter table public.daily_activity_summaries enable row level security;
alter table public.friendships enable row level security;
alter table public.user_blocks enable row level security;

revoke all on public.platforms, public.estimation_profiles, public.devices, public.user_platform_settings,
  public.daily_activity_summaries, public.daily_activity_totals, public.friendships, public.user_blocks
  from anon;

-- Reference data: readable by any signed-in user, writable by nobody but migrations.
create policy platforms_read on public.platforms for select to authenticated using (true);
create policy estimation_profiles_read on public.estimation_profiles for select to authenticated using (true);
revoke insert, update, delete, truncate on public.platforms, public.estimation_profiles from authenticated;

-- Profiles: widen Phase 3's own-row policy to friends, pending-request counterparts and public profiles.
drop policy profiles_select_own on public.profiles;
create policy profiles_select on public.profiles for select to authenticated
  using (id = (select auth.uid()) or private.can_view_profile(id));

-- Devices: owner only. The id and owner can't change after registration.
create policy devices_select_own on public.devices for select to authenticated
  using (user_id = (select auth.uid()));
create policy devices_insert_own on public.devices for insert to authenticated
  with check (user_id = (select auth.uid()));
create policy devices_update_own on public.devices for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy devices_delete_own on public.devices for delete to authenticated
  using (user_id = (select auth.uid()));
revoke update on public.devices from authenticated;
grant update (label, app_version, last_sync_at) on public.devices to authenticated;

-- Platform settings: owner only.
create policy platform_settings_own on public.user_platform_settings for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

-- Activity: owner reads everything of theirs; others only what can_view_activity allows.
create policy activity_select on public.daily_activity_summaries for select to authenticated
  using (user_id = (select auth.uid()) or private.can_view_activity(user_id, platform_id));

create policy activity_insert on public.daily_activity_summaries for insert to authenticated
  with check (
    user_id = (select auth.uid())
    and quality <> 'VERIFIED'
    and source <> 'data_portability'
    and device_id in (select d.id from public.devices d where d.user_id = (select auth.uid()))
  );

create policy activity_update on public.daily_activity_summaries for update to authenticated
  using (user_id = (select auth.uid()) and source <> 'data_portability')
  with check (
    user_id = (select auth.uid())
    and quality <> 'VERIFIED'
    and source <> 'data_portability'
    and device_id in (select d.id from public.devices d where d.user_id = (select auth.uid()))
  );

create policy activity_delete on public.daily_activity_summaries for delete to authenticated
  using (user_id = (select auth.uid()) and source <> 'data_portability');

-- Friendships and blocks: read-only to participants; all writes go through Phase 6 RPCs.
create policy friendships_select on public.friendships for select to authenticated
  using ((select auth.uid()) in (user_low, user_high));
revoke insert, update, delete, truncate on public.friendships from authenticated;

-- The blocked person can't detect the block.
create policy blocks_select on public.user_blocks for select to authenticated
  using (blocker_id = (select auth.uid()));
revoke insert, update, delete, truncate on public.user_blocks from authenticated;

-- ===== 20261021120000_friends_search_presence.sql =====
-- Phase 6: username search, the friend-request engine, blocks and opt-in presence.
--
-- Every write to friendships and user_blocks goes through the SECURITY DEFINER RPCs below. Each
-- RPC takes the caller from auth.uid() (never from a parameter), so a user can only ever act as
-- themselves. Expected outcomes are returned as a status word, never as an error, and they are
-- worded so they don't reveal blocks: a blocked or non-existent user is just 'not_found'.

-- ── Rate limiting ────────────────────────────────────────────────────
create table private.rate_limits (
  user_id      uuid not null references auth.users (id) on delete cascade,
  action       text not null,
  window_start timestamptz not null,
  count        int not null,
  primary key (user_id, action)
);
alter table private.rate_limits enable row level security;

-- Fixed-window counter per user and action. Over the limit raises RC002; the raise rolls back
-- the increment, so the window simply expires.
create function private.consume_rate_limit(action_name text, max_calls int, per interval) returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  calls int;
begin
  insert into private.rate_limits as r (user_id, action, window_start, count)
  values ((select auth.uid()), action_name, now(), 1)
  on conflict (user_id, action) do update set
    count        = case when r.window_start < now() - per then 1 else r.count + 1 end,
    window_start = case when r.window_start < now() - per then now() else r.window_start end
  returning r.count into calls;
  if calls > max_calls then
    raise exception using errcode = 'RC002', message = 'rate_limited';
  end if;
end;
$$;

create function private.require_user() returns uuid
language plpgsql stable
set search_path = ''
as $$
declare
  me uuid := (select auth.uid());
begin
  if me is null then
    raise exception using errcode = '42501', message = 'not_authenticated';
  end if;
  return me;
end;
$$;

-- ── Search ───────────────────────────────────────────────────────────
-- Prefix search on usernames. Returns only public-card fields plus the caller's own relationship
-- with each result; never emails or settings. Excludes the caller, blocks in either direction,
-- and users who turned off "discoverable" (unless already connected).
create function public.search_profiles(prefix text, max_results int default 20)
returns table (id uuid, username text, display_name text, avatar_path text, relationship text)
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := private.require_user();
  normalized text := lower(btrim(coalesce(prefix, '')));
begin
  if char_length(normalized) < 2 then
    return;
  end if;
  perform private.consume_rate_limit('search', 60, interval '1 minute');
  return query
    select p.id, p.username, p.display_name, p.avatar_path,
           case
             when f.status = 'accepted' then 'friend'
             when f.status = 'pending' and f.requested_by = me then 'outgoing'
             when f.status = 'pending' then 'incoming'
             else 'none'
           end
    from public.profiles p
    join public.user_privacy_settings s on s.user_id = p.id
    left join public.friendships f on f.user_low = least(p.id, me) and f.user_high = greatest(p.id, me)
    where p.username like replace(replace(replace(normalized, '\', '\\'), '%', '\%'), '_', '\_') || '%'
      and p.id <> me
      and (s.discoverable or f.id is not null)
      and not private.is_blocked_between(p.id, me)
    order by (p.username = normalized) desc, p.username
    limit least(greatest(coalesce(max_results, 20), 1), 20);
end;
$$;

-- ── Friend requests ──────────────────────────────────────────────────
-- Returns 'requested', 'accepted' (they had already asked you), 'already_friends',
-- 'already_requested', 'not_accepting' or 'not_found'.
create function public.send_friend_request(target uuid) returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := private.require_user();
  existing public.friendships%rowtype;
  accepting boolean;
begin
  perform private.consume_rate_limit('friend_request', 30, interval '1 hour');
  if target is null or target = me
     or not exists (select 1 from public.profiles p where p.id = target and p.username is not null)
     or private.is_blocked_between(me, target) then
    return 'not_found';
  end if;

  select * into existing from public.friendships f
  where f.user_low = least(me, target) and f.user_high = greatest(me, target)
  for update;
  if found then
    if existing.status = 'accepted' then return 'already_friends'; end if;
    if existing.requested_by = me then return 'already_requested'; end if;
    update public.friendships set status = 'accepted', responded_at = now() where id = existing.id;
    return 'accepted';
  end if;

  select s.accept_friend_requests into accepting from public.user_privacy_settings s where s.user_id = target;
  if not coalesce(accepting, false) then
    return 'not_accepting';
  end if;

  insert into public.friendships (user_low, user_high, requested_by)
  values (least(me, target), greatest(me, target), me)
  on conflict (user_low, user_high) do nothing;
  return 'requested';
end;
$$;

-- Only the person a pending request was sent TO can answer it. Returns 'accepted', 'declined' or
-- 'not_found'. Declining deletes the request, so the sender isn't told it was declined.
create function public.respond_friend_request(requester uuid, accept boolean) returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := private.require_user();
  request_id bigint;
begin
  select f.id into request_id from public.friendships f
  where f.user_low = least(me, requester) and f.user_high = greatest(me, requester)
    and f.status = 'pending' and f.requested_by = requester and requester <> me
  for update;
  if not found then
    return 'not_found';
  end if;
  if accept then
    update public.friendships set status = 'accepted', responded_at = now() where id = request_id;
    return 'accepted';
  end if;
  delete from public.friendships where id = request_id;
  return 'declined';
end;
$$;

-- Only the sender can withdraw a pending request. Returns 'cancelled' or 'not_found'.
create function public.cancel_friend_request(target uuid) returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := private.require_user();
begin
  delete from public.friendships f
  where f.user_low = least(me, target) and f.user_high = greatest(me, target)
    and f.status = 'pending' and f.requested_by = me;
  return case when found then 'cancelled' else 'not_found' end;
end;
$$;

-- Either friend can end a friendship. Returns 'removed' or 'not_found'.
create function public.remove_friend(target uuid) returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := private.require_user();
begin
  delete from public.friendships f
  where f.user_low = least(me, target) and f.user_high = greatest(me, target) and f.status = 'accepted';
  return case when found then 'removed' else 'not_found' end;
end;
$$;

-- Blocking ends any friendship or request and hides both users from each other everywhere
-- (search, profiles, activity, presence). Returns 'blocked' or 'not_found'.
create function public.block_user(target uuid) returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := private.require_user();
begin
  if target is null or target = me or not exists (select 1 from public.profiles p where p.id = target) then
    return 'not_found';
  end if;
  insert into public.user_blocks (blocker_id, blocked_id) values (me, target) on conflict do nothing;
  delete from public.friendships f where f.user_low = least(me, target) and f.user_high = greatest(me, target);
  return 'blocked';
end;
$$;

-- Returns 'unblocked' or 'not_found'. Unblocking doesn't restore the friendship.
create function public.unblock_user(target uuid) returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := private.require_user();
begin
  delete from public.user_blocks b where b.blocker_id = me and b.blocked_id = target;
  return case when found then 'unblocked' else 'not_found' end;
end;
$$;

-- The caller's friends and requests, with each other user's public card.
-- status: 'friend', 'incoming' or 'outgoing'.
create function public.my_connections()
returns table (user_id uuid, username text, display_name text, avatar_path text, status text, since timestamptz)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  me uuid := private.require_user();
begin
  return query
    select o.id, o.username, o.display_name, o.avatar_path,
           case when f.status = 'accepted' then 'friend' when f.requested_by = me then 'outgoing' else 'incoming' end,
           coalesce(f.responded_at, f.created_at)
    from public.friendships f
    join public.profiles o on o.id = case when f.user_low = me then f.user_high else f.user_low end
    where me in (f.user_low, f.user_high)
      and not private.is_blocked_between(me, o.id)
    order by 5, o.username;
end;
$$;

-- People the caller has blocked, so they can be unblocked. Only the blocker ever sees this.
create function public.my_blocked_users()
returns table (user_id uuid, username text, display_name text, avatar_path text)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  me uuid := private.require_user();
begin
  return query
    select p.id, p.username, p.display_name, p.avatar_path
    from public.user_blocks b
    join public.profiles p on p.id = b.blocked_id
    where b.blocker_id = me
    order by p.username;
end;
$$;

revoke execute on function
  public.search_profiles(text, int), public.send_friend_request(uuid), public.respond_friend_request(uuid, boolean),
  public.cancel_friend_request(uuid), public.remove_friend(uuid), public.block_user(uuid), public.unblock_user(uuid),
  public.my_connections(), public.my_blocked_users()
  from public, anon;
grant execute on function
  public.search_profiles(text, int), public.send_friend_request(uuid), public.respond_friend_request(uuid, boolean),
  public.cancel_friend_request(uuid), public.remove_friend(uuid), public.block_user(uuid), public.unblock_user(uuid),
  public.my_connections(), public.my_blocked_users()
  to authenticated;
revoke execute on function private.consume_rate_limit(text, int, interval), private.require_user() from public, anon, authenticated;

-- ── Presence (Supabase Realtime Authorization, private channels) ─────
-- Each user shares presence on their own private channel "presence:<user id>". A friend may join
-- it only if the owner opted in (share_presence), isn't in stealth mode and there's no block.
create function private.presence_visible(owner uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select owner = (select auth.uid())
      or (
        private.are_friends(owner, (select auth.uid()))
        and not private.is_blocked_between(owner, (select auth.uid()))
        and exists (
          select 1 from public.user_privacy_settings s
          where s.user_id = owner and s.share_presence and not s.stealth_mode
        )
      );
$$;

create function private.can_join_presence(topic text) returns boolean
language sql stable security definer set search_path = '' as $$
  select coalesce(topic ~ '^presence:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', false)
     and private.presence_visible(substring(topic from 10)::uuid);
$$;

revoke execute on function private.presence_visible(uuid), private.can_join_presence(text) from public, anon;
grant execute on function private.presence_visible(uuid), private.can_join_presence(text) to authenticated;

create policy presence_join on realtime.messages for select to authenticated
  using (realtime.messages.extension = 'presence' and private.can_join_presence((select realtime.topic())));

create policy presence_track_own on realtime.messages for insert to authenticated
  with check (
    realtime.messages.extension = 'presence'
    and (select realtime.topic()) = 'presence:' || (select auth.uid())::text
  );

-- ── Live friend-request updates (Broadcast from Database) ─────────────
-- postgres_changes can't apply RLS or filters to DELETE events, so every subscriber would hear
-- about every deleted friendship in the system. Instead, this trigger tells only the two people
-- involved, on their own private channel "friendships:<user id>". The message carries no data:
-- the app refetches its lists through my_connections(), which applies all the usual rules.
create function private.notify_friendship_change() returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  low uuid;
  high uuid;
begin
  if tg_op = 'DELETE' then
    low := old.user_low;
    high := old.user_high;
  else
    low := new.user_low;
    high := new.user_high;
  end if;
  perform realtime.send('{}'::jsonb, 'changed', 'friendships:' || low::text, true);
  perform realtime.send('{}'::jsonb, 'changed', 'friendships:' || high::text, true);
  return null;
end;
$$;
revoke execute on function private.notify_friendship_change() from public, anon, authenticated;

create trigger friendships_notify
  after insert or update or delete on public.friendships
  for each row execute function private.notify_friendship_change();

-- Only the owner may receive their channel's broadcasts. There's no insert policy for it, so
-- nobody can send fake updates to someone else.
create policy friendship_updates_receive_own on realtime.messages for select to authenticated
  using (
    realtime.messages.extension = 'broadcast'
    and (select realtime.topic()) = 'friendships:' || (select auth.uid())::text
  );
