-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MySQL
-- Upgrade Script from 109 to 110
-- *********************************************************************

UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';

ALTER TABLE outbox MODIFY COLUMN aggregateid VARCHAR(2048) NOT NULL;
