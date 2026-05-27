-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrade Script from 107 to 108
-- *********************************************************************

UPDATE apicurio SET propValue = 108 WHERE propName = 'db_version';

-- Add epoch column (xRegistry modification counter) to groups, artifacts, versions
ALTER TABLE groups ADD COLUMN epoch BIGINT NOT NULL DEFAULT 0;
ALTER TABLE artifacts ADD COLUMN epoch BIGINT NOT NULL DEFAULT 0;
ALTER TABLE versions ADD COLUMN epoch BIGINT NOT NULL DEFAULT 0;
