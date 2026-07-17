-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 107 to 108
-- *********************************************************************
ALTER TABLE versions ADD versionSortKey NVARCHAR(512);
UPDATE apicurio SET propValue = 108 WHERE propName = 'db_version';
