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
