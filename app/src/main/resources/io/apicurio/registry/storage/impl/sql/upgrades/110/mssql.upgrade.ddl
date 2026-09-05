-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 109 to 110
-- *********************************************************************

UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';

ALTER TABLE outbox ALTER COLUMN aggregateid NVARCHAR(2048) NOT NULL;
