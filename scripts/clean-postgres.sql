SELECT schemaname, tablename, tableowner FROM pg_tables WHERE schemaname = 'public';

DO $$ DECLARE
    r RECORD;
BEGIN
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE 'DROP TABLE IF EXISTS ' || quote_ident(r.tablename) || ' CASCADE';
    END LOOP;
END $$;

SELECT schemaname, tablename, tableowner FROM pg_tables WHERE schemaname = 'public';

SELECT relname FROM pg_class WHERE relkind = 'S';

DO $$ DECLARE
    r RECORD;
BEGIN
    FOR r IN (SELECT relname FROM pg_class WHERE relkind = 'S') LOOP
        EXECUTE 'DROP SEQUENCE IF EXISTS ' || quote_ident(r.relname) || ' CASCADE';
    END LOOP;
END $$;

SELECT relname FROM pg_class WHERE relkind = 'S';