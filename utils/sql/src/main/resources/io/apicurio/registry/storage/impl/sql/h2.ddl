-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- *********************************************************************

CREATE TABLE apicurio (prop_name VARCHAR(255) NOT NULL, prop_value VARCHAR(255));
ALTER TABLE apicurio ADD PRIMARY KEY (prop_name);
INSERT INTO apicurio (prop_name, prop_value) VALUES ('db_version', 1);

CREATE TABLE globalrules (type VARCHAR(32) NOT NULL, configuration CLOB NOT NULL);
ALTER TABLE globalrules ADD PRIMARY KEY (type);

CREATE TABLE artifacts (artifactId VARCHAR(512) NOT NULL, type VARCHAR(32) NOT NULL, createdBy VARCHAR(256), createdOn TIMESTAMP NOT NULL, latest BIGINT);
ALTER TABLE artifacts ADD PRIMARY KEY (artifactId);
CREATE INDEX IDX_artifacts_0 ON artifacts(type);
CREATE INDEX IDX_artifacts_1 ON artifacts(createdBy);
CREATE INDEX IDX_artifacts_2 ON artifacts(createdOn);

CREATE TABLE rules (artifactId VARCHAR(512) NOT NULL, type VARCHAR(32) NOT NULL, configuration VARCHAR(1024) NOT NULL);
ALTER TABLE rules ADD PRIMARY KEY (artifactId, type);
ALTER TABLE rules ADD CONSTRAINT FK_rules_1 FOREIGN KEY (artifactId) REFERENCES artifacts(artifactId);

CREATE TABLE content (contentId BIGINT AUTO_INCREMENT NOT NULL, canonicalHash VARCHAR(64) NOT NULL, contentHash VARCHAR(64) NOT NULL, content BLOB NOT NULL);
ALTER TABLE content ADD PRIMARY KEY (contentId);
ALTER TABLE content ADD CONSTRAINT UNQ_content_1 UNIQUE (contentHash);
CREATE INDEX IDX_content_1 ON content(canonicalHash);
CREATE INDEX IDX_content_2 ON content(contentHash);

CREATE SEQUENCE globalidsequence;

CREATE TABLE versions (globalId BIGINT NOT NULL, artifactId VARCHAR(512) NOT NULL, version INT NOT NULL, state VARCHAR(64) NOT NULL, name VARCHAR(512), description VARCHAR(1024), createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, labels TEXT, properties TEXT, contentId BIGINT NOT NULL);
ALTER TABLE versions ADD PRIMARY KEY (globalId);
ALTER TABLE versions ADD CONSTRAINT UQ_versions_1 UNIQUE (artifactId, version);
ALTER TABLE versions ADD CONSTRAINT FK_versions_1 FOREIGN KEY (artifactId) REFERENCES artifacts(artifactId);
ALTER TABLE versions ADD CONSTRAINT FK_versions_2 FOREIGN KEY (contentId) REFERENCES content(contentId);
CREATE INDEX IDX_versions_1 ON versions(version);
CREATE INDEX IDX_versions_2 ON versions(state);
CREATE INDEX IDX_versions_3 ON versions(name);
CREATE INDEX IDX_versions_4 ON versions(description);
CREATE INDEX IDX_versions_5 ON versions(createdBy);
CREATE INDEX IDX_versions_6 ON versions(createdOn);
CREATE INDEX IDX_versions_7 ON versions(globalId);
CREATE INDEX IDX_versions_8 ON versions(contentId);

CREATE TABLE properties (globalId BIGINT NOT NULL, pkey VARCHAR(256) NOT NULL, pvalue VARCHAR(1024));
ALTER TABLE properties ADD CONSTRAINT FK_props_1 FOREIGN KEY (globalId) REFERENCES versions(globalId);
CREATE INDEX IDX_props_1 ON properties(pkey);
CREATE INDEX IDX_props_2 ON properties(pvalue);

CREATE TABLE labels (globalId BIGINT NOT NULL, label VARCHAR(256) NOT NULL);
ALTER TABLE labels ADD CONSTRAINT FK_labels_1 FOREIGN KEY (globalId) REFERENCES versions(globalId);
CREATE INDEX IDX_labels_1 ON labels(label);
