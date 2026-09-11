-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 109 to 110
-- *********************************************************************

UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';

ALTER TABLE outbox ALTER COLUMN aggregateid TYPE VARCHAR(2048);
