-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL 10+
-- *********************************************************************


CREATE TABLE apicurio (
                          prop_name STRING(255) NOT NULL,
                          prop_value STRING(255),
) PRIMARY KEY(prop_name);

CREATE TABLE artifacts (
                           tenantId STRING(128) NOT NULL,
                           artifactId STRING(512) NOT NULL,
                           type STRING(32) NOT NULL,
                           createdBy STRING(256),
                           createdOn TIMESTAMP NOT NULL,
                           latest INT64,
) PRIMARY KEY(tenantId, artifactId);

CREATE INDEX IDX_artifacts_0 ON artifacts(type);

CREATE INDEX IDX_artifacts_1 ON artifacts(createdBy);

CREATE INDEX IDX_artifacts_2 ON artifacts(createdOn);

CREATE TABLE content (
                         contentId STRING(36) NOT NULL,
                         canonicalHash STRING(64) NOT NULL,
                         contentHash STRING(64) NOT NULL,
                         content BYTES(MAX) NOT NULL,
) PRIMARY KEY(contentId);

CREATE INDEX IDX_content_1 ON content(canonicalHash);

CREATE INDEX IDX_content_2 ON content(contentHash);

CREATE UNIQUE INDEX UNQ_content_1 ON content(contentHash);

CREATE TABLE globalrules (
                             tenantId STRING(128) NOT NULL,
                             type STRING(32) NOT NULL,
                             configuration STRING(MAX) NOT NULL,
) PRIMARY KEY(type);

CREATE TABLE labels (
                        globalId STRING(36) NOT NULL,
                        label STRING(256) NOT NULL,
) PRIMARY KEY(globalId, label);

CREATE INDEX IDX_labels_1 ON labels(label);

CREATE TABLE properties (
                            globalId STRING(36) NOT NULL,
                            pkey STRING(256) NOT NULL,
                            pvalue STRING(1024),
) PRIMARY KEY(globalId, pkey, pvalue);

CREATE INDEX IDX_props_1 ON properties(pkey);

CREATE INDEX IDX_props_2 ON properties(pvalue);

CREATE TABLE rules (
                       tenantId STRING(128) NOT NULL,
                       artifactId STRING(512) NOT NULL,
                       type STRING(32) NOT NULL,
                       configuration STRING(1024) NOT NULL,
                       CONSTRAINT FK_rules_1 FOREIGN KEY(tenantId, artifactId) REFERENCES artifacts(tenantId, artifactId),
) PRIMARY KEY(tenantId, artifactId, type);

CREATE TABLE sequences (
                           name STRING(64) NOT NULL,
                           next_value INT64 NOT NULL,
) PRIMARY KEY(name);

CREATE TABLE versions (
                          globalId STRING(36) NOT NULL,
                          tenantId STRING(128) NOT NULL,
                          artifactId STRING(512) NOT NULL,
                          version INT64 NOT NULL,
                          state STRING(64) NOT NULL,
                          name STRING(512),
                          description STRING(1024),
                          createdBy STRING(256),
                          createdOn TIMESTAMP NOT NULL,
                          labels STRING(MAX),
                          properties STRING(MAX),
                          contentId STRING(36) NOT NULL,
                          CONSTRAINT FK_versions_1 FOREIGN KEY(tenantId, artifactId) REFERENCES artifacts(tenantId, artifactId),
                          CONSTRAINT FK_versions_2 FOREIGN KEY(contentId) REFERENCES content(contentId),
) PRIMARY KEY(globalId);

ALTER TABLE labels ADD CONSTRAINT FK_labels_1 FOREIGN KEY(globalId) REFERENCES versions(globalId);

ALTER TABLE properties ADD CONSTRAINT FK_props_1 FOREIGN KEY(globalId) REFERENCES versions(globalId);

CREATE INDEX IDX_versions_2 ON versions(state);

CREATE INDEX IDX_versions_3 ON versions(name);

CREATE INDEX IDX_versions_4 ON versions(description);

CREATE INDEX IDX_versions_5 ON versions(createdBy);

CREATE INDEX IDX_versions_6 ON versions(createdOn);

CREATE INDEX IDX_versions_7 ON versions(contentId);

CREATE UNIQUE INDEX UQ_versions_1 ON versions(tenantId, artifactId, version)





-- CREATE TABLE apicurio (prop_name STRING(255) NOT NULL, prop_value STRING(255)) ADD PRIMARY KEY (prop_name);
INSERT INTO apicurio (prop_name, prop_value) VALUES ('db_version', 1);

-- CREATE TABLE globalrules (tenantId STRING(128) NOT NULL, type STRING(32) NOT NULL, configuration STRING(MAX) NOT NULL);
-- ALTER TABLE globalrules ADD PRIMARY KEY (type);

-- CREATE TABLE artifacts (tenantId STRING(128) NOT NULL, artifactId STRING(512) NOT NULL, type STRING(32) NOT NULL, createdBy STRING(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, latest BIGINT);
-- ALTER TABLE artifacts ADD PRIMARY KEY (tenantId, artifactId);
-- CREATE INDEX IDX_artifacts_0 ON artifacts USING HASH (type);
-- CREATE INDEX IDX_artifacts_1 ON artifacts USING HASH (createdBy);
-- CREATE INDEX IDX_artifacts_2 ON artifacts(createdOn);

-- CREATE TABLE rules (tenantId STRING(128) NOT NULL, artifactId STRING(512) NOT NULL, type STRING(32) NOT NULL, configuration STRING(1024) NOT NULL);
-- ALTER TABLE rules ADD PRIMARY KEY (tenantId, artifactId, type);
-- ALTER TABLE rules ADD CONSTRAINT FK_rules_1 FOREIGN KEY (tenantId, artifactId) REFERENCES artifacts(tenantId, artifactId);

-- CREATE TABLE content (contentId BIGSERIAL NOT NULL, canonicalHash STRING(64) NOT NULL, contentHash STRING(64) NOT NULL, content BYTEA NOT NULL);
-- ALTER TABLE content ADD PRIMARY KEY (contentId);
-- ALTER TABLE content ADD CONSTRAINT UNQ_content_1 UNIQUE (contentHash);
-- CREATE INDEX IDX_content_1 ON content USING HASH (canonicalHash);
-- CREATE INDEX IDX_content_2 ON content USING HASH (contentHash);

CREATE SEQUENCE globalidsequence;

-- CREATE TABLE versions (globalId BIGINT NOT NULL, tenantId STRING(128) NOT NULL, artifactId STRING(512) NOT NULL, version INT NOT NULL, state STRING(64) NOT NULL, name STRING(512), description STRING(1024), createdBy STRING(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, labels TEXT, properties TEXT, contentId BIGINT NOT NULL);
-- ALTER TABLE versions ADD PRIMARY KEY (globalId);
-- ALTER TABLE versions ADD CONSTRAINT UQ_versions_1 UNIQUE (tenantId, artifactId, version);
-- ALTER TABLE versions ADD CONSTRAINT FK_versions_1 FOREIGN KEY (tenantId, artifactId) REFERENCES artifacts(tenantId, artifactId);
-- ALTER TABLE versions ADD CONSTRAINT FK_versions_2 FOREIGN KEY (contentId) REFERENCES content(contentId);
-- CREATE INDEX IDX_versions_1 ON versions(version);
-- CREATE INDEX IDX_versions_2 ON versions USING HASH (state);
-- CREATE INDEX IDX_versions_3 ON versions(name);
-- CREATE INDEX IDX_versions_4 ON versions(description);
-- CREATE INDEX IDX_versions_5 ON versions USING HASH (createdBy);
-- CREATE INDEX IDX_versions_6 ON versions(createdOn);
-- CREATE INDEX IDX_versions_7 ON versions USING HASH (contentId);
--
-- CREATE TABLE properties (globalId BIGINT NOT NULL, pkey STRING(256) NOT NULL, pvalue STRING(1024));
-- ALTER TABLE properties ADD CONSTRAINT FK_props_1 FOREIGN KEY (globalId) REFERENCES versions(globalId);
-- CREATE INDEX IDX_props_1 ON properties(pkey);
-- CREATE INDEX IDX_props_2 ON properties(pvalue);
--
-- CREATE TABLE labels (globalId BIGINT NOT NULL, label STRING(256) NOT NULL);
-- ALTER TABLE labels ADD CONSTRAINT FK_labels_1 FOREIGN KEY (globalId) REFERENCES versions(globalId);
-- CREATE INDEX IDX_labels_1 ON labels(label);
