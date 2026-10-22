-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 108 to 109
-- *********************************************************************
ALTER TABLE versions ADD versionSortKey NVARCHAR(512);
UPDATE apicurio SET propValue = 109 WHERE propName = 'db_version';
UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.VersionSortKeyUpgrader;
