-- *********************************************************************
-- DDL for the Apicurio Registry - Database: SQL Server
-- Upgrade from version 101 to 102
-- Search Query Optimization Phase 1 (Issue #7010)
-- *********************************************************************

-- Composite indexes for optimized label JOIN searches
CREATE INDEX IDX_alabels_composite ON artifact_labels(groupId, artifactId, labelKey, labelValue);
CREATE INDEX IDX_vlabels_composite ON version_labels(globalId, labelKey, labelValue);
CREATE INDEX IDX_glabels_composite ON group_labels(groupId, labelKey, labelValue);

-- Update the database version
UPDATE apicurio SET propValue = '102' WHERE propName = 'db_version';
