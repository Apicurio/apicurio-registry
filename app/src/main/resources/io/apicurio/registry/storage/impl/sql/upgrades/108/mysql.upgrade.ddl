-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mysql
-- Upgrade Script from 107 to 108
-- *********************************************************************

UPDATE apicurio SET propValue = 108 WHERE propName = 'db_version';

CREATE TABLE artifact_structured_content (
    groupId      VARCHAR(512) NOT NULL,
    artifactId   VARCHAR(512) NOT NULL,
    elementType  VARCHAR(64)  NOT NULL,
    elementValue VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL
) DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;
ALTER TABLE artifact_structured_content ADD CONSTRAINT FK_asc_1 FOREIGN KEY (groupId, artifactId) REFERENCES artifacts (groupId, artifactId) ON DELETE CASCADE;
CREATE INDEX IDX_asc_1 ON artifact_structured_content (elementType, elementValue);
CREATE INDEX IDX_asc_2 ON artifact_structured_content (groupId, artifactId);

UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.StructuredContentUpgrader;
