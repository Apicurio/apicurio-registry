-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrades the DB schema from version 1 to version 2.
-- *********************************************************************

UPDATE apicurio SET prop_value = 2 WHERE prop_name = 'db_version';

CREATE TABLE acls (tenantId VARCHAR(128) NOT NULL, principalId VARCHAR(256) NOT NULL, role VARCHAR(32) NOT NULL);
ALTER TABLE acls ADD PRIMARY KEY (tenantId, principalId);
