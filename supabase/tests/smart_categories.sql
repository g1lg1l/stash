-- supabase test db: which saves wait for the model, and the model's pick holding against stale pushes.
begin;
select plan(6);

insert into auth.users (id, email, raw_user_meta_data) values
  ('11111111-1111-1111-1111-111111111111', 'on@example.com', '{"smart_categories": true}'),
  ('22222222-2222-2222-2222-222222222222', 'off@example.com', '{}');
insert into public.saves (id, user_id, url, canonical_url, status, category_is_manual, deleted_at, created_at, last_saved_at) values
  ('00000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111', 'https://a.com', 'a.com', 'enriched', false, null, now(), now()),
  ('00000000-0000-4000-8000-000000000002', '11111111-1111-1111-1111-111111111111', 'https://b.com', 'b.com', 'failed', false, null, now(), now()),
  ('00000000-0000-4000-8000-000000000003', '11111111-1111-1111-1111-111111111111', 'https://c.com', 'c.com', 'pending', false, null, now(), now()),
  ('00000000-0000-4000-8000-000000000004', '11111111-1111-1111-1111-111111111111', 'https://d.com', 'd.com', 'enriched', true, null, now(), now()),
  ('00000000-0000-4000-8000-000000000005', '11111111-1111-1111-1111-111111111111', 'https://e.com', 'e.com', 'enriched', false, now(), now(), now()),
  ('00000000-0000-4000-8000-000000000006', '22222222-2222-2222-2222-222222222222', 'https://f.com', 'f.com', 'enriched', false, null, now(), now());

select results_eq(
  'select id::text from private.smart_categories_due order by id',
  array['00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000002'],
  'due: enriched or failed, not sorted by hand, not deleted, and only for people who turned it on');

-- The model's pick, as the Edge Function writes it.
update public.saves set categorized_at = now() - interval '1 minute' where id = '00000000-0000-4000-8000-000000000001';
update public.saves set category = 'food', categorized_at = now() where id = '00000000-0000-4000-8000-000000000001';
select is((select category from public.saves where id = '00000000-0000-4000-8000-000000000001'), 'food', 'the model''s pick lands');
select is((select count(*) from private.smart_categories_due where id = '00000000-0000-4000-8000-000000000001')::int, 0, 'and it isn''t due again');

set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
update public.saves set category = 'other', opened_at = now() where id = '00000000-0000-4000-8000-000000000001';
select is((select category from public.saves where id = '00000000-0000-4000-8000-000000000001'), 'food', 'a phone''s stale category doesn''t undo it');
update public.saves set category = 'travel', category_is_manual = true where id = '00000000-0000-4000-8000-000000000001';
select is((select category from public.saves where id = '00000000-0000-4000-8000-000000000001'), 'travel', 'a pick by hand does');

select throws_ok('select * from private.smart_categories_due', '42501', null, 'the queue is out of the clients'' reach');

select * from finish();
rollback;
