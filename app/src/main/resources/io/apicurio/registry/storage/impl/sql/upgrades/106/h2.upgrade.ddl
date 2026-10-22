-- *********************************************************************
-- DDL for the Apicurio Registry - Database: h2
-- Upgrade Script from 105 to 106
-- *********************************************************************

UPDATE apicurio SET propValue = 106 WHERE propName = 'db_version';

CREATE TABLE contract_audit_log (auditId BIGINT AUTO_INCREMENT NOT NULL, groupId VARCHAR(512), artifactId VARCHAR(512) NOT NULL, version VARCHAR(256), action VARCHAR(64) NOT NULL, principal VARCHAR(256), details TEXT, createdOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
ALTER TABLE contract_audit_log ADD PRIMARY KEY (auditId);
CREATE INDEX IDX_contract_audit_1 ON contract_audit_log(groupId, artifactId);
CREATE INDEX IDX_contract_audit_2 ON contract_audit_log(createdOn);
