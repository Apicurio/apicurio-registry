-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrades the DB schema from version 4 to version 5.
-- *********************************************************************

UPDATE apicurio SET prop_value = 5 WHERE prop_name = 'db_version';

CREATE TABLE artifactreferences (tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, version VARCHAR(256) NOT NULL, name VARCHAR(512) NOT NULL);
ALTER TABLE artifactreferences ADD PRIMARY KEY (name, tenantId);

ALTER TABLE versions ADD COLUMN artifactreferences VARCHAR(512);