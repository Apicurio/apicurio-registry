-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MySQL
-- Upgrade Script from 107 to 108
-- *********************************************************************

ALTER TABLE versions ADD COLUMN versionSortKey VARCHAR(512);
UPDATE versions SET versionSortKey = version;

UPDATE apicurio SET propValue = 108 WHERE propName = 'db_version';