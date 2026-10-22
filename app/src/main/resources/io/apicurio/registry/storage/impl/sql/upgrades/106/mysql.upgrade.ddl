-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mysql
-- Upgrade Script from 105 to 106
-- *********************************************************************

UPDATE apicurio SET propValue = 106 WHERE propName = 'db_version';

CREATE TABLE contract_audit_log (auditId BIGINT NOT NULL AUTO_INCREMENT, groupId VARCHAR(512), artifactId VARCHAR(512) NOT NULL, version VARCHAR(256), action VARCHAR(64) NOT NULL, principal VARCHAR(256), details TEXT, createdOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (auditId));
CREATE INDEX IDX_contract_audit_1 ON contract_audit_log(groupId(255), artifactId(255));
CREATE INDEX IDX_contract_audit_2 ON contract_audit_log(createdOn);
