-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 109 to 110
-- *********************************************************************

UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';

CREATE TABLE artifact_structured_content (groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, elementType VARCHAR(64) NOT NULL, elementValue VARCHAR(256) NOT NULL);
ALTER TABLE artifact_structured_content ADD PRIMARY KEY (groupId, artifactId, elementType, elementValue);
ALTER TABLE artifact_structured_content ADD CONSTRAINT FK_asc_1 FOREIGN KEY (groupId, artifactId) REFERENCES artifacts(groupId, artifactId) ON DELETE CASCADE;
CREATE INDEX IDX_asc_1 ON artifact_structured_content(elementType, elementValue);

UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.StructuredContentUpgrader;
