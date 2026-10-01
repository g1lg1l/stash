-- supabase test db: a table added later is private until its migration says otherwise.
begin;
select plan(3);

create table public.later (id int);
select ok((select relrowsecurity from pg_class where oid = 'public.later'::regclass), 'new tables get row level security');
select ok(not has_table_privilege('anon', 'public.later', 'select'), 'signed out gets no access');
select ok(not has_table_privilege('authenticated', 'public.later', 'select'), 'signed in gets no access until granted');

select * from finish();
rollback;
