-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MS SQL Server
-- Upgrade Script from 104 to 105
-- *********************************************************************

UPDATE apicurio SET propValue = 105 WHERE propName = 'db_version';

CREATE TABLE schema_usage (globalId BIGINT NOT NULL, contentId BIGINT NOT NULL DEFAULT 0, clientId NVARCHAR(256) NOT NULL, operation NVARCHAR(32) NOT NULL, eventTimestamp BIGINT NOT NULL, recordedOn DATETIME2 NOT NULL DEFAULT GETDATE());
CREATE INDEX IDX_schema_usage_1 ON schema_usage(globalId);
CREATE INDEX IDX_schema_usage_2 ON schema_usage(clientId);
CREATE INDEX IDX_schema_usage_3 ON schema_usage(eventTimestamp);
