-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 103 to 104
-- *********************************************************************

UPDATE apicurio SET propValue = 104 WHERE propName = 'db_version';

CREATE TABLE schema_usage (globalId BIGINT NOT NULL, clientId VARCHAR(256) NOT NULL, operation VARCHAR(32) NOT NULL, eventTimestamp BIGINT NOT NULL, recordedOn TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX IDX_schema_usage_1 ON schema_usage(globalId);
CREATE INDEX IDX_schema_usage_2 ON schema_usage(clientId);
CREATE INDEX IDX_schema_usage_3 ON schema_usage(eventTimestamp);
