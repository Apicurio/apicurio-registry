-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mysql
-- Upgrade Script from 115 to 116
-- *********************************************************************
UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.SchemaUsagePrimaryKeyUpgrader;
UPDATE apicurio SET propValue = 116 WHERE propName = 'db_version';
