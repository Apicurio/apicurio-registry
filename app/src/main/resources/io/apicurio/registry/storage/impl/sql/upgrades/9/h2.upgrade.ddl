-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrades the DB schema from version 8 to version 9.
-- *********************************************************************

UPDATE apicurio SET prop_value = 9 WHERE prop_name = 'db_version';

ALTER TABLE rules DROP CONSTRAINT FK_rules_1;
