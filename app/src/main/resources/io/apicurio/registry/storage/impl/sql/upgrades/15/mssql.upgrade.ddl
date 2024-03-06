-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrades the DB schema from version 14 to version 15.
-- *********************************************************************

UPDATE apicurio SET prop_value = 15 WHERE prop_name = 'db_version';

UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.SqlReferencesCanonicalHashUpgrader;
