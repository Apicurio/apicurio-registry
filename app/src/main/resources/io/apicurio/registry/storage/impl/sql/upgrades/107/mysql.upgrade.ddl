-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MySQL
-- Upgrade Script from 106 to 107
-- *********************************************************************

UPDATE apicurio SET propValue = 107 WHERE propName = 'db_version';

ALTER TABLE content MODIFY COLUMN content MEDIUMBLOB NOT NULL;
