-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MS SQL Server
-- Upgrade Script from 107 to 108
-- *********************************************************************

UPDATE apicurio SET propValue = 108 WHERE propName = 'db_version';

-- Add epoch column (xRegistry modification counter) to groups, artifacts, versions
ALTER TABLE groups ADD epoch BIGINT NOT NULL DEFAULT 0;
ALTER TABLE artifacts ADD epoch BIGINT NOT NULL DEFAULT 0;
ALTER TABLE versions ADD epoch BIGINT NOT NULL DEFAULT 0;
