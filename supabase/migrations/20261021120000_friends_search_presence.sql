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
