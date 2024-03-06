-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrades the DB schema from version 13 to version 14.
-- *********************************************************************

UPDATE apicurio SET prop_value = 14 WHERE prop_name = 'db_version';

-- This upgrade script left intentionally blank.
