-- Settings → Delete account: removes the signed-in user, and their saves with them (on delete cascade).
-- The App Store requires it once an app has accounts. Called as POST /rest/v1/rpc/delete_account.
create function public.delete_account() returns void
language sql security definer set search_path = '' as $$
  delete from auth.users where id = (select auth.uid());
$$;

revoke execute on function public.delete_account() from public, anon;
grant execute on function public.delete_account() to authenticated;
