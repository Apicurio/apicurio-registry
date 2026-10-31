-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 111 to 112
-- *********************************************************************
UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.DuplicateVersionOrderUpgrader;
ALTER TABLE versions ADD CONSTRAINT UQ_versions_3 UNIQUE (groupId, artifactId, versionOrder);
UPDATE apicurio SET propValue = 112 WHERE propName = 'db_version';
