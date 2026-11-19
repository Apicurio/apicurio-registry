-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mysql
-- Upgrade Script from 114 to 115
-- *********************************************************************
UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.SchemaUsagePrimaryKeyUpgrader;
UPDATE apicurio SET propValue = 115 WHERE propName = 'db_version';
