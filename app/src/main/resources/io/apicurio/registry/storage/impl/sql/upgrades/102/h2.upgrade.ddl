-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrade from version 101 to 102
-- Search Query Optimization Phase 1 (Issue #7010)
-- *********************************************************************

-- Note: H2 does not support trigram indexes like PostgreSQL's pg_trgm.
-- For substring searches, H2 uses standard LIKE with B-tree indexes.

-- Update the database version
UPDATE apicurio SET propValue = '102' WHERE propName = 'db_version';
