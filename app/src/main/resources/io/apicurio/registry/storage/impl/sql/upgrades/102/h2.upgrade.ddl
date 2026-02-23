-- *********************************************************************
-- DDL for the Apicurio Registry - Database: h2
-- Upgrade Script from 101 to 102
-- *********************************************************************

UPDATE apicurio SET propValue = 102 WHERE propName = 'db_version';

CREATE INDEX IDX_versions_8 ON versions(modifiedOn);
