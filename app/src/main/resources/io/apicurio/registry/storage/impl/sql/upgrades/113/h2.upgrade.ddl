-- *********************************************************************
-- DDL for the Apicurio Registry - Database: h2
-- Upgrade Script from 112 to 113
-- *********************************************************************
ALTER TABLE versions ADD CONSTRAINT UQ_versions_3 UNIQUE (groupId, artifactId, versionOrder);
UPDATE apicurio SET propValue = 113 WHERE propName = 'db_version';
