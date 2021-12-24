-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrades the DB schema from version 5 to version 6.
-- *********************************************************************

UPDATE apicurio SET prop_value = 7 WHERE prop_name = 'db_version';

CREATE TABLE customrules (tenantId VARCHAR(128) NOT NULL, ruleId VARCHAR(32) NOT NULL, supportedArtifactType VARCHAR(32), customRuleType VARCHAR(32) NOT NULL, configuration TEXT NOT NULL, description VARCHAR(1024));
ALTER TABLE customrules ADD PRIMARY KEY (tenantId, ruleId);

CREATE TABLE customrulebindings (tenantId VARCHAR(128) NOT NULL, groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, ruleId VARCHAR(32) NOT NULL);
ALTER TABLE customrulebindings ADD PRIMARY KEY (tenantId, groupId, artifactId, ruleId);
