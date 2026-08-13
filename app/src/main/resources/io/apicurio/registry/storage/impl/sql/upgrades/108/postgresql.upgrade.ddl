-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 107 to 108
-- *********************************************************************

UPDATE apicurio SET propValue = 108 WHERE propName = 'db_version';

UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.AvroCanonicalHashUpgrader;
