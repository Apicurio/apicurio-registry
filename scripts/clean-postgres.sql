SELECT schemaname, tablename, tableowner FROM pg_tables WHERE schemaname = 'public';

DO $$ DECLARE
    r RECORD;
BEGIN
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE 'DROP TABLE IF EXISTS ' || quote_ident(r.tablename) || ' CASCADE';
    END LOOP;
END $$;

SELECT schemaname, tablename, tableowner FROM pg_tables WHERE schemaname = 'public';