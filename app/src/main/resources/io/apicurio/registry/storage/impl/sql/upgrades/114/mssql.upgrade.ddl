-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 113 to 114
-- *********************************************************************
ALTER TABLE schema_usage ADD usageId BIGINT IDENTITY(1,1) NOT NULL;
ALTER TABLE schema_usage ADD PRIMARY KEY (usageId);
UPDATE apicurio SET propValue = 114 WHERE propName = 'db_version';
