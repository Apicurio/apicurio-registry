-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrades the DB schema from version 13 to version 14.
-- *********************************************************************

UPDATE apicurio SET prop_value = 14 WHERE prop_name = 'db_version';

UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.ReferencesCanonicalHashUpgrader;