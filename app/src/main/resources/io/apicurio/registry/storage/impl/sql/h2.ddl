-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- *********************************************************************

CREATE TABLE apicurio (prop_name VARCHAR(255) NOT NULL, prop_value VARCHAR(255));
ALTER TABLE apicurio ADD PRIMARY KEY (prop_name);
INSERT INTO apicurio (prop_name, prop_value) VALUES ('db_version', 1);

CREATE TABLE sequences (name VARCHAR(32) NOT NULL, seq_value BIGINT NOT NULL);
ALTER TABLE sequences ADD PRIMARY KEY (name);

CREATE TABLE globalrules (type VARCHAR(32) NOT NULL, configuration TEXT NOT NULL);
ALTER TABLE globalrules ADD PRIMARY KEY (type);

CREATE TABLE artifacts (groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, type VARCHAR(32) NOT NULL, createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, labels TEXT);
ALTER TABLE artifacts ADD PRIMARY KEY (groupId, artifactId);
CREATE HASH INDEX IDX_artifacts_0 ON artifacts(type);
CREATE HASH INDEX IDX_artifacts_1 ON artifacts(createdBy);
CREATE INDEX IDX_artifacts_2 ON artifacts(createdOn);

CREATE TABLE artifact_labels (groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, pkey VARCHAR(256) NOT NULL, pvalue VARCHAR(1024));
ALTER TABLE artifact_labels ADD CONSTRAINT FK_alabels_1 FOREIGN KEY (groupId, artifactId) REFERENCES artifacts(groupId, artifactId);
CREATE INDEX IDX_alabels_1 ON artifact_labels(pkey);
CREATE INDEX IDX_alabels_2 ON artifact_labels(pvalue);

CREATE TABLE rules (groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, type VARCHAR(32) NOT NULL, configuration VARCHAR(1024) NOT NULL);
ALTER TABLE rules ADD PRIMARY KEY (groupId, artifactId, type);

CREATE TABLE content (contentId BIGINT NOT NULL, canonicalHash VARCHAR(64) NOT NULL, contentHash VARCHAR(64) NOT NULL, content BYTEA NOT NULL, artifactreferences TEXT);
ALTER TABLE content ADD PRIMARY KEY (contentId);
ALTER TABLE content ADD CONSTRAINT UQ_content_1 UNIQUE (contentHash);
CREATE HASH INDEX IDX_content_1 ON content(canonicalHash);
CREATE HASH INDEX IDX_content_2 ON content(contentHash);

-- The "versionOrder" field is needed to generate "version" when it is not provided.
-- It contains the same information as the "branchOrder" in the "latest" branch, but we cannot use it because of a chicken-and-egg problem.
-- At least it is no longer confusingly called "versionId". The "versionOrder" field should not be used for any other purpose.
CREATE TABLE versions (globalId BIGINT NOT NULL, groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, version VARCHAR(256), versionOrder INT NOT NULL, state VARCHAR(64) NOT NULL, name VARCHAR(512), description VARCHAR(1024), createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, labels TEXT, contentId BIGINT NOT NULL);
ALTER TABLE versions ADD PRIMARY KEY (globalId);
ALTER TABLE versions ADD CONSTRAINT UQ_versions_1 UNIQUE (groupId, artifactId, version);
ALTER TABLE versions ADD CONSTRAINT UQ_versions_2 UNIQUE (globalId, versionOrder);
ALTER TABLE versions ADD CONSTRAINT FK_versions_1 FOREIGN KEY (groupId, artifactId) REFERENCES artifacts(groupId, artifactId);
ALTER TABLE versions ADD CONSTRAINT FK_versions_2 FOREIGN KEY (contentId) REFERENCES content(contentId);
CREATE INDEX IDX_versions_1 ON versions(version);
CREATE HASH INDEX IDX_versions_2 ON versions(state);
CREATE INDEX IDX_versions_3 ON versions(name);
CREATE INDEX IDX_versions_4 ON versions(description);
CREATE HASH INDEX IDX_versions_5 ON versions(createdBy);
CREATE INDEX IDX_versions_6 ON versions(createdOn);
CREATE HASH INDEX IDX_versions_7 ON versions(contentId);

CREATE TABLE artifact_branches (groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, branchId VARCHAR(256) NOT NULL, branchOrder INT NOT NULL, version VARCHAR(256) NOT NULL);
ALTER TABLE artifact_branches ADD PRIMARY KEY (groupId, artifactId, branchId, branchOrder);
ALTER TABLE artifact_branches ADD CONSTRAINT FK_artifact_branches_1 FOREIGN KEY (groupId, artifactId, version) REFERENCES versions(groupId, artifactId, version);
CREATE INDEX IDX_artifact_branches_1 ON artifact_branches(groupId, artifactId, branchId, branchOrder);

CREATE TABLE version_labels (globalId BIGINT NOT NULL, pkey VARCHAR(256) NOT NULL, pvalue VARCHAR(1024));
ALTER TABLE version_labels ADD CONSTRAINT FK_vlabels_1 FOREIGN KEY (globalId) REFERENCES versions(globalId);
CREATE INDEX IDX_vlabels_1 ON version_labels(pkey);
CREATE INDEX IDX_vlabels_2 ON version_labels(pvalue);

CREATE TABLE comments (commentId VARCHAR(128) NOT NULL, globalId BIGINT NOT NULL, createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, cvalue VARCHAR(1024) NOT NULL);
ALTER TABLE comments ADD PRIMARY KEY (commentId);
ALTER TABLE comments ADD CONSTRAINT FK_comments_1 FOREIGN KEY (globalId) REFERENCES versions(globalId);
CREATE INDEX IDX_comments_1 ON comments(createdBy);

CREATE TABLE groups (groupId VARCHAR(512) NOT NULL, description VARCHAR(1024), artifactsType VARCHAR(32), createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, modifiedBy VARCHAR(256), modifiedOn TIMESTAMP WITHOUT TIME ZONE, labels TEXT);
ALTER TABLE groups ADD PRIMARY KEY (groupId);

CREATE TABLE group_labels (groupId VARCHAR(512) NOT NULL, pkey VARCHAR(256) NOT NULL, pvalue VARCHAR(1024));
ALTER TABLE group_labels ADD CONSTRAINT FK_glabels_1 FOREIGN KEY (groupId) REFERENCES groups(groupId);
CREATE INDEX IDX_glabels_1 ON group_labels(pkey);
CREATE INDEX IDX_glabels_2 ON group_labels(pvalue);

CREATE TABLE acls (principalId VARCHAR(256) NOT NULL, role VARCHAR(32) NOT NULL, principalName VARCHAR(256));
ALTER TABLE acls ADD PRIMARY KEY (principalId);

CREATE TABLE downloads (downloadId VARCHAR(128) NOT NULL, expires BIGINT NOT NULL, context VARCHAR(1024));
ALTER TABLE downloads ADD PRIMARY KEY (downloadId);
CREATE HASH INDEX IDX_down_1 ON downloads(expires);

CREATE TABLE config (pname VARCHAR(255) NOT NULL, pvalue VARCHAR(1024), modifiedOn BIGINT NOT NULL);
ALTER TABLE config ADD PRIMARY KEY (pname);
CREATE INDEX IDX_config_1 ON config(modifiedOn);

CREATE TABLE artifactreferences (contentId BIGINT NOT NULL, groupId VARCHAR(512), artifactId VARCHAR(512) NOT NULL, version VARCHAR(256), name VARCHAR(512) NOT NULL);
ALTER TABLE artifactreferences ADD PRIMARY KEY (contentId, name);
ALTER TABLE artifactreferences ADD CONSTRAINT FK_artifactreferences_1 FOREIGN KEY (contentId) REFERENCES content(contentId) ON DELETE CASCADE;
