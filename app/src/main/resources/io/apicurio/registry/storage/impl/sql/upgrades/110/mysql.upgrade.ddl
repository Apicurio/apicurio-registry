-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mysql
-- Upgrade Script from 109 to 110
-- *********************************************************************

-- MySQL DDL commits implicitly: creation must be retryable and the marker must follow backfill.
CREATE TABLE IF NOT EXISTS artifact_structured_content (
    groupId      VARCHAR(512) NOT NULL,
    artifactId   VARCHAR(512) NOT NULL,
    elementType  VARCHAR(64)  NOT NULL,
    elementValue VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (groupId, artifactId, elementType, elementValue),
    CONSTRAINT FK_asc_1 FOREIGN KEY (groupId, artifactId) REFERENCES artifacts (groupId, artifactId) ON DELETE CASCADE,
    INDEX IDX_asc_1 (elementType, elementValue)
) DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;

UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.StructuredContentUpgrader;

UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';
