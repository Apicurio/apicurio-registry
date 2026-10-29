-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mysql
-- Upgrade Script from 111 to 112
-- *********************************************************************

UPDATE apicurio SET propValue = 112 WHERE propName = 'db_version';

UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.AvroCanonicalHashUpgrader;
