-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrades the DB schema from version 3 to version 4.
-- *********************************************************************

UPDATE apicurio SET prop_value = 4 WHERE prop_name = 'db_version';

CREATE TABLE downloads (tenantId VARCHAR(128) NOT NULL, downloadId VARCHAR(128) NOT NULL, expires BIGINT NOT NULL, context VARCHAR(1024));
ALTER TABLE downloads ADD PRIMARY KEY (tenantId, downloadId);
CREATE INDEX IDX_down_1 ON downloads USING HASH (expires);

ALTER TABLE acls ADD COLUMN principalName VARCHAR(256);