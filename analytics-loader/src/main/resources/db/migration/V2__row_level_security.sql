-- Row-level security: a reader sees only the rows of the tenant it names.
--
-- Two roles use this database:
--   the loader (spring.datasource.username, the server's owner locally) writes rows of every
--     tenant. A superuser, or a role with BYPASSRLS, is not restricted by any policy.
--   analytics_reader reads: dashboards and reports (S8). Every policy applies to it.
--
-- A session says whose rows it wants with two settings:
--   SET app.tenant_id = 'ff-eu-freight'   that tenant's rows
--   SET app.all_tenants = 'on'            every tenant's rows (the platform's own reports)
-- A session that sets neither sees nothing: the safe answer to a forgotten setting.

-- The reader. LOGIN for the tools that will connect as it; its password is a Flyway placeholder
-- (spring.flyway.placeholders.analytics_reader_password), from the analytics-reader-credentials
-- Secret in the cluster. Roles belong to the server, not to one database: created once, kept.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'analytics_reader') THEN
        CREATE ROLE analytics_reader LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD '${analytics_reader_password}';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO analytics_reader;
GRANT SELECT ON shipment_deliveries TO analytics_reader;

-- ENABLE alone hides every row from everyone but the table's owner; FORCE holds the owner to it
-- too. The loader stays unrestricted because it is a superuser, not because it owns the table.
ALTER TABLE shipment_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE shipment_deliveries FORCE ROW LEVEL SECURITY;

-- current_setting(name, true): null instead of an error when the session never set it, and
-- tenant_id = null is never true. WITH CHECK: a row written by a restricted role must be its own.
CREATE POLICY tenant_isolation ON shipment_deliveries
    USING (tenant_id = current_setting('app.tenant_id', true)
           OR current_setting('app.all_tenants', true) = 'on')
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

-- The scope may change only between statements, never inside one. With set_config() a single
-- statement could widen its own scope half way:
--   SELECT set_config('app.all_tenants', 'on', false), count(*) FROM shipment_deliveries
-- and the rows after the call would be read with the new value. A chart's own SQL expression in
-- an embedded dashboard (S8) is such a statement, and a quoted or escaped name gets past any
-- check of the text. So no role but a superuser may call set_config() in this database; SET still
-- works, and SET is a statement of its own. Function privileges belong to the database: other
-- databases on the server are not affected.
REVOKE EXECUTE ON FUNCTION pg_catalog.set_config(text, text, boolean) FROM PUBLIC;
