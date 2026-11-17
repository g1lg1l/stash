-- Smart categories are gone: no model picks a category any more. The keyword pick the devices make when they
-- save, and a pick by hand, are all there is. Everything 20261002105640_smart_categories.sql added goes, except
-- the pg_cron and pg_net extensions, which other things in the shared project may come to use.
select cron.unschedule('smart-categories');

drop view private.smart_categories_due;
drop schema private;

drop trigger saves_keep_smart_category on public.saves;
drop function public.keep_smart_category();

-- The server's own column: the apps never sent it and ignored it on pull.
alter table public.saves drop column categorized_at;
