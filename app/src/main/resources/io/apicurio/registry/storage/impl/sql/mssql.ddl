-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MS SQL Server
-- *********************************************************************

CREATE TABLE apicurio (prop_name NVARCHAR(255) NOT NULL, prop_value NVARCHAR(255));
ALTER TABLE apicurio ADD PRIMARY KEY (prop_name);
INSERT INTO apicurio (prop_name, prop_value) VALUES ('db_version', 100);

CREATE TABLE sequences (seq_name NVARCHAR(32) NOT NULL, seq_value BIGINT NOT NULL);
ALTER TABLE sequences ADD PRIMARY KEY (seq_name);

CREATE TABLE config (pname NVARCHAR(255) NOT NULL, pvalue NVARCHAR(1024) NOT NULL, modifiedOn BIGINT NOT NULL);
ALTER TABLE config ADD PRIMARY KEY (pname);
CREATE INDEX IDX_config_1 ON config(modifiedOn);

CREATE TABLE acls (principalId NVARCHAR(256) NOT NULL, role NVARCHAR(32) NOT NULL, principalName NVARCHAR(256));
ALTER TABLE acls ADD PRIMARY KEY (principalId);

CREATE TABLE downloads (downloadId NVARCHAR(128) NOT NULL, expires BIGINT NOT NULL, context NVARCHAR(1024));
ALTER TABLE downloads ADD PRIMARY KEY (downloadId);
CREATE INDEX IDX_down_1 ON downloads(expires);

CREATE TABLE global_rules (type NVARCHAR(32) NOT NULL, configuration TEXT NOT NULL);
ALTER TABLE global_rules ADD PRIMARY KEY (type);

CREATE TABLE content (contentId BIGINT NOT NULL, canonicalHash NVARCHAR(64) NOT NULL, contentHash NVARCHAR(64) NOT NULL, content VARBINARY(MAX) NOT NULL, refs TEXT);
ALTER TABLE content ADD PRIMARY KEY (contentId);
ALTER TABLE content ADD CONSTRAINT UQ_content_1 UNIQUE (contentHash);
CREATE INDEX IDX_content_1 ON content(canonicalHash);
CREATE INDEX IDX_content_2 ON content(contentHash);

CREATE TABLE content_references (contentId BIGINT NOT NULL, groupId NVARCHAR(512), artifactId NVARCHAR(512) NOT NULL, version NVARCHAR(256), name NVARCHAR(512) NOT NULL);
ALTER TABLE content_references ADD PRIMARY KEY (contentId, name);
ALTER TABLE content_references ADD CONSTRAINT FK_content_references_1 FOREIGN KEY (contentId) REFERENCES content(contentId) ON DELETE CASCADE;

CREATE TABLE groups (groupId NVARCHAR(512) NOT NULL, description NVARCHAR(1024), artifactsType NVARCHAR(32), createdBy NVARCHAR(256), createdOn DATETIME2(6) NOT NULL, modifiedBy NVARCHAR(256), modifiedOn DATETIME2(6), labels TEXT);
ALTER TABLE groups ADD PRIMARY KEY (groupId);

CREATE TABLE group_labels (groupId NVARCHAR(512) NOT NULL, pkey NVARCHAR(256) NOT NULL, pvalue NVARCHAR(512));
ALTER TABLE group_labels ADD CONSTRAINT FK_glabels_1 FOREIGN KEY (groupId) REFERENCES groups(groupId) ON DELETE CASCADE;
CREATE INDEX IDX_glabels_1 ON group_labels(pkey);
CREATE INDEX IDX_glabels_2 ON group_labels(pvalue);

CREATE TABLE group_rules (groupId NVARCHAR(512) NOT NULL, type NVARCHAR(32) NOT NULL, configuration NVARCHAR(1024) NOT NULL);
ALTER TABLE group_rules ADD PRIMARY KEY (groupId, type);
ALTER TABLE group_rules ADD CONSTRAINT FK_grules_1 FOREIGN KEY (groupId) REFERENCES groups(groupId) ON DELETE CASCADE;

CREATE TABLE artifacts (groupId NVARCHAR(512) NOT NULL, artifactId NVARCHAR(512) NOT NULL, type NVARCHAR(32) NOT NULL, createdBy NVARCHAR(256), createdOn DATETIME2(6) NOT NULL, labels TEXT);
ALTER TABLE artifacts ADD PRIMARY KEY (groupId, artifactId);
CREATE INDEX IDX_artifacts_0 ON artifacts(type);
CREATE INDEX IDX_artifacts_1 ON artifacts(createdBy);
CREATE INDEX IDX_artifacts_2 ON artifacts(createdOn);

CREATE TABLE artifact_labels (groupId NVARCHAR(512) NOT NULL, artifactId NVARCHAR(512) NOT NULL, pkey NVARCHAR(256) NOT NULL, pvalue NVARCHAR(512));
ALTER TABLE artifact_labels ADD CONSTRAINT FK_alabels_1 FOREIGN KEY (groupId, artifactId) REFERENCES artifacts(groupId, artifactId) ON DELETE CASCADE;
CREATE INDEX IDX_alabels_1 ON artifact_labels(pkey);
CREATE INDEX IDX_alabels_2 ON artifact_labels(pvalue);

CREATE TABLE artifact_rules (groupId NVARCHAR(512) NOT NULL, artifactId NVARCHAR(512) NOT NULL, type NVARCHAR(32) NOT NULL, configuration NVARCHAR(1024) NOT NULL);
ALTER TABLE artifact_rules ADD PRIMARY KEY (groupId, artifactId, type);
-- Note: no FK constraint between artifact_rules and artifacts because the Confluent API allows
--       rules to be configured for Artifacts that do not yet exist.

-- The "versionOrder" field is needed to generate "version" when it is not provided.
-- It contains the same information as the "branchOrder" in the "latest" branch, but we cannot use it because of a chicken-and-egg problem.
-- At least it is no longer confusingly called "versionId". The "versionOrder" field should not be used for any other purpose.
CREATE TABLE versions (globalId BIGINT NOT NULL, groupId NVARCHAR(512) NOT NULL, artifactId NVARCHAR(512) NOT NULL, version NVARCHAR(256), versionOrder INT NOT NULL, state NVARCHAR(64) NOT NULL, name NVARCHAR(512), description NVARCHAR(1024), createdBy NVARCHAR(256), createdOn DATETIME2(6) NOT NULL, labels TEXT, contentId BIGINT NOT NULL);
ALTER TABLE versions ADD PRIMARY KEY (globalId);
ALTER TABLE versions ADD CONSTRAINT UQ_versions_1 UNIQUE (groupId, artifactId, version);
ALTER TABLE versions ADD CONSTRAINT UQ_versions_2 UNIQUE (globalId, versionOrder);
ALTER TABLE versions ADD CONSTRAINT FK_versions_1 FOREIGN KEY (groupId, artifactId) REFERENCES artifacts(groupId, artifactId) ON DELETE CASCADE;
ALTER TABLE versions ADD CONSTRAINT FK_versions_2 FOREIGN KEY (contentId) REFERENCES content(contentId);
CREATE INDEX IDX_versions_1 ON versions(version);
CREATE INDEX IDX_versions_2 ON versions(state);
CREATE INDEX IDX_versions_3 ON versions(name);
-- CREATE INDEX IDX_versions_4 ON versions(description); TODO: Why commented out? Maybe the same as IDX_props_2?
CREATE INDEX IDX_versions_5 ON versions(createdBy);
CREATE INDEX IDX_versions_6 ON versions(createdOn);
CREATE INDEX IDX_versions_7 ON versions(contentId);

CREATE TABLE version_labels (globalId BIGINT NOT NULL, pkey NVARCHAR(256) NOT NULL, pvalue NVARCHAR(512));
ALTER TABLE version_labels ADD CONSTRAINT FK_vlabels_1 FOREIGN KEY (globalId) REFERENCES versions(globalId) ON DELETE CASCADE;
CREATE INDEX IDX_vlabels_1 ON version_labels(pkey);
CREATE INDEX IDX_vlabels_2 ON version_labels(pvalue);

CREATE TABLE version_comments (commentId NVARCHAR(128) NOT NULL, globalId BIGINT NOT NULL, createdBy NVARCHAR(256), createdOn DATETIME2(6) NOT NULL, cvalue NVARCHAR(1024) NOT NULL);
ALTER TABLE version_comments ADD PRIMARY KEY (commentId);
ALTER TABLE version_comments ADD CONSTRAINT FK_version_comments_1 FOREIGN KEY (globalId) REFERENCES versions(globalId) ON DELETE CASCADE;
CREATE INDEX IDX_version_comments_1 ON version_comments(createdBy);

-- This table is defined way down here because it has a FK to the artifacts table *and* the versions table
CREATE TABLE artifact_branches (groupId NVARCHAR(512) NOT NULL, artifactId NVARCHAR(512) NOT NULL, branchId NVARCHAR(256) NOT NULL, branchOrder INT NOT NULL, version NVARCHAR(256) NOT NULL);
ALTER TABLE artifact_branches ADD PRIMARY KEY (groupId, artifactId, branchId, branchOrder);
ALTER TABLE artifact_branches ADD CONSTRAINT FK_artifact_branches_1 FOREIGN KEY (groupId, artifactId) REFERENCES artifacts(groupId, artifactId);
ALTER TABLE artifact_branches ADD CONSTRAINT FK_artifact_branches_2 FOREIGN KEY (groupId, artifactId, version) REFERENCES versions(groupId, artifactId, version) ON DELETE CASCADE;
CREATE INDEX IDX_artifact_branches_1 ON artifact_branches(groupId, artifactId, branchId, branchOrder);
