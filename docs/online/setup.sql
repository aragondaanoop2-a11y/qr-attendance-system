-- Attendly cloud attendance schema for Supabase.
-- Paste this entire file into Supabase SQL Editor and click Run.

create table if not exists public.qr_profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null default 'Attendly user',
  student_id text,
  role text not null default 'student' check (role in ('student', 'faculty')),
  created_at timestamptz not null default now()
);

create unique index if not exists qr_profiles_student_id_unique
  on public.qr_profiles (lower(student_id))
  where student_id is not null;

create table if not exists public.qr_attendance_sessions (
  id uuid primary key,
  faculty_user_id uuid not null references auth.users(id) on delete cascade,
  course text not null check (length(trim(course)) between 1 and 120),
  section text not null default '' check (length(section) <= 60),
  status text not null default 'active' check (status in ('active', 'closed')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  constraint qr_session_expiry_window check (
    expires_at > created_at and expires_at <= created_at + interval '2 hours'
  )
);

create index if not exists qr_sessions_faculty_created
  on public.qr_attendance_sessions (faculty_user_id, created_at desc);

-- Tokens are not readable by browser clients. Only the checked RPCs below use this table.
create table if not exists public.qr_session_secrets (
  session_id uuid primary key references public.qr_attendance_sessions(id) on delete cascade,
  checkin_token text not null
);

create table if not exists public.qr_attendance_records (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.qr_attendance_sessions(id) on delete cascade,
  student_user_id uuid not null references auth.users(id) on delete cascade,
  marked_at timestamptz not null default statement_timestamp(),
  constraint qr_one_checkin_per_student unique (session_id, student_user_id)
);

create index if not exists qr_records_session_marked
  on public.qr_attendance_records (session_id, marked_at);
create index if not exists qr_records_student_marked
  on public.qr_attendance_records (student_user_id, marked_at desc);

alter table public.qr_profiles enable row level security;
alter table public.qr_attendance_sessions enable row level security;
alter table public.qr_session_secrets enable row level security;
alter table public.qr_attendance_records enable row level security;

-- Every new Auth user starts as a student. Only the project owner can promote faculty.
create or replace function public.qr_handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.qr_profiles (id, full_name, student_id, role)
  values (
    new.id,
    coalesce(nullif(trim(new.raw_user_meta_data ->> 'full_name'), ''), split_part(coalesce(new.email, 'Attendly user'), '@', 1)),
    nullif(upper(trim(new.raw_user_meta_data ->> 'student_id')), ''),
    'student'
  );
  return new;
end;
$$;

drop trigger if exists qr_auth_user_created on auth.users;
create trigger qr_auth_user_created
  after insert on auth.users
  for each row execute function public.qr_handle_new_user();

create or replace function public.qr_current_is_faculty()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.qr_profiles
    where id = auth.uid() and role = 'faculty'
  );
$$;

revoke all on function public.qr_handle_new_user() from public, anon, authenticated;
revoke all on function public.qr_current_is_faculty() from public, anon;
grant execute on function public.qr_current_is_faculty() to authenticated;

drop policy if exists "read own profile or faculty attendance profiles" on public.qr_profiles;
create policy "read own profile or faculty attendance profiles"
  on public.qr_profiles for select to authenticated
  using (
    id = (select auth.uid())
    or (
      (select public.qr_current_is_faculty())
      and exists (
        select 1
        from public.qr_attendance_records r
        join public.qr_attendance_sessions s on s.id = r.session_id
        where r.student_user_id = qr_profiles.id
          and s.faculty_user_id = (select auth.uid())
      )
    )
  );

drop policy if exists "faculty read own sessions" on public.qr_attendance_sessions;
create policy "faculty read own sessions"
  on public.qr_attendance_sessions for select to authenticated
  using (
    faculty_user_id = (select auth.uid())
    and (select public.qr_current_is_faculty())
  );

drop policy if exists "faculty close own sessions" on public.qr_attendance_sessions;
create policy "faculty close own sessions"
  on public.qr_attendance_sessions for update to authenticated
  using (
    faculty_user_id = (select auth.uid())
    and (select public.qr_current_is_faculty())
  )
  with check (
    faculty_user_id = (select auth.uid())
    and (select public.qr_current_is_faculty())
  );

drop policy if exists "students and faculty read permitted attendance" on public.qr_attendance_records;
create policy "students and faculty read permitted attendance"
  on public.qr_attendance_records for select to authenticated
  using (
    student_user_id = (select auth.uid())
    or (
      (select public.qr_current_is_faculty())
      and exists (
        select 1 from public.qr_attendance_sessions s
        where s.id = qr_attendance_records.session_id
          and s.faculty_user_id = (select auth.uid())
      )
    )
  );

-- Browser clients can only read their own profile and allowed rows. Session creation and
-- check-in happen through narrowly scoped RPC functions; students cannot write attendance.
revoke all on public.qr_profiles from anon, authenticated;
revoke all on public.qr_attendance_sessions from anon, authenticated;
revoke all on public.qr_session_secrets from anon, authenticated;
revoke all on public.qr_attendance_records from anon, authenticated;
grant select on public.qr_profiles to authenticated;
grant select on public.qr_attendance_sessions to authenticated;
grant update (status) on public.qr_attendance_sessions to authenticated;
grant select on public.qr_attendance_records to authenticated;

create or replace function public.qr_create_session(
  p_course text,
  p_section text,
  p_duration_minutes integer
)
returns table (new_session_id uuid, checkin_token text, expires_at timestamptz)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_session_id uuid := gen_random_uuid();
  v_token text := replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '');
  v_expires_at timestamptz := statement_timestamp() + make_interval(mins => p_duration_minutes);
begin
  if auth.uid() is null or not public.qr_current_is_faculty() then
    raise exception 'Faculty sign-in is required to create a session.';
  end if;
  if p_course is null or length(trim(p_course)) not between 1 and 120 then
    raise exception 'Enter a course name (up to 120 characters).';
  end if;
  if coalesce(length(p_section), 0) > 60 then
    raise exception 'Section must be 60 characters or fewer.';
  end if;
  if p_duration_minutes is null or p_duration_minutes not between 1 and 120 then
    raise exception 'Session duration must be from 1 to 120 minutes.';
  end if;

  insert into public.qr_attendance_sessions (id, faculty_user_id, course, section, status, created_at, expires_at)
  values (v_session_id, auth.uid(), trim(p_course), coalesce(trim(p_section), ''), 'active', statement_timestamp(), v_expires_at);
  insert into public.qr_session_secrets (session_id, checkin_token)
  values (v_session_id, v_token);

  return query select v_session_id, v_token, v_expires_at;
end;
$$;

create or replace function public.qr_record_attendance(
  p_session_id uuid,
  p_checkin_token text
)
returns table (course text, section text, marked_at timestamptz, already_recorded boolean)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_student_id text;
  v_session public.qr_attendance_sessions%rowtype;
  v_saved_token text;
  v_marked_at timestamptz;
  v_inserted boolean := false;
begin
  if v_user_id is null then
    raise exception 'Sign in with your student account before checking in.';
  end if;

  select role, student_id into v_role, v_student_id
  from public.qr_profiles where id = v_user_id;
  if v_role is distinct from 'student' then
    raise exception 'Student accounts are required to check in.';
  end if;
  if v_student_id is null or length(trim(v_student_id)) = 0 then
    raise exception 'Your account has no student ID. Ask your faculty member to correct your profile.';
  end if;

  select * into v_session
  from public.qr_attendance_sessions
  where id = p_session_id and status = 'active' and expires_at > statement_timestamp();
  if not found then
    raise exception 'This attendance session is closed or expired.';
  end if;

  select checkin_token into v_saved_token
  from public.qr_session_secrets where session_id = p_session_id;
  if v_saved_token is null or p_checkin_token is null or v_saved_token <> p_checkin_token then
    raise exception 'This QR link is invalid. Scan the current code displayed by faculty.';
  end if;

  insert into public.qr_attendance_records (session_id, student_user_id, marked_at)
  values (p_session_id, v_user_id, statement_timestamp())
  on conflict (session_id, student_user_id) do nothing
  returning qr_attendance_records.marked_at into v_marked_at;

  if found then
    v_inserted := true;
  else
    select r.marked_at into v_marked_at
    from public.qr_attendance_records r
    where r.session_id = p_session_id and r.student_user_id = v_user_id;
  end if;

  return query select v_session.course, v_session.section, v_marked_at, not v_inserted;
end;
$$;

revoke all on function public.qr_create_session(text, text, integer) from public, anon;
revoke all on function public.qr_record_attendance(uuid, text) from public, anon;
grant execute on function public.qr_create_session(text, text, integer) to authenticated;
grant execute on function public.qr_record_attendance(uuid, text) to authenticated;

create or replace view public.qr_faculty_attendance
with (security_invoker = true)
as
  select
    r.id,
    r.session_id,
    p.student_id,
    p.full_name as student_name,
    s.course,
    s.section,
    r.marked_at
  from public.qr_attendance_records r
  join public.qr_profiles p on p.id = r.student_user_id
  join public.qr_attendance_sessions s on s.id = r.session_id;

grant select on public.qr_faculty_attendance to authenticated;
