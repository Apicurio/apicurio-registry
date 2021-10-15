-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrades the DB schema from version 6 to version 7.
-- *********************************************************************

UPDATE apicurio SET prop_value = 7 WHERE prop_name = 'db_version';

CREATE TABLE references (tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, globalId INT NOT NULL, name VARCHAR(512) NOT NULL);
ALTER TABLE references ADD PRIMARY KEY (name, tenantId);

ALTER TABLE versions ADD COLUMN references VARCHAR(512);
