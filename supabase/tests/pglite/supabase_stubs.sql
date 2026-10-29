-- Minimal stand-ins for the parts of a Supabase database our migrations touch, so they can be
-- tested in PGlite (Postgres in WebAssembly) without Docker. Mirrors Supabase's real objects
-- closely enough for RLS tests: roles, default grants, auth.uid(), auth.users, storage.

create role anon nologin;
create role authenticated nologin;
create role service_role nologin bypassrls;

-- Supabase grants API roles broad table privileges by default; RLS and explicit revokes do the
-- real restricting. Reproducing that makes our revokes and policies meaningful in tests.
grant usage on schema public to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
alter default privileges in schema public grant all on functions to anon, authenticated, service_role;
alter default privileges in schema public grant all on sequences to anon, authenticated, service_role;

-- auth
create schema auth;
grant usage on schema auth to anon, authenticated, service_role;

create table auth.users (
  id    uuid primary key,
  email text
);

create function auth.uid() returns uuid
language sql stable
as $$
  select coalesce(
    nullif(current_setting('request.jwt.claim.sub', true), ''),
    (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub')
  )::uuid
$$;

-- storage
create schema storage;
grant usage on schema storage to anon, authenticated, service_role;

create table storage.buckets (
  id                 text primary key,
  name               text not null,
  public             boolean default false,
  file_size_limit    bigint,
  allowed_mime_types text[]
);

create table storage.objects (
  id        uuid primary key default gen_random_uuid(),
  bucket_id text references storage.buckets (id),
  name      text,
  owner     uuid
);

alter table storage.objects enable row level security;
grant all on storage.objects to anon, authenticated, service_role;
grant select on storage.buckets to anon, authenticated, service_role;

-- realtime (Realtime Authorization): policies on realtime.messages decide who may join private
-- channels; realtime.topic() is the channel being joined (tests set it with set_config).
create schema realtime;
grant usage on schema realtime to anon, authenticated, service_role;

create table realtime.messages (
  id        bigserial primary key,
  topic     text not null,
  extension text not null,
  payload   jsonb,
  event     text,
  private   boolean default true
);
alter table realtime.messages enable row level security;
grant select, insert on realtime.messages to anon, authenticated;
grant usage on sequence realtime.messages_id_seq to anon, authenticated;

create function realtime.topic() returns text
language sql stable
as $$ select nullif(current_setting('realtime.topic', true), '') $$;

-- Broadcast from Database: Supabase's realtime.send queues a broadcast as a row in
-- realtime.messages; the Realtime server delivers it to members allowed by the select policies.
create function realtime.send(payload jsonb, event text, topic text, private boolean default true) returns void
language sql
as $$ insert into realtime.messages (topic, extension, payload, event, private) values (topic, 'broadcast', payload, event, private) $$;

-- Same behavior as Supabase: every path segment except the file name.
create function storage.foldername(name text) returns text[]
language plpgsql
as $$
declare
  _parts text[];
begin
  select string_to_array(name, '/') into _parts;
  return _parts[1:array_length(_parts, 1) - 1];
end
$$;
