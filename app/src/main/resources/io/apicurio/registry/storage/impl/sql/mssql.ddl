-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MS SQL Server
-- *********************************************************************

CREATE TABLE apicurio (prop_name NVARCHAR(255) NOT NULL, prop_value NVARCHAR(255));
ALTER TABLE apicurio ADD PRIMARY KEY (prop_name);
INSERT INTO apicurio (prop_name, prop_value) VALUES ('db_version', 100);

-- TODO: Different column name in h2
CREATE TABLE sequences (name NVARCHAR(32) NOT NULL, value BIGINT NOT NULL);
ALTER TABLE sequences ADD PRIMARY KEY (name);

CREATE TABLE globalrules (type NVARCHAR(32) NOT NULL, configuration TEXT NOT NULL);
ALTER TABLE globalrules ADD PRIMARY KEY (type);

CREATE TABLE artifacts (groupId NVARCHAR(512) NOT NULL, artifactId NVARCHAR(512) NOT NULL, type NVARCHAR(32) NOT NULL, createdBy NVARCHAR(256), createdOn DATETIME2(6) NOT NULL);
ALTER TABLE artifacts ADD PRIMARY KEY (groupId, artifactId);
CREATE INDEX IDX_artifacts_0 ON artifacts(type);
CREATE INDEX IDX_artifacts_1 ON artifacts(createdBy);
CREATE INDEX IDX_artifacts_2 ON artifacts(createdOn);

CREATE TABLE rules (groupId NVARCHAR(512) NOT NULL, artifactId NVARCHAR(512) NOT NULL, type NVARCHAR(32) NOT NULL, configuration NVARCHAR(1024) NOT NULL);
ALTER TABLE rules ADD PRIMARY KEY (groupId, artifactId, type);

CREATE TABLE content (contentId BIGINT NOT NULL, canonicalHash NVARCHAR(64) NOT NULL, contentHash NVARCHAR(64) NOT NULL, content VARBINARY(MAX) NOT NULL, artifactreferences TEXT);
ALTER TABLE content ADD PRIMARY KEY (contentId);
ALTER TABLE content ADD CONSTRAINT UQ_content_1 UNIQUE (contentHash);
CREATE INDEX IDX_content_1 ON content(canonicalHash);
CREATE INDEX IDX_content_2 ON content(contentHash);

-- The "versionOrder" field is needed to generate "version" when it is not provided.
-- It contains the same information as the "branchOrder" in the "latest" branch, but we cannot use it because of a chicken-and-egg problem.
-- At least it is no longer confusingly called "versionId". The "versionOrder" field should not be used for any other purpose.
CREATE TABLE versions (globalId BIGINT NOT NULL, groupId NVARCHAR(512) NOT NULL, artifactId NVARCHAR(512) NOT NULL, version NVARCHAR(256), versionOrder INT NOT NULL, state NVARCHAR(64) NOT NULL, name NVARCHAR(512), description NVARCHAR(1024), createdBy NVARCHAR(256), createdOn DATETIME2(6) NOT NULL, labels TEXT, properties TEXT, contentId BIGINT NOT NULL);
ALTER TABLE versions ADD PRIMARY KEY (globalId);
ALTER TABLE versions ADD CONSTRAINT UQ_versions_1 UNIQUE (groupId, artifactId, version);
ALTER TABLE versions ADD CONSTRAINT UQ_versions_2 UNIQUE (globalId, versionOrder);
ALTER TABLE versions ADD CONSTRAINT FK_versions_1 FOREIGN KEY (groupId, artifactId) REFERENCES artifacts(groupId, artifactId);
ALTER TABLE versions ADD CONSTRAINT FK_versions_2 FOREIGN KEY (contentId) REFERENCES content(contentId);
CREATE INDEX IDX_versions_1 ON versions(version);
CREATE INDEX IDX_versions_2 ON versions(state);
CREATE INDEX IDX_versions_3 ON versions(name);
-- CREATE INDEX IDX_versions_4 ON versions(description); TODO: Why commented out? Maybe the same as IDX_props_2?
CREATE INDEX IDX_versions_5 ON versions(createdBy);
CREATE INDEX IDX_versions_6 ON versions(createdOn);
CREATE INDEX IDX_versions_7 ON versions(contentId);

CREATE TABLE artifact_branches (groupId NVARCHAR(512) NOT NULL, artifactId NVARCHAR(512) NOT NULL, branchId NVARCHAR(256) NOT NULL, branchOrder INT NOT NULL, version NVARCHAR(256) NOT NULL);
ALTER TABLE artifact_branches ADD PRIMARY KEY (groupId, artifactId, branchId, branchOrder);
ALTER TABLE artifact_branches ADD CONSTRAINT FK_artifact_branches_1 FOREIGN KEY (groupId, artifactId, version) REFERENCES versions(groupId, artifactId, version);
CREATE INDEX IDX_artifact_branches_1 ON artifact_branches(groupId, artifactId, branchId, branchOrder);

CREATE TABLE properties (globalId BIGINT NOT NULL, pkey NVARCHAR(256) NOT NULL, pvalue NVARCHAR(1024));
ALTER TABLE properties ADD CONSTRAINT FK_props_1 FOREIGN KEY (globalId) REFERENCES versions(globalId);
CREATE INDEX IDX_props_1 ON properties(pkey);
-- MSSQL may have a limit on the size of indexed value:
-- com.microsoft.sqlserver.jdbc.SQLServerException: Operation failed. The index entry of length 2048 bytes for the index 'IDX_props_2' exceeds the maximum length of 1700 bytes for nonclustered indexes.
-- CREATE INDEX IDX_props_2 ON properties(pvalue);

CREATE TABLE labels (globalId BIGINT NOT NULL, label NVARCHAR(256) NOT NULL);
ALTER TABLE labels ADD CONSTRAINT FK_labels_1 FOREIGN KEY (globalId) REFERENCES versions(globalId);
CREATE INDEX IDX_labels_1 ON labels(label);

CREATE TABLE comments (commentId NVARCHAR(128) NOT NULL, globalId BIGINT NOT NULL, createdBy NVARCHAR(256), createdOn DATETIME2(6) NOT NULL, cvalue NVARCHAR(1024) NOT NULL);
ALTER TABLE comments ADD PRIMARY KEY (commentId);
ALTER TABLE comments ADD CONSTRAINT FK_comments_1 FOREIGN KEY (globalId) REFERENCES versions(globalId);
CREATE INDEX IDX_comments_1 ON comments(createdBy);

CREATE TABLE groups (groupId NVARCHAR(512) NOT NULL, description NVARCHAR(1024), artifactsType NVARCHAR(32), createdBy NVARCHAR(256), createdOn DATETIME2(6) NOT NULL, modifiedBy NVARCHAR(256), modifiedOn DATETIME2(6), properties TEXT);
ALTER TABLE groups ADD PRIMARY KEY (groupId);

CREATE TABLE acls (principalId NVARCHAR(256) NOT NULL, role NVARCHAR(32) NOT NULL, principalName NVARCHAR(256));
ALTER TABLE acls ADD PRIMARY KEY (principalId);

CREATE TABLE downloads (downloadId NVARCHAR(128) NOT NULL, expires BIGINT NOT NULL, context NVARCHAR(1024));
ALTER TABLE downloads ADD PRIMARY KEY (downloadId);
CREATE INDEX IDX_down_1 ON downloads(expires);

-- TODO: Missing NOT NULL in h2
CREATE TABLE config (pname NVARCHAR(255) NOT NULL, pvalue NVARCHAR(1024) NOT NULL, modifiedOn BIGINT NOT NULL);
ALTER TABLE config ADD PRIMARY KEY (pname);
CREATE INDEX IDX_config_1 ON config(modifiedOn);

CREATE TABLE artifactreferences (contentId BIGINT NOT NULL, groupId NVARCHAR(512), artifactId NVARCHAR(512) NOT NULL, version NVARCHAR(256), name NVARCHAR(512) NOT NULL);
ALTER TABLE artifactreferences ADD PRIMARY KEY (contentId, name);
ALTER TABLE artifactreferences ADD CONSTRAINT FK_artifactreferences_1 FOREIGN KEY (contentId) REFERENCES content(contentId) ON DELETE CASCADE;
