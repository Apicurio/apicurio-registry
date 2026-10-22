-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 102 to 103
-- *********************************************************************

UPDATE apicurio SET propValue = 103 WHERE propName = 'db_version';

CREATE TABLE contract_rules (ruleId BIGSERIAL NOT NULL, groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, globalId BIGINT, ruleCategory VARCHAR(32) NOT NULL, orderIndex INT NOT NULL, ruleName VARCHAR(512) NOT NULL, kind VARCHAR(32) NOT NULL, ruleType VARCHAR(256) NOT NULL, mode VARCHAR(32) NOT NULL, expr TEXT, params TEXT, tags TEXT, onSuccess VARCHAR(32), onFailure VARCHAR(32), disabled BOOLEAN NOT NULL DEFAULT FALSE);
ALTER TABLE contract_rules ADD PRIMARY KEY (ruleId);
ALTER TABLE contract_rules ADD CONSTRAINT FK_contract_rules_1 FOREIGN KEY (globalId) REFERENCES versions(globalId) ON DELETE CASCADE;
CREATE INDEX IDX_contract_rules_1 ON contract_rules(groupId, artifactId);
CREATE INDEX IDX_contract_rules_2 ON contract_rules(globalId);
