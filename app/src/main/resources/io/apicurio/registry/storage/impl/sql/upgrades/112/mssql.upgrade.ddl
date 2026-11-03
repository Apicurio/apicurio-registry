-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 111 to 112
-- *********************************************************************

DELETE FROM branch_versions WHERE branchId = 'drafts' AND EXISTS (SELECT 1 FROM versions v WHERE v.groupId = branch_versions.groupId AND v.artifactId = branch_versions.artifactId AND v.version = branch_versions.version AND v.state <> 'DRAFT');

UPDATE apicurio SET propValue = 112 WHERE propName = 'db_version';
