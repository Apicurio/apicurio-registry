-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MySQL
-- Upgrade from version 102 to 102
-- Search Query Optimization Phase 1 (Issue #7010)
-- *********************************************************************

-- Note: MySQL does not support trigram indexes like PostgreSQL's pg_trgm.
-- For substring searches, MySQL uses standard LIKE with B-tree indexes.
-- Consider using FULLTEXT indexes for text search in future phases.

-- Update the database version
UPDATE apicurio SET propValue = '102' WHERE propName = 'db_version';
