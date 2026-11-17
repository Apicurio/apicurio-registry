-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mysql
-- Upgrade Script from 113 to 114
-- *********************************************************************
UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.SchemaUsagePrimaryKeyUpgrader;
UPDATE apicurio SET propValue = 114 WHERE propName = 'db_version';
