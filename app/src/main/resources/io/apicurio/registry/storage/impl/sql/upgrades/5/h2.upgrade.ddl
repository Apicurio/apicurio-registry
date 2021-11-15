-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrades the DB schema from version 4 to version 5.
-- *********************************************************************

UPDATE apicurio SET prop_value = 5 WHERE prop_name = 'db_version';

CREATE TABLE artifactreferences (tenantId VARCHAR(128) NOT NULL, globalId BIGINT NOT NULL, contentId BIGINT NOT NULL, name VARCHAR(512) NOT NULL);
ALTER TABLE artifactreferences ADD PRIMARY KEY (tenantId, name, globalId);

ALTER TABLE content ADD COLUMN artifactreferences VARCHAR(512);