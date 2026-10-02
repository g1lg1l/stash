-- The client keys are public and anyone can sign up, so one account mustn't be able to fill the free tier's
-- 500 MB database, which then goes read-only for everyone.

-- Oversized fields are cut down rather than refused: a refusal fails the whole batch a phone pushes, and its
-- sync would stall on that one save. The cut values reach the devices on their next pull. The limits sit far
-- above anything a link's metadata needs.
create function public.limit_save_size() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.url = left(new.url, 8192);
  new.canonical_url = left(new.canonical_url, 8192);
  new.thumbnail_url = left(new.thumbnail_url, 8192);
  new.source = left(new.source, 32);
  new.content_type = left(new.content_type, 32);
  new.category = left(new.category, 32);
  new.status = left(new.status, 32);
  new.title = left(new.title, 1000);
  new.author = left(new.author, 300);
  new.description_text = left(new.description_text, 4000);
  new.summary = left(new.summary, 4000);
  new.tags = array(select left(tag, 100) from unnest(new.tags[1:30]) with ordinality as t(tag, i) order by i);
  return new;
end $$;

create trigger saves_limit_size before insert or update on public.saves
for each row execute function public.limit_save_size();

-- ponytail: a flat cap per account, tombstones included. Purge old tombstones, or raise it, if anyone gets close.
create function public.limit_saves_per_user() returns trigger
language plpgsql set search_path = '' as $$
begin
  if exists (
    select from (select distinct user_id from added) as a
    where (select count(*) from public.saves as s where s.user_id = a.user_id) > 10000
  ) then
    raise exception 'Stash keeps up to 10,000 saves per account.';
  end if;
  return null;
end $$;

create trigger saves_per_user after insert on public.saves
referencing new table as added
for each statement execute function public.limit_saves_per_user();
