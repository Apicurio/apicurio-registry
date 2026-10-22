-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 106 to 107
-- *********************************************************************

UPDATE apicurio SET propValue = 107 WHERE propName = 'db_version';
