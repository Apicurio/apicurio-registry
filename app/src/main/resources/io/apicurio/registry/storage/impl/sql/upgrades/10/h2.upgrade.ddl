-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrades the DB schema from version 9 to version 10.
-- *********************************************************************

UPDATE apicurio SET prop_value = 10 WHERE prop_name = 'db_version';

UPDATE artifactreferences SET groupId = '__$GROUPID$__' WHERE groupId IS NULL;

-- Add ON DELETE CASCADE in FK
ALTER TABLE artifactreferences DROP CONSTRAINT FK_artifactreferences_1
ALTER TABLE artifactreferences ADD CONSTRAINT FK_artifactreferences_1 FOREIGN KEY (tenantId, contentId) REFERENCES content(tenantId, contentId) ON DELETE CASCADE;
