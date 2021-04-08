-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL 10+
-- *********************************************************************

CREATE TABLE apicurio (prop_name VARCHAR(255) NOT NULL, prop_value VARCHAR(255));
ALTER TABLE apicurio ADD PRIMARY KEY (prop_name);
INSERT INTO apicurio (prop_name, prop_value) VALUES ('db_version', 1);

CREATE SEQUENCE contentidsequence INCREMENT BY 1 NO MINVALUE;
CREATE SEQUENCE globalidsequence INCREMENT BY 1 NO MINVALUE;

CREATE TABLE globalrules (tenantId VARCHAR(128) NOT NULL, type VARCHAR(32) NOT NULL, configuration TEXT NOT NULL);
ALTER TABLE globalrules ADD PRIMARY KEY (tenantId, type);

CREATE TABLE artifacts (tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, type VARCHAR(32) NOT NULL, createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, latest BIGINT);
ALTER TABLE artifacts ADD PRIMARY KEY (tenantId, groupId, artifactId);
CREATE INDEX IDX_artifacts_0 ON artifacts USING HASH (type);
CREATE INDEX IDX_artifacts_1 ON artifacts USING HASH (createdBy);
CREATE INDEX IDX_artifacts_2 ON artifacts(createdOn);

CREATE TABLE rules (tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, type VARCHAR(32) NOT NULL, configuration VARCHAR(1024) NOT NULL);
ALTER TABLE rules ADD PRIMARY KEY (tenantId, groupId, artifactId, type);
ALTER TABLE rules ADD CONSTRAINT FK_rules_1 FOREIGN KEY (tenantId, groupId, artifactId) REFERENCES artifacts(tenantId, groupId, artifactId);

CREATE TABLE content (contentId BIGINT NOT NULL, canonicalHash VARCHAR(64) NOT NULL, contentHash VARCHAR(64) NOT NULL, content BYTEA NOT NULL);
ALTER TABLE content ADD PRIMARY KEY (contentId);
ALTER TABLE content ADD CONSTRAINT UNQ_content_1 UNIQUE (contentHash);
CREATE INDEX IDX_content_1 ON content USING HASH (canonicalHash);
CREATE INDEX IDX_content_2 ON content USING HASH (contentHash);

CREATE TABLE versions (globalId BIGINT NOT NULL, tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, version VARCHAR(256), versionId INT NOT NULL, state VARCHAR(64) NOT NULL, name VARCHAR(512), description VARCHAR(1024), createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, labels TEXT, properties TEXT, contentId BIGINT NOT NULL);
ALTER TABLE versions ADD PRIMARY KEY (globalId);
ALTER TABLE versions ADD CONSTRAINT UQ_versions_1 UNIQUE (tenantId, groupId, artifactId, version);
ALTER TABLE versions ADD CONSTRAINT FK_versions_1 FOREIGN KEY (tenantId, groupId, artifactId) REFERENCES artifacts(tenantId, groupId, artifactId);
ALTER TABLE versions ADD CONSTRAINT FK_versions_2 FOREIGN KEY (contentId) REFERENCES content(contentId);
CREATE INDEX IDX_versions_1 ON versions(version);
CREATE INDEX IDX_versions_2 ON versions USING HASH (state);
CREATE INDEX IDX_versions_3 ON versions(name);
CREATE INDEX IDX_versions_4 ON versions(description);
CREATE INDEX IDX_versions_5 ON versions USING HASH (createdBy);
CREATE INDEX IDX_versions_6 ON versions(createdOn);
CREATE INDEX IDX_versions_7 ON versions USING HASH (contentId);

CREATE TABLE properties (globalId BIGINT NOT NULL, pkey VARCHAR(256) NOT NULL, pvalue VARCHAR(1024));
ALTER TABLE properties ADD CONSTRAINT FK_props_1 FOREIGN KEY (globalId) REFERENCES versions(globalId);
CREATE INDEX IDX_props_1 ON properties(pkey);
CREATE INDEX IDX_props_2 ON properties(pvalue);

CREATE TABLE labels (globalId BIGINT NOT NULL, label VARCHAR(256) NOT NULL);
ALTER TABLE labels ADD CONSTRAINT FK_labels_1 FOREIGN KEY (globalId) REFERENCES versions(globalId);
CREATE INDEX IDX_labels_1 ON labels(label);

CREATE TABLE tenants (tenantId VARCHAR(128) NOT NULL, authClientId VARCHAR(255) NOT NULL, authServerUrl VARCHAR(255), createdBy VARCHAR(255), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, deploymentFlavor VARCHAR(255), organizationId VARCHAR(255) NOT NULL, status VARCHAR(255) );
ALTER TABLE tenants ADD PRIMARY KEY (tenantId);

CREATE TABLE logconfiguration (logger VARCHAR(512) NOT NULL, loglevel VARCHAR(32) NOT NULL);
ALTER TABLE logconfiguration ADD PRIMARY KEY (logger);

CREATE TABLE groups (tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(512) NOT NULL, description VARCHAR(1024), artifactsType VARCHAR(32), createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, modifiedBy VARCHAR(256), modifiedOn TIMESTAMP WITHOUT TIME ZONE, properties TEXT);
ALTER TABLE groups ADD PRIMARY KEY (tenantId, groupId);
