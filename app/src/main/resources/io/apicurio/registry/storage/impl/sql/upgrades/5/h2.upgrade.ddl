-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrades the DB schema from version 4 to version 5.
-- *********************************************************************

UPDATE apicurio SET prop_value = 5 WHERE prop_name = 'db_version';

UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.ProtobufCanonicalHashUpgrader;
