-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrade from version 101 to 102
-- Search Query Optimization (Issue #7010)
-- - Trigram indexes for substring searches
-- - Composite indexes for JOIN-based label filtering
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

-- Composite indexes for JOIN-based label searches
-- These indexes optimize the JOIN conditions used in the SearchQueryBuilder
-- for label filtering queries.

-- Artifact labels composite index: covers JOIN on (groupId, artifactId) with labelKey filter
CREATE INDEX IF NOT EXISTS IDX_alabels_composite ON artifact_labels(groupId, artifactId, labelKey, labelValue);

-- Version labels composite index: covers JOIN on (globalId) with labelKey filter
CREATE INDEX IF NOT EXISTS IDX_vlabels_composite ON version_labels(globalId, labelKey, labelValue);

-- Group labels composite index: covers JOIN on (groupId) with labelKey filter
CREATE INDEX IF NOT EXISTS IDX_glabels_composite ON group_labels(groupId, labelKey, labelValue);

-- Update the database version
UPDATE apicurio SET propValue = '102' WHERE propName = 'db_version';
