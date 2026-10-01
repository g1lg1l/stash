-- The dashboard's "Automatically expose new tables" off and "Enable automatic RLS" on, in code,
-- so they hold locally and on the hosted project alike.

-- New tables, sequences and functions start with no access for the client roles: each migration
-- grants what it needs, as saves does.
alter default privileges in schema public revoke all on tables from anon, authenticated;
alter default privileges in schema public revoke all on sequences from anon, authenticated;
alter default privileges in schema public revoke all on functions from anon, authenticated;

-- Every table created in public gets row level security, so a forgotten policy means no access
-- rather than open access.
create function public.enable_rls_on_new_tables() returns event_trigger
language plpgsql set search_path = '' as $$
declare
  command record;
begin
  for command in
    select * from pg_event_trigger_ddl_commands()
    where object_type = 'table' and schema_name = 'public'
  loop
    execute format('alter table %s enable row level security', command.object_identity);
  end loop;
end $$;

create event trigger enable_rls_on_new_tables on ddl_command_end
  when tag in ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
  execute function public.enable_rls_on_new_tables();
