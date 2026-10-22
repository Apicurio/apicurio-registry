-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 105 to 106
-- *********************************************************************

UPDATE apicurio SET propValue = 106 WHERE propName = 'db_version';

CREATE TABLE contract_audit_log (auditId BIGINT IDENTITY NOT NULL, groupId NVARCHAR(512), artifactId NVARCHAR(512) NOT NULL, version NVARCHAR(256), action NVARCHAR(64) NOT NULL, principal NVARCHAR(256), details NVARCHAR(MAX), createdOn DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());
ALTER TABLE contract_audit_log ADD PRIMARY KEY (auditId);
CREATE INDEX IDX_contract_audit_1 ON contract_audit_log(groupId, artifactId);
CREATE INDEX IDX_contract_audit_2 ON contract_audit_log(createdOn);
