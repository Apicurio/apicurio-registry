-- *********************************************************************
-- DDL for the Apicurio Registry - Database: h2
-- Upgrade Script from 109 to 110
-- *********************************************************************
ALTER TABLE global_rules ADD COLUMN onFailure VARCHAR(32) NOT NULL DEFAULT 'ERROR';
ALTER TABLE group_rules ADD COLUMN onFailure VARCHAR(32) NOT NULL DEFAULT 'ERROR';
ALTER TABLE artifact_rules ADD COLUMN onFailure VARCHAR(32) NOT NULL DEFAULT 'ERROR';
UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';
