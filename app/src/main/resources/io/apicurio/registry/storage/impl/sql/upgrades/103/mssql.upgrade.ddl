-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MS SQL Server
-- Upgrade Script from 102 to 103
-- *********************************************************************

UPDATE apicurio SET propValue = 103 WHERE propName = 'db_version';

CREATE TABLE contract_rules (ruleId BIGINT IDENTITY(1,1) NOT NULL, groupId NVARCHAR(512) NOT NULL, artifactId NVARCHAR(512) NOT NULL, globalId BIGINT, ruleCategory NVARCHAR(32) NOT NULL, orderIndex INT NOT NULL, ruleName NVARCHAR(512) NOT NULL, kind NVARCHAR(32) NOT NULL, ruleType NVARCHAR(256) NOT NULL, mode NVARCHAR(32) NOT NULL, expr TEXT, params TEXT, tags TEXT, onSuccess NVARCHAR(32), onFailure NVARCHAR(32), disabled BIT NOT NULL DEFAULT 0);
ALTER TABLE contract_rules ADD PRIMARY KEY (ruleId);
ALTER TABLE contract_rules ADD CONSTRAINT FK_contract_rules_1 FOREIGN KEY (globalId) REFERENCES versions(globalId) ON DELETE CASCADE;
CREATE INDEX IDX_contract_rules_1 ON contract_rules(groupId, artifactId);
CREATE INDEX IDX_contract_rules_2 ON contract_rules(globalId);
