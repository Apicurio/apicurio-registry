-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrades the DB schema from version 2 to version 3.
-- *********************************************************************

UPDATE apicurio SET prop_value = 3 WHERE prop_name = 'db_version';

-- create sequences table
CREATE TABLE sequences (tenantId VARCHAR(128) NOT NULL, name VARCHAR(32) NOT NULL, value BIGINT NOT NULL);
ALTER TABLE sequences ADD PRIMARY KEY (tenantId, name);

-- remove old sequences
DROP SEQUENCE contentidsequence;
DROP SEQUENCE globalidsequence;

-- create new table as backup
CREATE TABLE contentV3 (tenantId VARCHAR(128) NOT NULL, contentId BIGINT NOT NULL, canonicalHash VARCHAR(64) NOT NULL, contentHash VARCHAR(64) NOT NULL, content BYTEA NOT NULL);
ALTER TABLE contentV3 ADD PRIMARY KEY (tenantId, contentId);
ALTER TABLE contentV3 ADD CONSTRAINT UNQ_content_1 UNIQUE (tenantId, contentHash);
CREATE INDEX IDX_content_1 ON contentV3 USING HASH (canonicalHash);
CREATE INDEX IDX_content_2 ON contentV3 USING HASH (contentHash);

-- migrate data to backup table
INSERT INTO contentV3 (tenantId, contentId, canonicalHash, contentHash, content)
SELECT v.tenantId, v.contentId, c.canonicalHash, c.contentHash, c.content FROM versions v , content c WHERE v.contentId = c.contentId;

-- drop old content table
DROP TABLE content;

-- rename backup table
ALTER TABLE contentV3 RENAME TO content;

-- change contentId constraint versions table
ALTER TABLE versions DROP CONSTRAINT FK_versions_2;
ALTER TABLE versions ADD CONSTRAINT FK_versions_2 FOREIGN KEY (tenantId, contentId) REFERENCES content(tenantId, contentId);

-- change versions table primaryKey to support multitenancy, we are guessing the primarykey constraint name
ALTER TABLE versions DROP CONSTRAINT versions_pkey;
ALTER TABLE versions ADD PRIMARY KEY (tenantId, globalId);
