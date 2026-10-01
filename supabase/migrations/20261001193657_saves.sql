-- Each device's own database is the source of truth; this table is the copy they sync through.
-- Columns mirror Save on iOS and Android. Enums are the same lowercase strings, with no check
-- constraints, so an older app keeps syncing when a newer one adds a value.
create table public.saves (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users on delete cascade,
  url text not null,
  canonical_url text not null,
  source text not null default 'unknown',
  content_type text not null default 'other',
  category text not null default 'other',
  status text not null default 'pending',
  category_is_manual boolean not null default false,
  title text,
  description_text text,
  thumbnail_url text,
  author text,
  tags text[] not null default '{}',
  summary text,
  created_at timestamptz not null,
  last_saved_at timestamptz not null,
  opened_at timestamptz,
  -- Set by the server on every write: devices pull what changed since the last updated_at they saw.
  updated_at timestamptz not null default now(),
  -- Deletes are tombstones, so other devices learn about them.
  deleted_at timestamptz
);

create index saves_user_updated on public.saves (user_id, updated_at);

-- ponytail: the server clock decides, so the last write to reach it wins. Fine for one person's
-- saves; add per-field merges if edits from two devices start clobbering each other.
create function public.touch_updated_at() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.updated_at = now();
  return new;
end $$;

create trigger saves_touch before insert or update on public.saves
for each row execute function public.touch_updated_at();

-- The repo and the client keys are public: row level security is what keeps saves private.
alter table public.saves enable row level security;

create policy "Read own saves" on public.saves for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "Add own saves" on public.saves for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "Change own saves" on public.saves for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- No delete grant: devices set deleted_at instead. Deleting the account removes the rows.
revoke all on public.saves from anon, authenticated;
grant select, insert, update on public.saves to authenticated;
