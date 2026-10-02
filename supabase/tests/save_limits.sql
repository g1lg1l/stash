-- supabase test db: oversized fields are cut down, and an account holds at most 10,000 saves.
begin;
select plan(6);

insert into auth.users (id, email) values ('11111111-1111-1111-1111-111111111111', 'a@example.com');
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';

insert into public.saves (id, url, canonical_url, title, tags, created_at, last_saved_at) values (
  '00000000-0000-4000-8000-000000000001', 'https://example.com/' || repeat('a', 10000), 'example.com',
  repeat('t', 5000), array(select 'tag' || n || repeat('x', 200) from generate_series(1, 40) as n), now(), now());
select is((select length(title) from public.saves), 1000, 'a long title is cut to 1,000 characters');
select is((select length(url) from public.saves), 8192, 'a long URL is cut to 8,192');
select is((select cardinality(tags) from public.saves), 30, 'tags past the 30th are dropped');
select is((select left(tags[2], 4) || length(tags[2]) from public.saves), 'tag2100', 'tags keep their order and 100 characters');

insert into public.saves (id, url, canonical_url, created_at, last_saved_at)
  select gen_random_uuid(), 'https://example.com', 'example.com', now(), now() from generate_series(2, 10000);
select is((select count(*) from public.saves)::int, 10000, '10,000 saves fit');
select throws_ok(
  $$insert into public.saves (id, url, canonical_url, created_at, last_saved_at)
    values (gen_random_uuid(), 'https://example.com', 'example.com', now(), now())$$,
  'P0001', 'Stash keeps up to 10,000 saves per account.', 'the 10,001st is refused');

select * from finish();
rollback;
