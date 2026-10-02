-- Smart categories (#19): for people who turn it on in Settings, Gemini picks a category for every save they
-- haven't sorted by hand. The apps keep the choice in the account's user metadata (`smart_categories`), so the
-- server knows it; the Edge Function `smart-categories` does the picking.

-- When the model picked (null: not yet). Server only: the apps never send it, so their upserts leave it alone.
alter table public.saves add column categorized_at timestamptz;

-- Once the model has picked, only a pick by hand changes the category. A phone that hasn't pulled the model's
-- pick yet sends its old category along with any other change, and the last write would win.
create function public.keep_smart_category() returns trigger
language plpgsql set search_path = '' as $$
begin
  if old.categorized_at is not null and new.categorized_at is not distinct from old.categorized_at
     and not new.category_is_manual then
    new.category = old.category;
  end if;
  return new;
end $$;

create trigger saves_keep_smart_category before update on public.saves
for each row execute function public.keep_smart_category();

-- Out of the API's reach: only the database and the Edge Function (which connects as postgres) see it.
create schema private;

-- What waits for the model: enriched saves (a title to go on; pending ones are still being fetched by a phone),
-- not sorted by hand, of people who turned it on. Newest first, so a new save never waits behind a backlog.
-- ponytail: scans saves every minute; add a partial index on categorized_at is null once that shows up.
create view private.smart_categories_due as
  select s.id from public.saves as s join auth.users as u on u.id = s.user_id
  where s.categorized_at is null and not s.category_is_manual and s.deleted_at is null and s.status <> 'pending'
    and coalesce((u.raw_user_meta_data ->> 'smart_categories')::boolean, false)
  order by s.created_at desc;

-- Every minute, and only while something waits, wake the Edge Function. pg_net sends it in the background.
-- The project URL is public (REQUIREMENTS.md); a local stack calls the hosted function too, which then finds
-- nothing of its own to do.
create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

select cron.schedule('smart-categories', '* * * * *', $$
  select net.http_post(
    url := 'https://kzgmhvqbvrylwydqskcm.supabase.co/functions/v1/smart-categories',
    timeout_milliseconds := 60000
  ) where exists (select from private.smart_categories_due)
$$);
