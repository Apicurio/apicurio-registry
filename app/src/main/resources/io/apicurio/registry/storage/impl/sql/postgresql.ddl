-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL 10+
-- *********************************************************************

CREATE TABLE apicurio (prop_name VARCHAR(255) NOT NULL, prop_value VARCHAR(255));
ALTER TABLE apicurio ADD PRIMARY KEY (prop_name);
INSERT INTO apicurio (prop_name, prop_value) VALUES ('db_version', 1);

-- TODO: Different column name in h2
CREATE TABLE sequences (name VARCHAR(32) NOT NULL, value BIGINT NOT NULL);
ALTER TABLE sequences ADD PRIMARY KEY (name);

CREATE TABLE globalrules (type VARCHAR(32) NOT NULL, configuration TEXT NOT NULL);
ALTER TABLE globalrules ADD PRIMARY KEY (type);

CREATE TABLE artifacts (groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, type VARCHAR(32) NOT NULL, createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL);
ALTER TABLE artifacts ADD PRIMARY KEY (groupId, artifactId);
CREATE INDEX IDX_artifacts_0 ON artifacts USING HASH (type);
CREATE INDEX IDX_artifacts_1 ON artifacts USING HASH (createdBy);
CREATE INDEX IDX_artifacts_2 ON artifacts(createdOn);

CREATE TABLE rules (groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, type VARCHAR(32) NOT NULL, configuration VARCHAR(1024) NOT NULL);
ALTER TABLE rules ADD PRIMARY KEY (groupId, artifactId, type);

CREATE TABLE content (contentId BIGINT NOT NULL, canonicalHash VARCHAR(64) NOT NULL, contentHash VARCHAR(64) NOT NULL, content BYTEA NOT NULL, artifactreferences TEXT);
ALTER TABLE content ADD PRIMARY KEY (contentId);
ALTER TABLE content ADD CONSTRAINT UQ_content_1 UNIQUE (contentHash);
CREATE INDEX IDX_content_1 ON content USING HASH (canonicalHash);
CREATE INDEX IDX_content_2 ON content USING HASH (contentHash);

-- The "versionOrder" field is needed to generate "version" when it is not provided.
-- It contains the same information as the "branchOrder" in the "latest" branch, but we cannot use it because of a chicken-and-egg problem.
-- At least it is no longer confusingly called "versionId". The "versionOrder" field should not be used for any other purpose.
CREATE TABLE versions (globalId BIGINT NOT NULL, groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, version VARCHAR(256), versionOrder INT NOT NULL, state VARCHAR(64) NOT NULL, name VARCHAR(512), description VARCHAR(1024), createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, labels TEXT, properties TEXT, contentId BIGINT NOT NULL);
ALTER TABLE versions ADD PRIMARY KEY (globalId);
ALTER TABLE versions ADD CONSTRAINT UQ_versions_1 UNIQUE (groupId, artifactId, version);
ALTER TABLE versions ADD CONSTRAINT UQ_versions_2 UNIQUE (globalId, versionOrder);
ALTER TABLE versions ADD CONSTRAINT FK_versions_1 FOREIGN KEY (groupId, artifactId) REFERENCES artifacts(groupId, artifactId);
ALTER TABLE versions ADD CONSTRAINT FK_versions_2 FOREIGN KEY (contentId) REFERENCES content(contentId);
CREATE INDEX IDX_versions_1 ON versions(version);
CREATE INDEX IDX_versions_2 ON versions USING HASH (state);
CREATE INDEX IDX_versions_3 ON versions(name);
CREATE INDEX IDX_versions_4 ON versions(description);
CREATE INDEX IDX_versions_5 ON versions USING HASH (createdBy);
CREATE INDEX IDX_versions_6 ON versions(createdOn);
CREATE INDEX IDX_versions_7 ON versions USING HASH (contentId);

CREATE TABLE artifact_version_branches (groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, branch VARCHAR(256) NOT NULL, branchOrder INT NOT NULL, version VARCHAR(256) NOT NULL);
ALTER TABLE artifact_version_branches ADD PRIMARY KEY (groupId, artifactId, branch, branchOrder);
ALTER TABLE artifact_version_branches ADD CONSTRAINT FK_artifact_version_branches_1 FOREIGN KEY (groupId, artifactId, version) REFERENCES versions(groupId, artifactId, version);
CREATE INDEX IDX_artifact_version_branches_1 ON artifact_version_branches(groupId, artifactId, branch, branchOrder);

CREATE TABLE properties (globalId BIGINT NOT NULL, pkey VARCHAR(256) NOT NULL, pvalue VARCHAR(1024));
ALTER TABLE properties ADD CONSTRAINT FK_props_1 FOREIGN KEY (globalId) REFERENCES versions(globalId);
CREATE INDEX IDX_props_1 ON properties(pkey);
CREATE INDEX IDX_props_2 ON properties(pvalue);

CREATE TABLE labels (globalId BIGINT NOT NULL, label VARCHAR(256) NOT NULL);
ALTER TABLE labels ADD CONSTRAINT FK_labels_1 FOREIGN KEY (globalId) REFERENCES versions(globalId);
CREATE INDEX IDX_labels_1 ON labels(label);

CREATE TABLE comments (commentId VARCHAR(128) NOT NULL, globalId BIGINT NOT NULL, createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, cvalue VARCHAR(1024) NOT NULL);
ALTER TABLE comments ADD PRIMARY KEY (commentId);
ALTER TABLE comments ADD CONSTRAINT FK_comments_1 FOREIGN KEY (globalId) REFERENCES versions(globalId);
CREATE INDEX IDX_comments_1 ON comments(createdBy);

CREATE TABLE groups (groupId VARCHAR(512) NOT NULL, description VARCHAR(1024), artifactsType VARCHAR(32), createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, modifiedBy VARCHAR(256), modifiedOn TIMESTAMP WITHOUT TIME ZONE, properties TEXT);
ALTER TABLE groups ADD PRIMARY KEY (groupId);

CREATE TABLE acls (principalId VARCHAR(256) NOT NULL, role VARCHAR(32) NOT NULL, principalName VARCHAR(256));
ALTER TABLE acls ADD PRIMARY KEY (principalId);

CREATE TABLE downloads (downloadId VARCHAR(128) NOT NULL, expires BIGINT NOT NULL, context VARCHAR(1024));
ALTER TABLE downloads ADD PRIMARY KEY (downloadId);
CREATE INDEX IDX_down_1 ON downloads USING HASH (expires);

-- TODO: Missing NOT NULL in h2
CREATE TABLE config (pname VARCHAR(255) NOT NULL, pvalue VARCHAR(1024) NOT NULL, modifiedOn BIGINT NOT NULL);
ALTER TABLE config ADD PRIMARY KEY (pname);
CREATE INDEX IDX_config_1 ON config(modifiedOn);

CREATE TABLE artifactreferences (contentId BIGINT NOT NULL, groupId VARCHAR(512), artifactId VARCHAR(512) NOT NULL, version VARCHAR(256), name VARCHAR(512) NOT NULL);
ALTER TABLE artifactreferences ADD PRIMARY KEY (contentId, name);
ALTER TABLE artifactreferences ADD CONSTRAINT FK_artifactreferences_1 FOREIGN KEY (contentId) REFERENCES content(contentId) ON DELETE CASCADE;
