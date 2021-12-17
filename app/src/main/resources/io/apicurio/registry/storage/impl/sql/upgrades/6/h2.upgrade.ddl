-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrades the DB schema from version 5 to version 6.
-- *********************************************************************

UPDATE apicurio SET prop_value = 6 WHERE prop_name = 'db_version';

-- This upgrade script left intentionally blank.
