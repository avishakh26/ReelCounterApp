-- ReelCounter: database updates for the hosted Supabase project.
--
-- Paste this whole file into Supabase → SQL Editor → New query, and click Run, ONCE.
-- It adds what the latest Android (and iPhone) app needs on top of hosted-setup.sql:
--   1. Friends' figures with live updates, and friend suggestions
--   2. The "counted reels" column for reels counted one by one
--   3. Account deletion from inside the app
--   4. Live updates when a friend changes who can see their figures
-- Generated from supabase/migrations/ (the files listed below); edit those, not this.


-- ═══════════════ 20261029120000_friend_activity_and_suggestions.sql ═══════════════

-- Friends' activity in the app, live updates of it, and friend suggestions.
--
-- Reading friends' figures needs nothing new: public.daily_activity_totals (security_invoker)
-- already applies private.can_view_activity, so a friend sees exactly what the owner's
-- "Who can see my activity" setting allows, and nothing across a block or in stealth mode.

-- ── Live activity updates (Broadcast from Database) ─────────────────
-- When someone's daily figures change, each accepted friend gets an "activity" message on their
-- own private channel "friendships:<friend id>", which only they may receive (Phase 6 policy
-- friendship_updates_receive_own). The message says only WHOSE figures changed; the app then
-- refetches them through daily_activity_totals, so RLS still decides what it sees.
-- Statement-level, so one upload of many rows sends one message per friend, not one per row.
create function private.notify_activity_change() returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  pair record;
begin
  for pair in
    select distinct c.user_id as owner,
           case when f.user_low = c.user_id then f.user_high else f.user_low end as friend
    from changed_rows c
    join public.user_privacy_settings s on s.user_id = c.user_id
    join public.friendships f
      on (f.user_low = c.user_id or f.user_high = c.user_id) and f.status = 'accepted'
    -- Nothing to announce when the owner hides their activity from friends.
    where not s.stealth_mode and s.activity_visibility <> 'private'
  loop
    perform realtime.send(jsonb_build_object('user_id', pair.owner), 'activity', 'friendships:' || pair.friend::text, true);
  end loop;
  return null;
end;
$$;
revoke execute on function private.notify_activity_change() from public, anon, authenticated;

-- Transition tables allow one event per trigger, so inserts and updates each get one.
create trigger daily_activity_notify_insert
  after insert on public.daily_activity_summaries
  referencing new table as changed_rows
  for each statement execute function private.notify_activity_change();

create trigger daily_activity_notify_update
  after update on public.daily_activity_summaries
  referencing new table as changed_rows
  for each statement execute function private.notify_activity_change();

-- ── Friend suggestions ───────────────────────────────────────────────
-- People the caller might know: friends of friends first (most mutual friends first), then other
-- people who chose to be discoverable, newest first. Never the caller, existing friends or
-- pending requests, people who turned off friend requests, or anyone across a block.
-- Returns the same public card as search_profiles, plus the number of mutual friends.
create function public.suggest_friends(max_results int default 20)
returns table (id uuid, username text, display_name text, avatar_path text, mutual_friends int)
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := private.require_user();
begin
  perform private.consume_rate_limit('suggest', 30, interval '1 minute');
  return query
    with my_friends as (
      select case when f.user_low = me then f.user_high else f.user_low end as friend_id
      from public.friendships f
      where (f.user_low = me or f.user_high = me) and f.status = 'accepted'
    ),
    mutual as (
      select case when f.user_low = mf.friend_id then f.user_high else f.user_low end as candidate,
             count(*)::int as n
      from my_friends mf
      join public.friendships f
        on (f.user_low = mf.friend_id or f.user_high = mf.friend_id) and f.status = 'accepted'
      group by 1
    )
    select p.id, p.username, p.display_name, p.avatar_path, coalesce(m.n, 0)
    from public.profiles p
    join public.user_privacy_settings s on s.user_id = p.id
    left join mutual m on m.candidate = p.id
    where p.id <> me
      and p.username is not null
      and s.discoverable
      and s.accept_friend_requests
      and not exists (
        select 1 from public.friendships f
        where f.user_low = least(p.id, me) and f.user_high = greatest(p.id, me)
      )
      and not private.is_blocked_between(p.id, me)
    order by coalesce(m.n, 0) desc, p.created_at desc
    limit least(greatest(coalesce(max_results, 20), 1), 50);
end;
$$;

revoke execute on function public.suggest_friends(int) from public, anon;
grant execute on function public.suggest_friends(int) to authenticated;

-- ── Whose presence may I watch? ──────────────────────────────────────
-- The app used to join every friend's presence channel; Realtime refuses friends who didn't opt
-- in, and the client library's endless rejoin attempts kept dropping the shared websocket (and
-- with it the live friendship and activity updates). The app now asks first and joins only these.
create function public.friends_sharing_presence()
returns setof uuid
language sql
stable
security definer
set search_path = ''
as $$
  select case when f.user_low = (select auth.uid()) then f.user_high else f.user_low end
  from public.friendships f
  where (select auth.uid()) in (f.user_low, f.user_high)
    and f.status = 'accepted'
    and private.presence_visible(case when f.user_low = (select auth.uid()) then f.user_high else f.user_low end);
$$;

revoke execute on function public.friends_sharing_presence() from public, anon;
grant execute on function public.friends_sharing_presence() to authenticated;

-- ═══════════════ 20261029130000_counted_reels.sql ═══════════════

-- Reels counted one by one on the device (ReelCounter's accessibility service).
--
-- A new metric category, COUNTED, ranked after VERIFIED and before ESTIMATED: an exact count made
-- on the phone, but not the platform's own data. Counted rows store their count in
-- estimated_reels with low = high = count, so the existing columns and constraints apply.
-- Clients may write it: activity_insert/activity_update only forbid VERIFIED.

alter type public.metric_quality add value if not exists 'COUNTED' after 'VERIFIED';

-- Friends read totals through this view. The new value can't be used as an enum literal in the
-- transaction that adds it, so counted rows are matched by their text form. New column at the end,
-- as CREATE OR REPLACE VIEW requires.
create or replace view public.daily_activity_totals with (security_invoker = true) as
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
       bool_or(user_adjusted)                                              as user_adjusted,
       sum(estimated_reels) filter (where quality::text = 'COUNTED')       as counted_reels
from public.daily_activity_summaries
group by user_id, platform_id, activity_date;

-- ═══════════════ 20261029140000_delete_account.sql ═══════════════

-- Account deletion from inside the app (Apple App Store guideline 5.1.1(v): apps that let people
-- create an account must let them delete it in the app).
--
-- Deleting the auth user cascades to the profile, privacy settings, devices, platform settings,
-- daily activity, friendships, blocks and rate limits (all reference profiles / auth.users with
-- ON DELETE CASCADE). Avatar files are removed by the app through the Storage API first, since
-- Storage objects can't be deleted with SQL.

create function public.delete_my_account() returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := private.require_user();
begin
  delete from auth.users where id = me;
end;
$$;

revoke execute on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

-- ═══════════════ 20261030120000_privacy_change_notify.sql ═══════════════

-- Live updates when someone changes who can see their figures.
--
-- Friends' apps refetch a friend's figures when an "activity" message arrives on their own
-- channel "friendships:<friend id>" (see 20261029120000). Until now only new figures sent it, so
-- after someone hid their activity (or showed it again, or switched stealth mode) their friends
-- kept seeing the old state until they refreshed. Now a change to activity_visibility or
-- stealth_mode sends the same message to every accepted friend. It still says only WHOSE
-- figures changed; RLS on daily_activity_totals decides what the refetch returns.

create function private.notify_privacy_change() returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  friend uuid;
begin
  for friend in
    select case when f.user_low = new.user_id then f.user_high else f.user_low end
    from public.friendships f
    where (f.user_low = new.user_id or f.user_high = new.user_id) and f.status = 'accepted'
  loop
    perform realtime.send(jsonb_build_object('user_id', new.user_id), 'activity', 'friendships:' || friend::text, true);
  end loop;
  return null;
end;
$$;
revoke execute on function private.notify_privacy_change() from public, anon, authenticated;

create trigger privacy_change_notify
  after update of activity_visibility, stealth_mode on public.user_privacy_settings
  for each row
  when (old.activity_visibility is distinct from new.activity_visibility
        or old.stealth_mode is distinct from new.stealth_mode)
  execute function private.notify_privacy_change();
