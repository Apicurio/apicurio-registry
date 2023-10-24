-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MySQL 8.0+
-- *********************************************************************

CREATE TABLE apicurio (prop_name VARCHAR(255) NOT NULL, prop_value VARCHAR(255));
ALTER TABLE apicurio ADD PRIMARY KEY (prop_name);
INSERT INTO apicurio (prop_name, prop_value) VALUES ('db_version', 1);

CREATE TABLE sequences (name VARCHAR(32) NOT NULL, value BIGINT NOT NULL);
ALTER TABLE sequences ADD PRIMARY KEY (name);

CREATE TABLE globalrules (type VARCHAR(32) NOT NULL, configuration TEXT NOT NULL);
ALTER TABLE globalrules ADD PRIMARY KEY (type);

-- Reduced groupId and artifactId to VARCHAR(256) from VARCHAR(512) due to the Primary Key exceeding the MySQL limit of 3072 bytes
CREATE TABLE artifacts (groupId VARCHAR(256) NOT NULL, artifactId VARCHAR(256) NOT NULL, type VARCHAR(32) NOT NULL, createdBy VARCHAR(256), createdOn TIMESTAMP NOT NULL, latest BIGINT);
ALTER TABLE artifacts ADD PRIMARY KEY (groupId, artifactId);
CREATE INDEX IDX_artifacts_0 ON artifacts (type);
CREATE INDEX IDX_artifacts_1 ON artifacts (createdBy);
CREATE INDEX IDX_artifacts_2 ON artifacts (createdOn);

-- Reduced groupId and artifactId to VARCHAR(256) from VARCHAR(512) due to the Primary Key exceeding the MySQL limit of 3072 bytes
CREATE TABLE rules(groupId VARCHAR(256) NOT NULL, artifactId VARCHAR(256) NOT NULL, type VARCHAR(32) NOT NULL, configuration VARCHAR(1024) NOT NULL);
ALTER TABLE rules ADD PRIMARY KEY (groupId, artifactId, type);

CREATE TABLE content (contentId BIGINT NOT NULL, canonicalHash VARCHAR(64) NOT NULL, contentHash VARCHAR(64) NOT NULL, content LONGBLOB NOT NULL, artifactreferences TEXT);
ALTER TABLE content ADD PRIMARY KEY (contentId);
ALTER TABLE content ADD UNIQUE KEY UNQ_content_1 (contentHash);
CREATE INDEX IDX_content_1 ON content (canonicalHash);
CREATE INDEX IDX_content_2 ON content (contentHash);

-- Reduced groupId and artifactId to VARCHAR(256) from VARCHAR(512) due to the Primary Key exceeding the MySQL limit of 3072 bytes
-- Dropped the index 'IDX_versions_4' on description as MySQL can't index VARCHAR(1024)
CREATE TABLE versions (globalId BIGINT NOT NULL, groupId VARCHAR(256) NOT NULL, artifactId VARCHAR(256) NOT NULL, version VARCHAR(128), versionId INT NOT NULL, state VARCHAR(64) NOT NULL, name VARCHAR(512), description VARCHAR(1024), createdBy VARCHAR(256), createdOn TIMESTAMP NOT NULL, labels TEXT, properties TEXT, contentId BIGINT NOT NULL);
ALTER TABLE versions ADD PRIMARY KEY (globalId);
ALTER TABLE versions ADD UNIQUE KEY UQ_versions_1 (groupId, artifactId, version);
ALTER TABLE versions ADD FOREIGN KEY (groupId, artifactId) REFERENCES artifacts (groupId, artifactId);
ALTER TABLE versions ADD FOREIGN KEY (contentId) REFERENCES content (contentId);
CREATE INDEX IDX_versions_1 ON versions (version);
CREATE INDEX IDX_versions_2 ON versions (state);
CREATE INDEX IDX_versions_3 ON versions (name);
-- CREATE INDEX IDX_versions_4 ON versions (description);
CREATE INDEX IDX_versions_5 ON versions (createdBy);
CREATE INDEX IDX_versions_6 ON versions (createdOn);
CREATE INDEX IDX_versions_7 ON versions (contentId);

-- Dropped the index 'IDX_props_2' on pvalue as MySQL can't index VARCHAR(1024)
CREATE TABLE properties (globalId BIGINT NOT NULL, pkey VARCHAR(256) NOT NULL, pvalue VARCHAR(1024));
ALTER TABLE properties ADD FOREIGN KEY (globalId) REFERENCES versions (globalId);
CREATE INDEX IDX_props_1 ON properties (pkey);
-- CREATE INDEX IDX_props_2 ON properties (pvalue);

CREATE TABLE labels (globalId BIGINT NOT NULL, label VARCHAR(256) NOT NULL);
ALTER TABLE labels ADD FOREIGN KEY (globalId) REFERENCES versions (globalId);
CREATE INDEX IDX_labels_1 ON labels (label);

CREATE TABLE comments (commentId VARCHAR(128) NOT NULL, globalId BIGINT NOT NULL, createdBy VARCHAR(256), createdOn TIMESTAMP NOT NULL, cvalue VARCHAR(1024) NOT NULL);
ALTER TABLE comments ADD PRIMARY KEY (commentId);
ALTER TABLE comments ADD FOREIGN KEY (globalId) REFERENCES versions (globalId);
CREATE INDEX IDX_comments_1 ON comments (createdBy);

-- Reduced groupId from VARCHAR(512) to VARCHAR(256) to match the same type for groupId in other tables
CREATE TABLE artifactgroups (groupId VARCHAR(256) NOT NULL, description VARCHAR(1024), artifactsType VARCHAR(32), createdBy VARCHAR(256), createdOn TIMESTAMP NOT NULL, modifiedBy VARCHAR(256), modifiedOn TIMESTAMP, properties TEXT);
ALTER TABLE artifactgroups ADD PRIMARY KEY (groupId);

CREATE TABLE acls (principalId VARCHAR(256) NOT NULL, role VARCHAR(32) NOT NULL, principalName VARCHAR(256));
ALTER TABLE acls ADD PRIMARY KEY (principalId);

CREATE TABLE downloads (downloadId VARCHAR(128) NOT NULL, expires BIGINT NOT NULL, context VARCHAR(1024));
ALTER TABLE downloads ADD PRIMARY KEY (downloadId);
CREATE INDEX IDX_down_1 ON downloads (expires);

CREATE TABLE config (pname VARCHAR(255) NOT NULL, pvalue VARCHAR(1024) NOT NULL, modifiedOn BIGINT NOT NULL);
ALTER TABLE config ADD PRIMARY KEY (pname);
CREATE INDEX IDX_config_1 ON config (modifiedOn);

CREATE TABLE artifactreferences (contentId BIGINT NOT NULL, groupId VARCHAR(512), artifactId VARCHAR(512) NOT NULL, version VARCHAR(256), name VARCHAR(512) NOT NULL);
ALTER TABLE artifactreferences ADD PRIMARY KEY (contentId, name);
ALTER TABLE artifactreferences ADD FOREIGN KEY (contentId) REFERENCES content (contentId) ON DELETE CASCADE;

-- Required for the method `getNextSequenceValue()` on MySQLSqlStatements since MySQL does not have an equivalent
-- to Postgres' RETURNING or SQLServer's OUTPUT functionalities
CREATE PROCEDURE GetNextSequenceValue(IN in_name varchar(32), IN in_value int) BEGIN INSERT INTO sequences (name, value) VALUES (in_name, in_value) ON DUPLICATE KEY UPDATE value = value + 1; SELECT value FROM sequences WHERE name = in_name; END;
