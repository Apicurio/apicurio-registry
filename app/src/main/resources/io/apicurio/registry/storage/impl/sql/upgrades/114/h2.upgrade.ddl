-- *********************************************************************
-- DDL for the Apicurio Registry - Database: h2
-- Upgrade Script from 113 to 114
-- *********************************************************************
ALTER TABLE schema_usage ADD COLUMN usageId BIGINT AUTO_INCREMENT NOT NULL;
ALTER TABLE schema_usage ADD PRIMARY KEY (usageId);
UPDATE apicurio SET propValue = 114 WHERE propName = 'db_version';
