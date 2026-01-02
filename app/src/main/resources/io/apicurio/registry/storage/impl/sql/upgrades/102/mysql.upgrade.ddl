-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MySQL
-- Upgrade from version 101 to 102
-- Search Query Optimization Phase 1 (Issue #7010)
-- *********************************************************************

-- Note: Composite indexes for label tables are not created for MySQL
-- due to the maximum key length limit of 3072 bytes. The existing
-- individual indexes on labelKey and labelValue columns will be used.

-- Update the database version
UPDATE apicurio SET propValue = '102' WHERE propName = 'db_version';
