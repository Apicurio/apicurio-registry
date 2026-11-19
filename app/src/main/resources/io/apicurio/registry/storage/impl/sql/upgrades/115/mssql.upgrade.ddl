-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 114 to 115
-- *********************************************************************
ALTER TABLE schema_usage ADD usageId BIGINT IDENTITY(1,1) NOT NULL;
ALTER TABLE schema_usage ADD PRIMARY KEY (usageId);
UPDATE apicurio SET propValue = 115 WHERE propName = 'db_version';
