-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MS SQL Server
-- Upgrade Script from 103 to 104
-- *********************************************************************

UPDATE apicurio SET propValue = 104 WHERE propName = 'db_version';

UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.AvroCanonicalHashUpgrader;
