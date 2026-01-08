-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrade from version 102 to 102
-- Search Query Optimization Phase 1 - Trigram Indexes (Issue #7010)
-- *********************************************************************

-- Create pg_trgm extension if it doesn't exist (requires superuser or create extension privilege)
-- This is optional - if the extension cannot be created, substring searches will still work
-- but without the trigram index optimization.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        BEGIN
            CREATE EXTENSION pg_trgm;
            RAISE NOTICE 'pg_trgm extension created successfully';
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'pg_trgm extension could not be created (requires superuser privilege). Trigram indexes will not be available.';
        END;
    END IF;
END
$$;

-- Create GIN trigram indexes for substring searches on artifacts table
-- These indexes significantly improve LIKE '%search%' query performance
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        -- Artifact name trigram index
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_artifacts_name_trgm') THEN
            CREATE INDEX IDX_artifacts_name_trgm ON artifacts USING GIN (name gin_trgm_ops);
            RAISE NOTICE 'Created trigram index on artifacts.name';
        END IF;

        -- Artifact description trigram index
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_artifacts_description_trgm') THEN
            CREATE INDEX IDX_artifacts_description_trgm ON artifacts USING GIN (description gin_trgm_ops);
            RAISE NOTICE 'Created trigram index on artifacts.description';
        END IF;

        -- Version name trigram index
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_versions_name_trgm') THEN
            CREATE INDEX IDX_versions_name_trgm ON versions USING GIN (name gin_trgm_ops);
            RAISE NOTICE 'Created trigram index on versions.name';
        END IF;

        -- Version description trigram index
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_versions_description_trgm') THEN
            CREATE INDEX IDX_versions_description_trgm ON versions USING GIN (description gin_trgm_ops);
            RAISE NOTICE 'Created trigram index on versions.description';
        END IF;

        -- Group description trigram index
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_groups_description_trgm') THEN
            CREATE INDEX IDX_groups_description_trgm ON groups USING GIN (description gin_trgm_ops);
            RAISE NOTICE 'Created trigram index on groups.description';
        END IF;
    ELSE
        RAISE NOTICE 'pg_trgm extension not available. Skipping trigram index creation.';
    END IF;
END
$$;

-- Update the database version
UPDATE apicurio SET propValue = '102' WHERE propName = 'db_version';
