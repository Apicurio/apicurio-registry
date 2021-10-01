-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrades the DB schema from version 2 to version 3.
-- *********************************************************************

UPDATE apicurio SET prop_value = 3 WHERE prop_name = 'db_version';

-- create new table
CREATE TABLE tenant_content (tenantId VARCHAR(128) NOT NULL, contentId BIGINT NOT NULL, contentHash VARCHAR(64) NOT NULL);
ALTER TABLE tenant_content ADD PRIMARY KEY (tenantId, contentId);
ALTER TABLE tenant_content ADD CONSTRAINT FK_tenant_content_1 FOREIGN KEY (contentHash) REFERENCES content(contentHash);
-- CREATE INDEX IDX_tenant_content_1 ON tenant_content USING HASH (contentHash); Is this needed? When will we search by contentHash in this table?

-- migrate data to new table
INSERT INTO tenant_content (tenantId, contentId, contentHash) 
SELECT v.tenantId, v.contentId, c.contentHash FROM versions v , content c WHERE v.contentId = c.contentId;

-- remove primary key, we are guessing the name the constraint will have
ALTER TABLE content DROP CONSTRAINT content_pkey;
-- remove no longer needed constraints related to contentHash, contentHash will be primary key
ALTER TABLE content DROP CONSTRAINT UNQ_content_1;
ALTER TABLE content DROP INDEX IDX_content_2;

-- change contentId constraint versions table
ALTER TABLE versions DROP CONSTRAINT FK_versions_2;
ALTER TABLE versions ADD CONSTRAINT FK_versions_2 FOREIGN KEY (tenantId, contentId) REFERENCES tenant_content(tenantId, contentId);

-- drop contentId column and add new primary key
ALTER TABLE content DROP COLUMN contentId;
ALTER TABLE content ADD PRIMARY KEY (contentHash);

-- change versions table primaryKey to support multitenancy, we are guessing the primarykey constraint name
ALTER TABLE versions DROP CONSTRAINT versions_pkey;
ALTER TABLE versions ADD PRIMARY KEY (tenantId, globalId);

CREATE TABLE tenantglobalid (tenantId VARCHAR(128) NOT NULL, globalId BIGINT NOT NULL);
ALTER TABLE tenantglobalid ADD PRIMARY KEY (tenantId, globalId);
