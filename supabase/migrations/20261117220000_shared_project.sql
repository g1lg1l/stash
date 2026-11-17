-- Stash now lives in the Supabase project it shares with Midori and Matcha (one free project for the small
-- apps, so none of them is paused for inactivity). The function's address moved with it: point the
-- every-minute wake-up at the shared project. cron.schedule replaces the job of the same name.
select cron.schedule('smart-categories', '* * * * *', $$
  select net.http_post(
    url := 'https://yuapnmbvkmdjsgccqlgx.supabase.co/functions/v1/smart-categories',
    timeout_milliseconds := 60000
  ) where exists (select from private.smart_categories_due)
$$);
