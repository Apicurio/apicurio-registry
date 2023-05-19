-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrades the DB schema from version 12 to version 13.
-- *********************************************************************

UPDATE apicurio SET prop_value = 13 WHERE prop_name = 'db_version';

CREATE TABLE comments (tenantId VARCHAR(128) NOT NULL, commentId VARCHAR(128) NOT NULL, globalId BIGINT NOT NULL, createdBy VARCHAR(256), createdOn DATETIME2(6) NOT NULL, cvalue VARCHAR(1024) NOT NULL);
ALTER TABLE comments ADD PRIMARY KEY (tenantId, commentId);
ALTER TABLE comments ADD CONSTRAINT FK_comments_1 FOREIGN KEY (tenantId, globalId) REFERENCES versions(tenantId, globalId);
CREATE INDEX IDX_comments_1 ON comments(createdBy);
