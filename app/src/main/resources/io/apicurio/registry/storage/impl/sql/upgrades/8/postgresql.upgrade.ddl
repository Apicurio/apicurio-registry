-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrades the DB schema from version 7 to version 8.
-- *********************************************************************

UPDATE apicurio SET prop_value = 8 WHERE prop_name = 'db_version';

ALTER TABLE content ADD COLUMN artifactreferences VARCHAR(512);
