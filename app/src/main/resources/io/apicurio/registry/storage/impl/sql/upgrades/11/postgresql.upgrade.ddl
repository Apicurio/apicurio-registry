-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrades the DB schema from version 10 to version 11.
-- *********************************************************************

UPDATE apicurio SET prop_value = 11 WHERE prop_name = 'db_version';

-- Delete FK for references
ALTER TABLE artifactreferences DROP CONSTRAINT FK_artifactreferences_2
