-- supabase test db: one user can't see, change or delete another's saves.
begin;
select plan(6);

insert into auth.users (id, email) values
  ('11111111-1111-1111-1111-111111111111', 'a@example.com'),
  ('22222222-2222-2222-2222-222222222222', 'b@example.com');

set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
insert into public.saves (id, url, canonical_url, created_at, last_saved_at)
  values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'https://example.com', 'example.com', now(), now());
select is((select count(*) from public.saves)::int, 1, 'the owner sees their save');

set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select is((select count(*) from public.saves)::int, 0, 'another user sees nothing');
update public.saves set title = 'mine now';
select throws_ok(
  $$insert into public.saves (id, user_id, url, canonical_url, created_at, last_saved_at)
    values (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'x', 'x', now(), now())$$,
  '42501', null, 'no one writes rows for someone else');
select throws_ok('delete from public.saves', '42501', null, 'devices tombstone instead of deleting');

set local role anon;
select throws_ok('select * from public.saves', '42501', null, 'signed out sees nothing');

reset role;
select is((select title from public.saves), null, 'another user''s update changed nothing');
select * from finish();
rollback;
