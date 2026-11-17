-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 113 to 114
-- *********************************************************************
-- Nothing to change: only the MySQL schema stored names, labels and principals as ASCII.
UPDATE apicurio SET propValue = 114 WHERE propName = 'db_version';
