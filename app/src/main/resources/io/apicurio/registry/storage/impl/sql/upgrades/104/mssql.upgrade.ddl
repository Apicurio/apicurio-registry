-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MS SQL Server
-- Upgrade Script from 103 to 104
-- *********************************************************************

UPDATE apicurio SET propValue = 104 WHERE propName = 'db_version';

CREATE TABLE schema_usage (globalId BIGINT NOT NULL, clientId NVARCHAR(256) NOT NULL, operation NVARCHAR(32) NOT NULL, eventTimestamp BIGINT NOT NULL, recordedOn DATETIME2 NOT NULL DEFAULT GETDATE());
CREATE INDEX IDX_schema_usage_1 ON schema_usage(globalId);
CREATE INDEX IDX_schema_usage_2 ON schema_usage(clientId);
CREATE INDEX IDX_schema_usage_3 ON schema_usage(eventTimestamp);

CREATE TABLE schema_usage_summary (globalId BIGINT NOT NULL, totalFetches BIGINT NOT NULL DEFAULT 0, uniqueClients INT NOT NULL DEFAULT 0, firstFetchedOn BIGINT NOT NULL, lastFetchedOn BIGINT NOT NULL, clientList TEXT, updatedOn DATETIME2 NOT NULL DEFAULT GETDATE());
ALTER TABLE schema_usage_summary ADD PRIMARY KEY (globalId);
