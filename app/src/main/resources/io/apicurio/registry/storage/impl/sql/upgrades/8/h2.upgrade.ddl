-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrades the DB schema from version 7 to version 8.
-- *********************************************************************

UPDATE apicurio SET prop_value = 8 WHERE prop_name = 'db_version';

CREATE TABLE artifactreferences (tenantId VARCHAR(128) NOT NULL, contentId BIGINT NOT NULL, groupId VARCHAR(512), artifactId VARCHAR(512) NOT NULL, version VARCHAR(256), name VARCHAR(512) NOT NULL);
ALTER TABLE artifactreferences ADD PRIMARY KEY (tenantId, contentId, name);

ALTER TABLE content ADD COLUMN artifactreferences TEXT;