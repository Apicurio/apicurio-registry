-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 113 to 114
-- *********************************************************************
CREATE TABLE schema_usage_daily (globalId BIGINT NOT NULL, contentId BIGINT NOT NULL DEFAULT 0, clientId NVARCHAR(256) NOT NULL, operation NVARCHAR(32) NOT NULL, usageDay BIGINT NOT NULL, fetchCount BIGINT NOT NULL, firstEventTimestamp BIGINT NOT NULL, lastEventTimestamp BIGINT NOT NULL);
ALTER TABLE schema_usage_daily ADD PRIMARY KEY (globalId, contentId, clientId, operation, usageDay);
CREATE INDEX IDX_schema_usage_daily_1 ON schema_usage_daily(clientId);
CREATE INDEX IDX_schema_usage_daily_2 ON schema_usage_daily(usageDay);
-- One row per (globalId, contentId, clientId, operation, UTC epoch day of eventTimestamp).
-- SQL integer division truncates toward zero and the application computes the day with Math.floorDiv;
-- the two agree for every timestamp at or after 1970-01-01.
INSERT INTO schema_usage_daily (globalId, contentId, clientId, operation, usageDay, fetchCount, firstEventTimestamp, lastEventTimestamp) SELECT globalId, contentId, clientId, operation, eventTimestamp / 86400000, COUNT(*), MIN(eventTimestamp), MAX(eventTimestamp) FROM schema_usage GROUP BY globalId, contentId, clientId, operation, eventTimestamp / 86400000;
DROP TABLE schema_usage;
UPDATE apicurio SET propValue = 114 WHERE propName = 'db_version';
