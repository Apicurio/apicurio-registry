-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 101 to 102
-- *********************************************************************

UPDATE apicurio SET propValue = 102 WHERE propName = 'db_version';

UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.AvroCanonicalHashUpgrader;
