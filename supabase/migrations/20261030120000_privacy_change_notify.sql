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
