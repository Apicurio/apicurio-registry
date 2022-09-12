-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL 10+
-- *********************************************************************

CREATE TABLE apicurio (prop_name VARCHAR(255) NOT NULL, prop_value VARCHAR(255));
ALTER TABLE apicurio ADD PRIMARY KEY (prop_name);
INSERT INTO apicurio (prop_name, prop_value) VALUES ('db_version', 11);

CREATE TABLE sequences (tenantId VARCHAR(128) NOT NULL, name VARCHAR(32) NOT NULL, value BIGINT NOT NULL);
ALTER TABLE sequences ADD PRIMARY KEY (tenantId, name);

CREATE TABLE globalrules (tenantId VARCHAR(128) NOT NULL, type VARCHAR(32) NOT NULL, configuration TEXT NOT NULL);
ALTER TABLE globalrules ADD PRIMARY KEY (tenantId, type);

CREATE TABLE artifacts (tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, type VARCHAR(32) NOT NULL, createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, latest BIGINT);
ALTER TABLE artifacts ADD PRIMARY KEY (tenantId, groupId, artifactId);
CREATE INDEX IDX_artifacts_0 ON artifacts USING HASH (type);
CREATE INDEX IDX_artifacts_1 ON artifacts USING HASH (createdBy);
CREATE INDEX IDX_artifacts_2 ON artifacts(createdOn);

CREATE TABLE rules (tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, type VARCHAR(32) NOT NULL, configuration VARCHAR(1024) NOT NULL);
ALTER TABLE rules ADD PRIMARY KEY (tenantId, groupId, artifactId, type);

CREATE TABLE content (tenantId VARCHAR(128) NOT NULL, contentId BIGINT NOT NULL, canonicalHash VARCHAR(64) NOT NULL, contentHash VARCHAR(64) NOT NULL, content BYTEA NOT NULL, artifactreferences TEXT);
ALTER TABLE content ADD PRIMARY KEY (tenantId, contentId);
ALTER TABLE content ADD CONSTRAINT UNQ_content_1 UNIQUE (tenantId, contentHash);
CREATE INDEX IDX_content_1 ON content USING HASH (canonicalHash);
CREATE INDEX IDX_content_2 ON content USING HASH (contentHash);

CREATE TABLE versions (globalId BIGINT NOT NULL, tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, version VARCHAR(256), versionId INT NOT NULL, state VARCHAR(64) NOT NULL, name VARCHAR(512), description VARCHAR(1024), createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, labels TEXT, properties TEXT, contentId BIGINT NOT NULL);
ALTER TABLE versions ADD PRIMARY KEY (tenantId, globalId);
ALTER TABLE versions ADD CONSTRAINT UQ_versions_1 UNIQUE (tenantId, groupId, artifactId, version);
ALTER TABLE versions ADD CONSTRAINT FK_versions_1 FOREIGN KEY (tenantId, groupId, artifactId) REFERENCES artifacts(tenantId, groupId, artifactId);
ALTER TABLE versions ADD CONSTRAINT FK_versions_2 FOREIGN KEY (tenantId, contentId) REFERENCES content(tenantId, contentId);
CREATE INDEX IDX_versions_1 ON versions(version);
CREATE INDEX IDX_versions_2 ON versions USING HASH (state);
CREATE INDEX IDX_versions_3 ON versions(name);
CREATE INDEX IDX_versions_4 ON versions(description);
CREATE INDEX IDX_versions_5 ON versions USING HASH (createdBy);
CREATE INDEX IDX_versions_6 ON versions(createdOn);
CREATE INDEX IDX_versions_7 ON versions USING HASH (contentId);

CREATE TABLE properties (tenantId VARCHAR(128) NOT NULL, globalId BIGINT NOT NULL, pkey VARCHAR(256) NOT NULL, pvalue VARCHAR(1024));
ALTER TABLE properties ADD CONSTRAINT FK_props_1 FOREIGN KEY (tenantId, globalId) REFERENCES versions(tenantId, globalId);
CREATE INDEX IDX_props_1 ON properties(pkey);
CREATE INDEX IDX_props_2 ON properties(pvalue);

CREATE TABLE labels (tenantId VARCHAR(128) NOT NULL, globalId BIGINT NOT NULL, label VARCHAR(256) NOT NULL);
ALTER TABLE labels ADD CONSTRAINT FK_labels_1 FOREIGN KEY (tenantId, globalId) REFERENCES versions(tenantId, globalId);
CREATE INDEX IDX_labels_1 ON labels(label);

CREATE TABLE logconfiguration (logger VARCHAR(512) NOT NULL, loglevel VARCHAR(32) NOT NULL);
ALTER TABLE logconfiguration ADD PRIMARY KEY (logger);

CREATE TABLE groups (tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(512) NOT NULL, description VARCHAR(1024), artifactsType VARCHAR(32), createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, modifiedBy VARCHAR(256), modifiedOn TIMESTAMP WITHOUT TIME ZONE, properties TEXT);
ALTER TABLE groups ADD PRIMARY KEY (tenantId, groupId);

CREATE TABLE acls (tenantId VARCHAR(128) NOT NULL, principalId VARCHAR(256) NOT NULL, role VARCHAR(32) NOT NULL, principalName VARCHAR(256));
ALTER TABLE acls ADD PRIMARY KEY (tenantId, principalId);

CREATE TABLE downloads (tenantId VARCHAR(128) NOT NULL, downloadId VARCHAR(128) NOT NULL, expires BIGINT NOT NULL, context VARCHAR(1024));
ALTER TABLE downloads ADD PRIMARY KEY (tenantId, downloadId);
CREATE INDEX IDX_down_1 ON downloads USING HASH (expires);

CREATE TABLE config (tenantId VARCHAR(128) NOT NULL, pname VARCHAR(255) NOT NULL, pvalue VARCHAR(1024) NOT NULL, modifiedOn BIGINT NOT NULL);
ALTER TABLE config ADD PRIMARY KEY (tenantId, pname);
CREATE INDEX IDX_config_1 ON config(modifiedOn);

CREATE TABLE artifactreferences (tenantId VARCHAR(128) NOT NULL, contentId BIGINT NOT NULL, groupId VARCHAR(512), artifactId VARCHAR(512) NOT NULL, version VARCHAR(256), name VARCHAR(512) NOT NULL);
ALTER TABLE artifactreferences ADD PRIMARY KEY (tenantId, contentId, name);
ALTER TABLE artifactreferences ADD CONSTRAINT FK_artifactreferences_1 FOREIGN KEY (tenantId, contentId) REFERENCES content(tenantId, contentId) ON DELETE CASCADE;

