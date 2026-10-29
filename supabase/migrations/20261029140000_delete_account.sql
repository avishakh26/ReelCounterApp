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
