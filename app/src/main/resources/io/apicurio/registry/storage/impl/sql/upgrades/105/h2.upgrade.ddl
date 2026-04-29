-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrade Script from 104 to 105
-- *********************************************************************

UPDATE apicurio SET propValue = 105 WHERE propName = 'db_version';

CREATE TABLE schema_usage_summary (globalId BIGINT NOT NULL, totalFetches BIGINT NOT NULL DEFAULT 0, uniqueClients INT NOT NULL DEFAULT 0, firstFetchedOn BIGINT NOT NULL, lastFetchedOn BIGINT NOT NULL, clientList TEXT, updatedOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
ALTER TABLE schema_usage_summary ADD PRIMARY KEY (globalId);
