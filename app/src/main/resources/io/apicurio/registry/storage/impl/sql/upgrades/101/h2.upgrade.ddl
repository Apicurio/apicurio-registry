-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrade Script from 100 to 101
-- *********************************************************************

UPDATE apicurio SET prop_value = 101 WHERE prop_name = 'db_version';
