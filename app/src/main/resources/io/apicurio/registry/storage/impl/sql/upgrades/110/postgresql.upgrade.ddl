-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 109 to 110
-- *********************************************************************

UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';
