-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrades the DB schema from version 2 to version 3.
-- *********************************************************************

UPDATE apicurio SET prop_value = 3 WHERE prop_name = 'db_version';

CREATE TABLE artifact_references (tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, version VARCHAR(256), versionId INT NOT NULL, name VARCHAR(512));
ALTER TABLE artifact_references ADD PRIMARY KEY (name, tenantId);