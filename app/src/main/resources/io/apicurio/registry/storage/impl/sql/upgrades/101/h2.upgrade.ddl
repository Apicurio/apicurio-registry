-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrade Script from 100 to 101
-- *********************************************************************

UPDATE apicurio SET propValue = 101 WHERE propName = 'db_version';
