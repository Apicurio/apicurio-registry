-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- *********************************************************************

CREATE TABLE apicurio (prop_name VARCHAR(255) NOT NULL, prop_value VARCHAR(255));
ALTER TABLE apicurio ADD PRIMARY KEY (prop_name);
INSERT INTO apicurio (prop_name, prop_value) VALUES ('db_version', 1);

CREATE TABLE globalrules (type VARCHAR(32) NOT NULL, configuration CLOB NOT NULL);
ALTER TABLE globalrules ADD PRIMARY KEY (type);

CREATE TABLE artifacts (artifactId VARCHAR(512) NOT NULL, type VARCHAR(32) NOT NULL, createdBy VARCHAR(256), createdOn TIMESTAMP NOT NULL, latest BIGINT);
ALTER TABLE artifacts ADD PRIMARY KEY (id);
ALTER TABLE artifacts ADD CONSTRAINT FK_artifacts_1 FOREIGN KEY (latest) REFERENCES versions (globalId);

CREATE TABLE versions (artifactId VARCHAR(512) NOT NULL, globalId BIGINT AUTO_INCREMENT NOT NULL, version INT NOT NULL, state TINYINT NOT NULL, name VARCHAR(512), description CLOB, createdBy VARCHAR(256), createdOn TIMESTAMP NOT NULL, CLOB labels, CLOB properties);
ALTER TABLE versions ADD PRIMARY KEY (globalId);
ALTER TABLE versions ADD CONSTRAINT FK_versions_1 FOREIGN KEY (artifactId) REFERENCES artifacts (artifactId);
CREATE INDEX IDX_versions_1 ON versions(version);
CREATE INDEX IDX_versions_2 ON versions(state);
CREATE INDEX IDX_versions_3 ON versions(name);
CREATE INDEX IDX_versions_4 ON versions(description);
CREATE INDEX IDX_versions_5 ON versions(createdBy);

CREATE TABLE rules (globalId BIGINT NOT NULL, type VARCHAR(32) NOT NULL, configuration CLOB NOT NULL);
ALTER TABLE rules ADD PRIMARY KEY (globalId, type);
ALTER TABLE rules ADD CONSTRAINT FK_rules_1 FOREIGN KEY (globalId) REFERENCES versions (globalId);

CREATE TABLE properties (globalId BIGINT NOT NULL, pkey VARCHAR(256) NOT NULL, pvalue VARCHAR(1024));
ALTER TABLE properties ADD CONSTRAINT FK_props_1 FOREIGN KEY (globalId) REFERENCES versions (globalId);
CREATE INDEX IDX_props_1 ON properties(pkey);
CREATE INDEX IDX_props_2 ON properties(pvalue);

CREATE TABLE labels (globalId BIGINT NOT NULL, label VARCHAR(256) NOT NULL);
ALTER TABLE labels ADD CONSTRAINT FK_labels_1 FOREIGN KEY (globalId) REFERENCES versions (globalId);
CREATE INDEX IDX_labels_1 ON properties(label);
