-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MySQL
-- Upgrade from version 101 to 102
-- Search Query Optimization (Issue #7010)
-- - Composite indexes for JOIN-based label filtering
-- *********************************************************************

-- Note: MySQL does not support trigram indexes like PostgreSQL's pg_trgm.
-- For substring searches, MySQL uses standard LIKE with B-tree indexes.

-- Composite indexes for JOIN-based label searches
-- These indexes optimize the JOIN conditions used in the SearchQueryBuilder
-- for label filtering queries.

-- Artifact labels composite index: covers JOIN on (groupId, artifactId) with labelKey filter
-- Note: Using prefix length on labelValue to stay within MySQL's 3072 byte index key limit
-- (groupId:512 + artifactId:512 + labelKey:256 + labelValue:255*4=1020 = 2300 bytes)
CREATE INDEX IDX_alabels_composite ON artifact_labels(groupId, artifactId, labelKey, labelValue(255));

-- Version labels composite index: covers JOIN on (globalId) with labelKey filter
-- (globalId:8 + labelKey:256 + labelValue:255*4=1020 = 1284 bytes)
CREATE INDEX IDX_vlabels_composite ON version_labels(globalId, labelKey, labelValue(255));

-- Group labels composite index: covers JOIN on (groupId) with labelKey filter
-- (groupId:512 + labelKey:256 + labelValue:255*4=1020 = 1788 bytes)
CREATE INDEX IDX_glabels_composite ON group_labels(groupId, labelKey, labelValue(255));

-- Update the database version
UPDATE apicurio SET propValue = '102' WHERE propName = 'db_version';
