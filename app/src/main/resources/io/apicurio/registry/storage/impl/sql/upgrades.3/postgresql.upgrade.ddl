-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrades the DB schema from version 2 to version 3.
-- *********************************************************************

-- Update database version to 3
UPDATE apicurio SET prop_value = 3 WHERE prop_name = 'db_version';

-- new sequences table
CREATE TABLE sequences(tenantid character varying(128) NOT NULL, name character varying(32) NOT NULL, nextval bigint NOT NULL);
ALTER TABLE sequences ADD PRIMARY KEY (tenantid, name);

-- TODO: DELETE current sequences (after migration)

-- new tenant_content table
CREATE TABLE tenant_content (tenantid VARCHAR(128) NOT NULL, contentid BIGINT NOT NULL, canonicalhash VARCHAR(64) NOT NULL, contenthash VARCHAR(64) NOT NULL);
ALTER TABLE tenant_content ADD PRIMARY KEY (tenantid, contentid);
ALTER TABLE tenant_content ADD CONSTRAINT FK_tenant_content_1 FOREIGN KEY (contenthash) REFERENCES content(contenthash);
CREATE INDEX IDX_tenant_content_1 ON content USING HASH (canonicalhash);
CREATE INDEX IDX_tenant_content_2 ON tenant_content USING HASH (contenthash);

-- TODO: ContentId into tenant_content - What will be the tenantId?

-- content table
ALTER TABLE content DROP CONSTRAINT primary_key_constraint;
ALTER TABLE content DROP CONSTRAINT UNQ_content_1;
DROP INDEX IDX_content_1
DROP INDEX IDX_content_2
ALTER TABLE content DROP COLUMN contentId;
ALTER TABLE content DROP COLUMN canonicalHash;
ALTER TABLE content ADD PRIMARY KEY (contentHash);

-- versions table
ALTER TABLE versions DROP CONSTRAINT primary_key_constraint;
ALTER TABLE versions ADD PRIMARY KEY (globalId, tenantId);
ALTER TABLE content DROP CONSTRAINT FK_versions_2;
ALTER TABLE versions ADD CONSTRAINT FK_versions_2 FOREIGN KEY (tenantid, contentid) REFERENCES tenant_content(tenantid, contentid);