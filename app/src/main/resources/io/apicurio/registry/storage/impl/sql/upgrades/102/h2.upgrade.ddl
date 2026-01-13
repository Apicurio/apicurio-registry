-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrade from version 101 to 102
-- Search Query Optimization (Issue #7010)
-- - Composite indexes for JOIN-based label filtering
-- *********************************************************************

-- Note: H2 does not support trigram indexes like PostgreSQL's pg_trgm.
-- For substring searches, H2 uses standard LIKE with B-tree indexes.

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
