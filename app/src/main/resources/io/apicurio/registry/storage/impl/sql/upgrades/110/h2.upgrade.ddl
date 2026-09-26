-- *********************************************************************
-- DDL for the Apicurio Registry - Database: h2
-- Upgrade Script from 109 to 110
-- *********************************************************************

CREATE TABLE IF NOT EXISTS artifact_structured_content (groupId VARCHAR(512) NOT NULL, artifactId VARCHAR(512) NOT NULL, elementType VARCHAR(64) NOT NULL, elementValue VARCHAR(256) NOT NULL, PRIMARY KEY (groupId, artifactId, elementType, elementValue), CONSTRAINT FK_asc_1 FOREIGN KEY (groupId, artifactId) REFERENCES artifacts(groupId, artifactId) ON DELETE CASCADE);
CREATE INDEX IF NOT EXISTS IDX_asc_1 ON artifact_structured_content(elementType, elementValue);

UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.StructuredContentUpgrader;

UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';
