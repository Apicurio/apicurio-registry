-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MySQL 8.0+
-- *********************************************************************

CREATE TABLE apicurio (prop_name VARCHAR(255) NOT NULL, prop_value VARCHAR(255));
ALTER TABLE apicurio ADD PRIMARY KEY (prop_name);
INSERT INTO apicurio (prop_name, prop_value) VALUES ('db_version', 14);

CREATE TABLE sequences (tenantId VARCHAR(128) NOT NULL, name VARCHAR(32) NOT NULL, value BIGINT NOT NULL);
ALTER TABLE sequences ADD PRIMARY KEY (tenantId, name);

CREATE TABLE globalrules (tenantId VARCHAR(128) NOT NULL, type VARCHAR(32) NOT NULL, configuration TEXT NOT NULL);
ALTER TABLE globalrules ADD PRIMARY KEY (tenantId, type);

-- Reduced groupId and artifactId to VARCHAR(256) from VARCHAR(512) due to the Primary Key exceeding the MySQL limit of 3072 bytes
CREATE TABLE artifacts (tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(256) NOT NULL, artifactId VARCHAR(256) NOT NULL, type VARCHAR(32) NOT NULL, createdBy VARCHAR(256), createdOn TIMESTAMP NOT NULL, latest BIGINT);
ALTER TABLE artifacts ADD PRIMARY KEY (tenantId, groupId, artifactId);
CREATE INDEX IDX_artifacts_0 ON artifacts(type) USING HASH;
CREATE INDEX IDX_artifacts_1 ON artifacts(createdBy) USING HASH;
CREATE INDEX IDX_artifacts_2 ON artifacts(createdOn);

-- Reduced groupId and artifactId to VARCHAR(256) from VARCHAR(512) due to the Primary Key exceeding the MySQL limit of 3072 bytes
CREATE TABLE rules (tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(256) NOT NULL, artifactId VARCHAR(256) NOT NULL, type VARCHAR(32) NOT NULL, configuration VARCHAR(1024) NOT NULL);
ALTER TABLE rules ADD PRIMARY KEY (tenantId, groupId, artifactId, type);

CREATE TABLE content (tenantId VARCHAR(128) NOT NULL, contentId BIGINT NOT NULL, canonicalHash VARCHAR(64) NOT NULL, contentHash VARCHAR(64) NOT NULL, content LONGBLOB NOT NULL, artifactreferences TEXT);
ALTER TABLE content ADD PRIMARY KEY (tenantId, contentId);
ALTER TABLE content ADD CONSTRAINT UNQ_content_1 UNIQUE (tenantId, contentHash);
CREATE INDEX IDX_content_1 ON content (canonicalHash) USING HASH;
CREATE INDEX IDX_content_2 ON content (contentHash) USING HASH;

-- Reduced groupId and artifactId to VARCHAR(256) from VARCHAR(512) due to the Primary Key exceeding the MySQL limit of 3072 bytes
-- Reduced version to VARCHAR(128) from VARCHAR(256) due to the constraint UQ_versions_1 exceeding the MySQL limit of 3072 bytes
-- Dropped the index 'IDX_versions_4' on description as MySQL can't index VARCHAR(1024)
CREATE TABLE versions (globalId BIGINT NOT NULL, tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(256) NOT NULL, artifactId VARCHAR(256) NOT NULL, version VARCHAR(128), versionId INT NOT NULL, state VARCHAR(64) NOT NULL, name VARCHAR(512), description VARCHAR(1024), createdBy VARCHAR(256), createdOn TIMESTAMP NOT NULL, labels TEXT, properties TEXT, contentId BIGINT NOT NULL);
ALTER TABLE versions ADD PRIMARY KEY (tenantId, globalId);
ALTER TABLE versions ADD CONSTRAINT UQ_versions_1 UNIQUE (tenantId, groupId, artifactId, version);
ALTER TABLE versions ADD CONSTRAINT FK_versions_1 FOREIGN KEY (tenantId, groupId, artifactId) REFERENCES artifacts (tenantId, groupId, artifactId);
ALTER TABLE versions ADD CONSTRAINT FK_versions_2 FOREIGN KEY (tenantId, contentId) REFERENCES content (tenantId, contentId);
CREATE INDEX IDX_versions_1 ON versions(version);
CREATE INDEX IDX_versions_2 ON versions (state) USING HASH ;
CREATE INDEX IDX_versions_3 ON versions(name);
-- CREATE INDEX IDX_versions_4 ON versions(description);
CREATE INDEX IDX_versions_5 ON versions (createdBy) USING HASH;
CREATE INDEX IDX_versions_6 ON versions(createdOn);
CREATE INDEX IDX_versions_7 ON versions (contentId) USING HASH ;

-- Dropped the index 'IDX_props_2' on pvalue as MySQL can't index VARCHAR(1024)
CREATE TABLE properties (tenantId VARCHAR(128) NOT NULL, globalId BIGINT NOT NULL, pkey VARCHAR(256) NOT NULL, pvalue VARCHAR(1024));
ALTER TABLE properties ADD CONSTRAINT FK_props_1 FOREIGN KEY (tenantId, globalId) REFERENCES versions (tenantId, globalId);
CREATE INDEX IDX_props_1 ON properties(pkey);
-- CREATE INDEX IDX_props_2 ON properties(pvalue);

CREATE TABLE labels (tenantId VARCHAR(128) NOT NULL,globalId BIGINT NOT NULL, label VARCHAR(256) NOT NULL);
ALTER TABLE labels ADD CONSTRAINT FK_labels_1 FOREIGN KEY (tenantId, globalId) REFERENCES versions (tenantId, globalId);
CREATE INDEX IDX_labels_1 ON labels(label);

CREATE TABLE comments (tenantId VARCHAR(128) NOT NULL, commentId VARCHAR(128) NOT NULL, globalId BIGINT NOT NULL, createdBy VARCHAR(256), createdOn TIMESTAMP NOT NULL, cvalue VARCHAR(1024) NOT NULL);
ALTER TABLE comments ADD PRIMARY KEY (tenantId, commentId);
ALTER TABLE comments ADD CONSTRAINT FK_comments_1 FOREIGN KEY (tenantId, globalId) REFERENCES versions (tenantId, globalId);
CREATE INDEX IDX_comments_1 ON comments(createdBy);

CREATE TABLE logconfiguration (logger VARCHAR(512) NOT NULL, loglevel VARCHAR(32) NOT NULL);
ALTER TABLE logconfiguration ADD PRIMARY KEY (logger);

CREATE TABLE artifactgroups (tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(512) NOT NULL, description VARCHAR(1024), artifactsType VARCHAR(32), createdBy VARCHAR(256), createdOn TIMESTAMP NOT NULL, modifiedBy VARCHAR(256), modifiedOn TIMESTAMP, properties TEXT);
ALTER TABLE artifactgroups ADD PRIMARY KEY (tenantId, groupId);

CREATE TABLE acls (tenantId VARCHAR(128) NOT NULL, principalId VARCHAR(256) NOT NULL, role VARCHAR(32) NOT NULL, principalName VARCHAR(256));
ALTER TABLE acls ADD PRIMARY KEY (tenantId, principalId);

CREATE TABLE downloads (tenantId VARCHAR(128) NOT NULL, downloadId VARCHAR(128) NOT NULL, expires BIGINT NOT NULL, context VARCHAR(1024));
ALTER TABLE downloads ADD PRIMARY KEY (tenantId, downloadId);
CREATE INDEX IDX_down_1 ON downloads (expires) USING HASH;

CREATE TABLE config (tenantId VARCHAR(128) NOT NULL, pname VARCHAR(255) NOT NULL, pvalue VARCHAR(1024) NOT NULL, modifiedOn BIGINT NOT NULL);
ALTER TABLE config ADD PRIMARY KEY (tenantId, pname);
CREATE INDEX IDX_config_1 ON config(modifiedOn);

CREATE TABLE artifactreferences (tenantId VARCHAR(128) NOT NULL, contentId BIGINT NOT NULL, groupId VARCHAR(512), artifactId VARCHAR(512) NOT NULL, version VARCHAR(256), name VARCHAR(512) NOT NULL);
ALTER TABLE artifactreferences ADD PRIMARY KEY (tenantId, contentId, name);
ALTER TABLE artifactreferences ADD CONSTRAINT FK_artifactreferences_1 FOREIGN KEY (tenantId, contentId) REFERENCES content (tenantId, contentId) ON DELETE CASCADE;

-- Required for the method `getNextSequenceValue()` on MySQLSqlStatements since  MySQL does not have an equivalent
-- to Postgres' RETURNING or SQLServer's OUTPUT functionalities
CREATE PROCEDURE GetNextSequenceValue(IN in_tenantId varchar(255), IN in_name varchar(255), IN in_value int) BEGIN INSERT INTO sequences (tenantId, name, value) VALUES (in_tenantId, in_name, in_value) ON DUPLICATE KEY UPDATE value = value + 1; SELECT value FROM sequences WHERE tenantId = in_tenantId AND name = in_name; END;
