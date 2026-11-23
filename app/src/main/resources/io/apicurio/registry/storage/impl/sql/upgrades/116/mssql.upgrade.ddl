-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 115 to 116
-- *********************************************************************
ALTER TABLE schema_usage ADD usageId BIGINT IDENTITY(1,1) NOT NULL;
ALTER TABLE schema_usage ADD PRIMARY KEY (usageId);
UPDATE apicurio SET propValue = 116 WHERE propName = 'db_version';
