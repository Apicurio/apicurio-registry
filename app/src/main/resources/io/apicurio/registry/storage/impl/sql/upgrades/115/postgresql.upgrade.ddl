-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 114 to 115
-- *********************************************************************
UPDATE apicurio SET propValue = 115 WHERE propName = 'db_version';
