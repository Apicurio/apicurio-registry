-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrades the DB schema from version 11 to version 12.
-- *********************************************************************

UPDATE apicurio SET prop_value = 12 WHERE prop_name = 'db_version';

UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.SqlReferencesContentHashUpgrader;